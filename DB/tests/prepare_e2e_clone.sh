#!/usr/bin/env bash
# ==============================================================================
# Chuan bi fixture E2E tren CLONE dung mot lan (Giai doan 2B.7C).
#   $1 ten database clone (bat buoc tien to claude_2b7x_e2e_ hoac phonenest_e2e_)
# Bien moi truong (bat buoc): PN_DB_PASSWORD, PN_E2E_ADMIN_HASH, PN_E2E_CUSTOMER_HASH
#   (bcrypt cua mat khau E2E ngau nhien; KHONG dung mat khau that, KHONG commit).
# Chi dat mat khau E2E cho TK001 (admin) va TK002 (khach) TREN CLONE. Khong dong vao
# database nao khac. Tao clone bang DB/tests/create_it_clone.sh (PN_IT_DB_NAME=claude_2b7c_e2e_...).
# ==============================================================================
set -euo pipefail
DB="${1:?can ten database clone}"
[[ "$DB" =~ ^(claude_2b7[a-z]_e2e_|phonenest_e2e_)[a-z0-9_]+$ ]] || { echo "REFUSED: $DB khong phai clone E2E"; exit 99; }
: "${PN_DB_PASSWORD:?}" "${PN_E2E_ADMIN_HASH:?}" "${PN_E2E_CUSTOMER_HASH:?}"
[[ "$PN_E2E_ADMIN_HASH" =~ ^\$2[aby]\$ && "$PN_E2E_CUSTOMER_HASH" =~ ^\$2[aby]\$ ]] || { echo "REFUSED: hash khong phai bcrypt"; exit 98; }
export MYSQL_PWD="$PN_DB_PASSWORD"
MDB="${PN_MARIADB_BIN:+$PN_MARIADB_BIN/}mariadb"
"$MDB" -h "${PN_DB_HOST:-127.0.0.1}" -P "${PN_DB_PORT:-3307}" -u "${PN_DB_USER:-root}" -D "$DB" -N -B -e "
  SELECT IF(DATABASE() REGEXP '^(claude_2b7[a-z]_e2e_|phonenest_e2e_)', 'OK', NULL) INTO @ok;
  UPDATE taikhoan SET matKhau='${PN_E2E_ADMIN_HASH}' WHERE maTk='TK001' AND @ok='OK';
  UPDATE taikhoan SET matKhau='${PN_E2E_CUSTOMER_HASH}' WHERE maTk='TK002' AND @ok='OK';
  SELECT CONCAT('fixture E2E: ', ROW_COUNT() >= 0, ' db=', DATABASE());"
