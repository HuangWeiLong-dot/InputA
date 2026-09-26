#!/usr/bin/env bash
#
# 在服务器上跑的那一半部署：装运行时依赖、重启服务、验证。
#
# 由 .github/workflows/deploy-backend.yml 经 ssh 管道过来执行，也可以手工跑：
#
#     ssh deploy@<host> bash -s -- /opt/inputa/app < deploy/deploy-remote.sh
#
# 之所以做成「管道过去」而不是「拷到服务器上再跑」，是为了让脚本永远是最新提交里
# 那一份 —— 否则改这个文件本身也需要一次部署才能生效，而这条路径正是部署卡住时
# 唯一的救援通道。
#
# 需要 deploy 用户对下面两条 systemctl 有免密 sudo 权限（见 setup-server.sh）。

set -euo pipefail

APP_DIR="${1:?usage: deploy-remote.sh <app-dir>}"
SERVICE="${SERVICE:-inputa}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8787/api/health}"

cd "$APP_DIR"

# 只装运行时依赖就够了：后端没有构建步骤（node 直接靠类型剥离跑 .ts 源码），
# 所以 devDependencies 在服务器上一个都不需要。better-sqlite3 有 linux-x64
# 预编译包，不需要编译器工具链。
echo "[deploy] npm ci --omit=dev in $APP_DIR"
npm ci --omit=dev

echo "[deploy] restarting $SERVICE"
sudo systemctl restart "$SERVICE"

# systemd 的 restart 是异步的，给它一点时间完成监听。
sleep 2

health="$(curl -fsS "$HEALTH_URL")"
echo "[deploy] $HEALTH_URL -> $health"

case "$health" in
  *'"ok":true'*)
    ;;
  *)
    echo "[deploy] FAILED: health check did not report ok:true" >&2
    echo "[deploy] recent logs:" >&2
    sudo journalctl -u "$SERVICE" -n 30 --no-pager >&2 || true
    exit 1
    ;;
esac

# 词典缺失是**警告**而不是失败：README 里写明没有它其余功能照常工作（只是没有中文
# 释义和考试标签），所以一个刻意不带词典的部署不应该被卡住。但这是最常见的初始化
# 遗漏，值得在每次部署时显式吼一声。
case "$health" in
  *'"dictionaryAvailable":true'*)
    ;;
  *)
    echo "[deploy] WARNING: /api/dict 会返回 503 —— $APP_DIR/data/stardict.db 缺失或不可读。"
    echo "[deploy]          把词典放好后需要再重启一次：进程会把打开失败的结果缓存到退出为止。"
    ;;
esac
