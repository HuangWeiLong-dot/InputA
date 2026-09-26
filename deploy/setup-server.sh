#!/usr/bin/env bash
#
# 一次性初始化：把一台干净的 Ubuntu / Debian 变成能跑 InputA 后端的机器。
#
#     sudo ./setup-server.sh inputa.duckdns.org
#
# 默认的 PAGES_ORIGIN 是 https://huangweilong-dot.github.io；换账号时覆盖它：
#
#     sudo PAGES_ORIGIN=https://<你的账号>.github.io ./setup-server.sh inputa.duckdns.org
#
# 幂等：重复跑不会破坏已有配置（/etc/inputa.env 存在就不再覆盖，因为里面是真实配置）。
#
# 这个脚本**不**做的事：不放词典文件、不放 SSH 公钥、不启动 inputa.service。
# 前两件见本机 DEPLOY.md（未入库，因为里面写着线上主机名）；第三件由第一次部署完成
# （此时应用目录还是空的，启动了也只会每 3 秒重启一次刷屏）。

set -euo pipefail

HOST="${1:-}"
PAGES_ORIGIN="${PAGES_ORIGIN:-https://huangweilong-dot.github.io}"
APP_USER="${APP_USER:-inputa}"
APP_HOME="${APP_HOME:-/opt/inputa}"
APP_DIR="${APP_DIR:-$APP_HOME/app}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "错误：$*" >&2; exit 1; }
step() { echo; echo "==> $*"; }

[[ $EUID -eq 0 ]] || die "需要 root 权限，请用 sudo 运行。"
[[ -n "$HOST" ]] || die "缺少参数。用法：sudo $0 <你的子域名>.duckdns.org"
[[ "$HOST" == *.* ]] || die "「$HOST」看起来不是域名。"

export DEBIAN_FRONTEND=noninteractive

# ------------------------------------------------------------------ 预检：DNS

step "预检 DNS：$HOST 是否指向本机"

resolved="$(getent hosts "$HOST" | awk '{print $1}' | head -1 || true)"
public_ip="$(curl -fsS --max-time 10 https://api.ipify.org 2>/dev/null || true)"

if [[ -z "$resolved" ]]; then
  echo "警告：$HOST 目前解析不出地址。"
  echo "      请先在 DuckDNS 上把它的 A 记录指向本机公网 IP，否则 Caddy 签不下证书。"
elif [[ -z "$public_ip" ]]; then
  echo "提示：$HOST 解析为 $resolved（取不到本机公网 IP，跳过比对）。"
elif [[ "$resolved" != "$public_ip" ]]; then
  echo "警告：$HOST 解析为 $resolved，但本机公网 IP 是 $public_ip。"
  echo "      A 记录没指对的话，Let's Encrypt 的 HTTP-01 挑战会失败，证书签不下来。"
else
  echo "$HOST -> $resolved，与本机公网 IP 一致。"
fi

# --------------------------------------------------------------- 用户与目录

step "创建用户与目录"

if id -u "$APP_USER" >/dev/null 2>&1; then
  echo "用户 $APP_USER 已存在，跳过。"
else
  # 需要能登录（部署是经 SSH 用这个用户跑的），所以用 /bin/bash 而不是 nologin。
  adduser --system --group --home "$APP_HOME" --shell /bin/bash "$APP_USER"
fi

# 不依赖 adduser 是否建了家目录（--system 的行为在不同版本上不一致），显式保证。
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_HOME"
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_DIR"
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_DIR/data"

# 部署时要写 ~/.ssh/authorized_keys，这里先把目录建好、权限设对 ——
# sshd 对这两处权限很挑剔，权限不对会静默拒绝公钥认证。
install -d -o "$APP_USER" -g "$APP_USER" -m 0700 "$APP_HOME/.ssh"
if [[ ! -f "$APP_HOME/.ssh/authorized_keys" ]]; then
  install -o "$APP_USER" -g "$APP_USER" -m 0600 /dev/null "$APP_HOME/.ssh/authorized_keys"
  echo "已创建空的 authorized_keys，请把部署公钥追加进去（见本机 DEPLOY.md）。"
fi

# ------------------------------------------------------------------- 依赖包

step "安装基础依赖（rsync、curl、gnupg）"

apt-get update -qq
apt-get install -y -qq --no-install-recommends rsync curl gnupg ca-certificates

# ---------------------------------------------------------------------- Node

step "安装 Node.js 24（NodeSource）"

if command -v node >/dev/null 2>&1; then
  echo "已安装：node $(node -v)"
else
  curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
  apt-get install -y -qq nodejs
  echo "已安装：node $(node -v)"
fi

# 后端靠 Node 内置的类型剥离直接加载 .ts，22.18 以下会在 import 时报未知扩展名。
node_major="$(node -p 'process.versions.node.split(".")[0]')"
node_minor="$(node -p 'process.versions.node.split(".")[1]')"
if (( node_major < 22 || (node_major == 22 && node_minor < 18) )); then
  die "Node $(node -v) 太旧。后端需要 ≥ 22.18（.ts 靠内置类型剥离加载）。"
fi

# --------------------------------------------------------------------- Caddy

step "安装 Caddy（官方 apt 源）"

if command -v caddy >/dev/null 2>&1; then
  echo "已安装：$(caddy version)"
else
  apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
    | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
    > /etc/apt/sources.list.d/caddy-stable.list
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  apt-get update -qq
  apt-get install -y -qq caddy
  echo "已安装：$(caddy version)"
fi

# ---------------------------------------------------------------- Caddyfile

step "写入 /etc/caddy/Caddyfile"

PLACEHOLDER='REPLACE_WITH_YOUR_DUCKDNS_HOST'
tmp_caddy="$(mktemp)"
sed "s/$PLACEHOLDER/$HOST/" "$SCRIPT_DIR/Caddyfile" > "$tmp_caddy"

# 校验再落地：一个语法错误的 Caddyfile 会让 Caddy 起不来，而 TLS 是整套部署的
# 前提，不该等到浏览器里报错才发现。
#
# --adapter caddyfile 不能省：Caddy 是按**文件名**推断格式的，而临时文件叫
# tmp.XXXXXX，不指定的话它会当成 JSON 去解析。
validate_log="$(mktemp)"
if ! caddy validate --adapter caddyfile --config "$tmp_caddy" >"$validate_log" 2>&1; then
  sed 's/^/    /' "$validate_log"
  rm -f "$tmp_caddy" "$validate_log"
  die "Caddyfile 校验失败，未写入。"
fi
rm -f "$validate_log"
install -m 0644 -o root -g root "$tmp_caddy" /etc/caddy/Caddyfile
rm -f "$tmp_caddy"

# ------------------------------------------------------------------- env 文件

step "写入 /etc/inputa.env"

if [[ -f /etc/inputa.env ]]; then
  echo "/etc/inputa.env 已存在，保持不动（里面是真实配置）。"
  echo "如需改 CORS 白名单或词典路径，手工编辑它，然后 systemctl restart inputa。"
else
  sed "s#^CORS_ALLOWED_ORIGINS=.*#CORS_ALLOWED_ORIGINS=$PAGES_ORIGIN#" \
    "$SCRIPT_DIR/inputa.env.example" > /etc/inputa.env
  chmod 0600 /etc/inputa.env
  echo "已生成，CORS_ALLOWED_ORIGINS=$PAGES_ORIGIN"
  echo "（若你的 Pages 来源不是这个，编辑 /etc/inputa.env 后重启服务。）"
fi

# ------------------------------------------------------------------ systemd

step "安装 inputa.service"

install -m 0644 -o root -g root "$SCRIPT_DIR/inputa.service" /etc/systemd/system/inputa.service

# 部署脚本会执行 `sudo systemctl restart inputa`，所以给这个用户一条最小授权。
# 路径从 command -v 取实际值：/bin 与 /usr/bin 的符号链接关系会让写死的路径匹配不上。
systemctl_bin="$(command -v systemctl)"
journalctl_bin="$(command -v journalctl)"
tmp_sudo="$(mktemp)"
cat > "$tmp_sudo" <<SUDOERS
# InputA 部署用：只允许重启本服务、看它的状态和日志。由 deploy/setup-server.sh 生成。
$APP_USER ALL=(root) NOPASSWD: $systemctl_bin restart inputa, \\
    $systemctl_bin status inputa, \\
    $journalctl_bin -u inputa *
SUDOERS

# 必须先用 visudo 校验：一个语法错误的 sudoers 文件会让你彻底用不了 sudo。
if ! visudo -c -f "$tmp_sudo" >/dev/null; then
  rm -f "$tmp_sudo"
  die "sudoers 规则校验失败，未安装（sudo 未被影响）。"
fi
install -m 0440 -o root -g root "$tmp_sudo" /etc/sudoers.d/inputa-deploy
rm -f "$tmp_sudo"
echo "已写入 /etc/sudoers.d/inputa-deploy"

systemctl daemon-reload
systemctl enable inputa.service
# 刻意不 start：应用目录此刻还是空的，启动了也只会每 3 秒重启一次刷屏。第一次部署会拉起它。

step "启动 Caddy"
systemctl enable caddy
systemctl restart caddy
sleep 1
systemctl is-active --quiet caddy && echo "caddy 正在运行。" || {
  echo "caddy 没能起来，最近日志：" >&2
  journalctl -u caddy -n 30 --no-pager >&2
  exit 1
}

# ------------------------------------------------------------------ 后续步骤

cat <<NEXT

=====================================================================
服务器初始化完成。接下来还差三步（详见本机 DEPLOY.md，未入库）：

  1. 放词典（可选，但没有它就没有中文释义）：

       # 在服务器上直接下载，217MB 的压缩包里就是那个 851MB 的 .db
       cd /tmp
       curl -fLO https://github.com/skywind3000/ECDICT/releases/download/1.0.28/ecdict-sqlite-28.zip
       unzip -o ecdict-sqlite-28.zip
       mv stardict.db $APP_DIR/data/stardict.db
       chown $APP_USER:$APP_USER $APP_DIR/data/stardict.db
       chmod 0644 $APP_DIR/data/stardict.db

     解出来的文件名若不是 stardict.db，改成它。

  2. 放部署公钥进 /opt/inputa/.ssh/authorized_keys（一行一个）。

  3. 腾讯云安全组放行 22 / 80 / 443 —— 不要放行 8787，它只该被本机的
     Caddy 访问。

然后推一次 main 分支，或手工触发 deploy-backend 工作流。

验证：

  curl -fsS https://$HOST/api/health

期望看到 "ok":true 和 "dictionaryAvailable":true。

=====================================================================
NEXT
