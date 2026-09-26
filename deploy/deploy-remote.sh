#!/usr/bin/env bash
#
# 在服务器上跑的那一半部署：装运行时依赖、修正属主、重启服务、验证。
#
# 由 .github/workflows/deploy-backend.yml 经 ssh 管道过来执行，也可以手工跑：
#
#     ssh root@<host> bash -s -- /opt/inputa/app < deploy/deploy-remote.sh
#
# 之所以做成「管道过去」而不是「拷到服务器上再跑」，是为了让脚本永远是最新提交里
# 那一份 —— 否则改这个文件本身也需要一次部署才能生效，而这条路径正是部署卡住时
# 唯一的救援通道。

set -euo pipefail

APP_DIR="${1:?usage: deploy-remote.sh <app-dir>}"
SERVICE="${SERVICE:-inputa}"
# 服务进程的用户，对应 systemd 单元的 User=。文件属主要归到它名下。
RUNTIME_USER="${RUNTIME_USER:-inputa}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8787/api/health}"

# 部署是以 root 经 SSH 跑的，此时不需要 sudo；但也允许普通用户来跑（那就要一条
# 免密 sudo 规则）。两种都支持，免得这个脚本和「以谁部署」那个决定绑死。
if [[ $EUID -eq 0 ]]; then
  SUDO=()
else
  SUDO=(sudo)
fi

cd "$APP_DIR"

# 只装运行时依赖就够了：后端没有构建步骤（node 直接靠类型剥离跑 .ts 源码），
# 所以 devDependencies 在服务器上一个都不需要。better-sqlite3 有 linux-x64
# 预编译包，不需要编译器工具链。
echo "[deploy] npm ci --omit=dev in $APP_DIR"
npm ci --omit=dev

# 以 root 跑 rsync 和 npm ci 写出来的文件属主是 **root**（新建文件的属主取决于
# 创建者，不取决于目录属主），而进程是以 $RUNTIME_USER 运行的。不收回来的话，
# 属主会随「以谁部署」漂移：今天 root 部署写出 root 的文件，明天换成普通用户部署
# 就写不进去 —— 这种问题只在换部署方式那天才会暴露，最难查。
if [[ $EUID -eq 0 ]]; then
  chown -R "$RUNTIME_USER:$RUNTIME_USER" "$APP_DIR"
fi

echo "[deploy] restarting $SERVICE"
"${SUDO[@]}" systemctl restart "$SERVICE"

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
    "${SUDO[@]}" journalctl -u "$SERVICE" -n 30 --no-pager >&2 || true
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
