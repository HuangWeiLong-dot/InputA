#!/usr/bin/env bash
#
# 给 inputa.duckdns.org 接上 nginx 反代并签发 Let's Encrypt 证书。
#
#     sudo ./setup-tls.sh inputa.duckdns.org
#     sudo LETSENCRYPT_EMAIL=you@example.com ./setup-tls.sh inputa.duckdns.org
#
# 为什么和 setup-server.sh 分开：那一步做的是「建用户、装 Node、装 systemd 单元」，
# 与这台机器上既有的东西无关；这一步做的是「接进已有的 nginx」，纯做加法。分开之后，
# 已经跑过 setup-server.sh 的机器不用为了配证书把前面重跑一遍。
#
# 这台机器上 nginx 早在跑，且服务着别的站点，所以这里**只做加法**：新增一个
# server_name 唯一匹配的配置文件，只给这一个域名签证书，现有站点一行都不碰。
#
# 幂等：证书已存在时只会 reload 一次 nginx。

set -euo pipefail

HOST="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE_FILE="${SITE_FILE:-/etc/nginx/sites-enabled/inputa.conf}"
PLACEHOLDER='REPLACE_WITH_YOUR_DUCKDNS_HOST'

die() { echo "错误：$*" >&2; exit 1; }
step() { echo; echo "==> $*"; }

[[ $EUID -eq 0 ]] || die "需要 root 权限，请用 sudo 运行。"
[[ -n "$HOST" ]] || die "缺少参数。用法：sudo $0 inputa.duckdns.org"

export DEBIAN_FRONTEND=noninteractive

# ------------------------------------------------------------------- 预检

step "预检"

command -v nginx >/dev/null 2>&1 || die "没找到 nginx —— 这台机器上没有 nginx 的话方案要换，请先告诉我。"
systemctl is-active --quiet nginx || die "nginx 没在运行，先把它起起来。"

# 证书签发走 HTTP-01，Let's Encrypt 必须能从这个域名访问到本机。这一条不对的话，
# certbot 的报错会指向「挑战失败」而不是「DNS 配错了」，所以提前验一次。
resolved="$(getent hosts "$HOST" | awk '{print $1}' | head -1 || true)"
public_ip="$(curl -fsS --max-time 10 https://api.ipify.org 2>/dev/null || true)"
if [[ -z "$resolved" ]]; then
  echo "警告：$HOST 解析不出地址 —— 证书签不下来。先配好 A 记录。"
elif [[ -n "$public_ip" && "$resolved" != "$public_ip" ]]; then
  echo "警告：$HOST 解析为 $resolved，而本机公网 IP 是 $public_ip，两者不一致。"
else
  echo "$HOST -> ${resolved:-?}，解析正常。"
fi

# ---------------------------------------------------------------- certbot

step "检查 certbot"

if command -v certbot >/dev/null 2>&1; then
  echo "已安装：$(certbot --version 2>&1)"
else
  apt-get update -qq
  apt-get install -y -qq certbot python3-certbot-nginx
  echo "已安装：$(certbot --version 2>&1)"
fi

# 插件缺失时 certbot --nginx 报的错很不直白，先补齐。
if ! dpkg -s python3-certbot-nginx >/dev/null 2>&1; then
  echo "缺少 nginx 插件，安装中…"
  apt-get update -qq && apt-get install -y -qq python3-certbot-nginx
fi

# ------------------------------------------------------- nginx 站点配置文件

step "写入 $SITE_FILE"

# 只有「已经属于这个域名」且「被 certbot 接管」时才跳过重写。
#
# 后半句不能少：换域名时站点文件同样带着 managed by Certbot 标记，只判断那一条就会
# 跳过重写，于是 server_name 和证书都停在旧域名上 —— 而那正是这次要改的东西。
if [[ -f "$SITE_FILE" ]] \
   && grep -q 'managed by Certbot' "$SITE_FILE" \
   && grep -qF "server_name $HOST;" "$SITE_FILE"; then
  echo "已是 $HOST 且由 certbot 接管（含 443 与证书路径），保持不动。"
else
  sed "s/$PLACEHOLDER/$HOST/" "$SCRIPT_DIR/inputa.nginx.conf" > "$SITE_FILE"
  chmod 0644 "$SITE_FILE"
  # 占位符没被替换掉的话，nginx -t 是**通不过**检验的：它是个语法合法的 server_name，
  # 只会静默地服务错域名。所以单独验一次。
  if grep -q "$PLACEHOLDER" "$SITE_FILE"; then
    rm -f "$SITE_FILE"
    die "占位符未被替换，已删除刚写入的文件。"
  fi
  echo "已写入。"
fi

# 先体检再 reload。这台 nginx 还服务着别的站点，所以校验不过就立刻回滚这个文件 ——
# 绝不能留下一个让 nginx 起不来的配置。
if ! nginx -t 2>&1 | sed 's/^/    /'; then
  rm -f "$SITE_FILE"
  die "nginx -t 没过，已删除刚写入的 $SITE_FILE（nginx 现状未受影响）。"
fi

systemctl reload nginx
echo "nginx 已 reload。"

# ------------------------------------------------------------------- 证书

step "申请证书"

if [[ -d "/etc/letsencrypt/live/$HOST" ]]; then
  echo "已有 $HOST 的证书，跳过签发（续期由 certbot 的 systemd timer 负责）。"
else
  # 复用已有的 certbot 账户就不需要邮箱；没有账户又没给邮箱时，只能不绑邮箱注册 ——
  # 那样证书到期前不会收到提醒，所以显式说出来。
  if [[ -n "${LETSENCRYPT_EMAIL:-}" ]]; then
    email_args=(-m "$LETSENCRYPT_EMAIL")
  elif [[ -d /etc/letsencrypt/accounts && -n "$(ls -A /etc/letsencrypt/accounts 2>/dev/null)" ]]; then
    echo "复用已有的 certbot 账户，无需邮箱。"
    email_args=()
  else
    email_args=(--register-unsafely-without-email)
    echo "提示：没有现成账户也没给 LETSENCRYPT_EMAIL，将不绑邮箱注册 —— 到期前收不到邮件提醒。"
  fi

  # --redirect 会往站点配置里加 80→443 跳转，与这台机器上原有站点的做法一致。
  certbot --nginx -d "$HOST" --non-interactive --redirect --agree-tos "${email_args[@]}" \
    || die "签发失败。常见原因：$HOST 没解析到本机，或安全组没放行 80。"
fi

systemctl reload nginx

# ------------------------------------------------------------------- 验证

step "验证"

echo "证书："
echo | openssl s_client -connect "$HOST:443" -servername "$HOST" 2>/dev/null \
  | openssl x509 -noout -subject -dates 2>/dev/null \
  || echo "  （没取到证书信息，看上面 certbot 的输出）"

echo
code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "https://$HOST/api/health" 2>/dev/null || true)"
echo "https://$HOST/api/health -> HTTP ${code:-连接失败}"
case "${code:-}" in
  200)     echo "后端已就绪。" ;;
  502|503) echo "TLS 已经通了，后端还没起来 —— 这是**正常**的："
           echo "inputa.service 要等第一次部署（或手工 systemd start）才会拉起。" ;;
  000|"")  echo "连不上。检查安全组是否放行 443。" ;;
  *)       echo "状态码意外，看后端日志：journalctl -u inputa -n 50" ;;
esac
