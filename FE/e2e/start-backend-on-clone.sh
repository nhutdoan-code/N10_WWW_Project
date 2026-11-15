#!/usr/bin/env bash
# Chay Backend (jar da build) tren CLONE E2E. Tu choi moi DB_URL khong phai localhost/clone.
#   Bien moi truong: DB_URL, DB_PASSWORD, JWT_SECRET (ngau nhien cho lan chay), GEMINI_API_KEY (co the gia)
set -euo pipefail
: "${DB_URL:?}" "${DB_PASSWORD:?}" "${JWT_SECRET:?}"
[[ "$DB_URL" =~ ^jdbc:mariadb://(localhost|127\.0\.0\.1)(:[0-9]+)?/(claude_2b7[a-z]_e2e_|phonenest_e2e_)[a-z0-9_]+$ ]] \
  || { echo "REFUSED: DB_URL khong phai clone E2E tren localhost"; exit 99; }
# 2B.7D: E2E KHONG chay bang root — dung tai khoan runtime gioi han quyen tren clone (vd. pn7d_rt).
: "${DB_USERNAME:?Can DB_USERNAME (tai khoan runtime gioi han quyen, khong phai root)}"
[[ "$DB_USERNAME" != "root" ]] || { echo "REFUSED: khong chay E2E bang root"; exit 98; }
export GEMINI_API_KEY="${GEMINI_API_KEY:-e2e-dummy-key}"
HERE="$(cd "$(dirname "$0")" && pwd)"
JAR="$(ls "$HERE"/../../BE/phonenest/target/phonenest-*.jar | grep -v original | head -1)"
exec java -jar "$JAR" --server.port="${E2E_BACKEND_PORT:-8080}"
