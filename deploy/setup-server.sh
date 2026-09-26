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
  echo "      请先在 DuckDNS 上把它的 A 记录指向本机公网 IP，否则证书签不下来。"
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
  # **服务**账号，不是部署账号：部署以 root 经 SSH 进行（见 DEPLOY.md），这个用户
  # 只用来跑 inputa.service 这个进程，所以它不需要登录能力。给它 /bin/bash 只是因为
  # 你在需要时能 `sudo -u inputa` 进去看一眼，本身不带任何权限。
  adduser --system --group --home "$APP_HOME" --shell /bin/bash "$APP_USER"
fi

# 不依赖 adduser 是否建了家目录（--system 的行为在不同版本上不一致），显式保证。
# 属主必须是 $APP_USER 而不是 root —— systemd 单元是 `User=inputa`，以 root 身份
# rsync/npm ci 写出来的文件若归了 root，服务读起来就会出问题。
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_HOME"
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_DIR"
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_DIR/data"

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

# TLS 和反代不在这里做 —— 由 deploy/setup-tls.sh 单独负责，因为它要接进这台机器上
# **已经在跑**的 nginx（还服务着别的站点），属于「给现有 nginx 做加法」，跟这里
# 「建用户、装 Node、装 systemd 单元」是两件独立的事。见那个脚本的说明。

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

# 部署以 root 经 SSH 进行，所以不需要给 $APP_USER 任何 sudo 授权 —— 它只是服务账号。
#
# 早先的版本在这里写过一条 /etc/sudoers.d/inputa-deploy 免密规则；机器上若还留着，
# 顺手删掉：一条没人再需要的免密 sudo 授权，正是最该清掉的那种残留权限。
if [[ -f /etc/sudoers.d/inputa-deploy ]]; then
  rm -f /etc/sudoers.d/inputa-deploy
  echo "已删除遗留的 /etc/sudoers.d/inputa-deploy（部署改用 root，不再需要）。"
fi

systemctl daemon-reload
systemctl enable inputa.service
# 刻意不 start：应用目录此刻还是空的，启动了也只会每 3 秒重启一次刷屏。第一次部署会拉起它。

# ------------------------------------------------------------------ 后续步骤

cat <<NEXT

=====================================================================
这一步做完的部分：服务账号、目录、Node 24、systemd 单元、/etc/inputa.env。
接下来还有四步：

  1. 配 TLS 和反代（会接进本机已有的 nginx，纯做加法）：

       cd $SCRIPT_DIR
       sudo ./setup-tls.sh $HOST

  2. 放词典（可选，但没有它就没有中文释义）：

       # 在服务器上直接下载，217MB 的压缩包里就是那个 851MB 的 .db
       cd /tmp
       curl -fLO https://github.com/skywind3000/ECDICT/releases/download/1.0.28/ecdict-sqlite-28.zip
       unzip -o ecdict-sqlite-28.zip
       mv stardict.db $APP_DIR/data/stardict.db
       chown $APP_USER:$APP_USER $APP_DIR/data/stardict.db
       chmod 0644 $APP_DIR/data/stardict.db

     解出来的文件名若不是 stardict.db，改成它。先 free -h 看一眼内存余量：
     这个文件约 851MB，会被 SQLite 只读打开。

  3. 部署是以 **root** 经 SSH 进行的，所以把部署公钥追加到 /root/.ssh/authorized_keys。
     先确认 sshd 允许 root 用密钥登录：

       sudo sshd -T | grep -i permitrootlogin      # 需要 prohibit-password 或 yes

     注意 $APP_USER 只是**服务**账号（systemd 单元是 User=$APP_USER），不需要也不能
     通过 SSH 登录。以 root 部署写出的文件属主会是 root，deploy-remote.sh 每次部署
     末尾会用 chown -R 收回给 $APP_USER —— 属主不能随「以谁部署」漂移。

  4. 安全组放行 22 / 80 / 443 —— 不要放行 8787，它只该被本机的 nginx 访问。

然后推一次 main 分支，或手工触发 deploy-backend 工作流。

验证（inputa.service 要等第一次部署才会启动，所以在那之前 /api/health 会 502，
TLS 本身应该已经通了）：

  curl -fsS https://$HOST/api/health

期望看到 "ok":true 和 "dictionaryAvailable":true。

=====================================================================
NEXT
