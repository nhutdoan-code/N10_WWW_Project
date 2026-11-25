#!/usr/bin/env bash
# ==============================================================================
# Tao clone dung mot lan cho integration test backend (Giai doan 2B.7B).
#   $1 file dump nguon (vd. dump cua bandienthoai_clean_acceptance, da co Migration 15)
# Bien moi truong: PN_DB_PASSWORD (bat buoc), PN_DB_HOST/PN_DB_PORT/PN_DB_USER, PN_MARIADB_BIN
# Ket qua: in ra lenh export PHONENEST_IT_DB_URL=... de chay `mvn test`.
# Script tu choi moi ten database khong co tien to claude_2b7x_it_ / claude_2b7x_e2e_ / phonenest_it_.
# ==============================================================================
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DUMP="${1:?can file dump nguon}"
: "${PN_DB_PASSWORD:?Can dat PN_DB_PASSWORD}"
export MYSQL_PWD="$PN_DB_PASSWORD"
HOST="${PN_DB_HOST:-127.0.0.1}"; PORT="${PN_DB_PORT:-3307}"; USER_="${PN_DB_USER:-root}"
MDB="${PN_MARIADB_BIN:+$PN_MARIADB_BIN/}mariadb"
DB="${PN_IT_DB_NAME:-phonenest_it_$(date +%Y%m%d%H%M%S)}"
[[ "$DB" =~ ^(claude_2b7[a-z]_it_|claude_2b7[a-z]_e2e_|phonenest_it_)[a-z0-9_]+$ ]] || { echo "REFUSED: ten database khong hop le: $DB"; exit 99; }

"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" -e "CREATE DATABASE \`$DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -D "$DB" < "$DUMP"
"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -N -B -D "$DB" < "$HERE/../migrations/15_add_sales_approval_status.sql" | head -1
"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -N -B -D "$DB" < "$HERE/../migrations/16_add_approval_verification_data.sql" | head -1
# Migration 21: lich su ChatBox + vector store MariaDB (ChatService luu hoi thoai cua tai khoan dang nhap).
"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -D "$DB" < "$HERE/../migrations/21_chat_history_vector_store.sql"
# Migration 22: phi van chuyen theo vung, ngay giao du kien, duong dan thong bao.
"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -D "$DB" < "$HERE/../migrations/22_shipping_zone_eta_notification_link.sql"
# Migration 23: voucher % co tran / tien mat theo bac.
"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -D "$DB" < "$HERE/../migrations/23_voucher_discount_type.sql"
echo
echo "Clone san sang. Chay test:"
echo "  export PHONENEST_IT_DB_URL=jdbc:mariadb://$HOST:$PORT/$DB"
echo "  export PHONENEST_IT_DB_PASSWORD=...   # khong in mat khau"
echo "  (cd BE/phonenest && mvn test)"
echo "Xoa clone khi khong can (chi sau khi duoc phep): DROP DATABASE \`$DB\`;"
