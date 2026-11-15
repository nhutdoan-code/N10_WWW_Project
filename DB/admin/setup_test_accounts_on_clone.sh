#!/usr/bin/env bash
# ==============================================================================
# TAO TAI KHOAN THU NGHIEM GIOI HAN QUYEN — CHI CHO CLONE (Giai doan 2B.7D)
#
# Tao 3 tai khoan moi, ten bat dau bang pn7d_, CHI co quyen tren cac database khop mau
# `claude\_2b7d\_%` (clone dung mot lan). KHONG cap bat ky quyen nao tren bandienthoai.
# KHONG sua / xoa tai khoan hien co. Neu tai khoan pn7d_* da ton tai: chi dat lai quyen cua chinh no.
#
#   pn7d_rt   runtime (Backend, E2E, integration test): SELECT, INSERT, UPDATE, DELETE
#   pn7d_mig  migration: runtime + CREATE, ALTER, INDEX, CREATE TEMPORARY TABLES (khong DROP database)
#   pn7d_none khong co quyen tren database nao (kiem tra "thieu quyen -> that bai an toan")
#
# Bien moi truong: PN_DB_PASSWORD (tai khoan quan tri dung de tao user), PN7D_RT_PW, PN7D_MIG_PW,
#                  PN7D_NONE_PW (mat khau ngau nhien, KHONG commit / KHONG in ra).
# Go bo (chi khi duoc phep): DROP USER cua dung 3 tai khoan pn7d_* (xem cuoi file).
# ==============================================================================
set -euo pipefail
: "${PN_DB_PASSWORD:?}" "${PN7D_RT_PW:?}" "${PN7D_MIG_PW:?}" "${PN7D_NONE_PW:?}"
export MYSQL_PWD="$PN_DB_PASSWORD"
MDB="${PN_MARIADB_BIN:+$PN_MARIADB_BIN/}mariadb"
H="${PN_DB_HOST:-127.0.0.1}"; P="${PN_DB_PORT:-3307}"; U="${PN_DB_USER:-root}"
for pw in "$PN7D_RT_PW" "$PN7D_MIG_PW" "$PN7D_NONE_PW"; do
  [[ "$pw" =~ ^[A-Za-z0-9_-]{16,}$ ]] || { echo "REFUSED: mat khau thu nghiem phai la chuoi ngau nhien >= 16 ky tu [A-Za-z0-9_-]"; exit 98; }
done
"$MDB" -h "$H" -P "$P" -u "$U" -N -B <<SQL 2>&1 | grep -v ssl-verify | sed -E "s/PASSWORD '[^']*'/PASSWORD '<da an>'/"
CREATE USER IF NOT EXISTS 'pn7d_rt'@'localhost'   IDENTIFIED BY '${PN7D_RT_PW}';
CREATE USER IF NOT EXISTS 'pn7d_rt'@'127.0.0.1'   IDENTIFIED BY '${PN7D_RT_PW}';
CREATE USER IF NOT EXISTS 'pn7d_mig'@'localhost'  IDENTIFIED BY '${PN7D_MIG_PW}';
CREATE USER IF NOT EXISTS 'pn7d_mig'@'127.0.0.1'  IDENTIFIED BY '${PN7D_MIG_PW}';
CREATE USER IF NOT EXISTS 'pn7d_none'@'localhost' IDENTIFIED BY '${PN7D_NONE_PW}';
CREATE USER IF NOT EXISTS 'pn7d_none'@'127.0.0.1' IDENTIFIED BY '${PN7D_NONE_PW}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`claude\\_2b7d\\_%\`.* TO 'pn7d_rt'@'localhost', 'pn7d_rt'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, CREATE TEMPORARY TABLES
      ON \`claude\\_2b7d\\_%\`.* TO 'pn7d_mig'@'localhost', 'pn7d_mig'@'127.0.0.1';
SHOW GRANTS FOR 'pn7d_rt'@'127.0.0.1';
SHOW GRANTS FOR 'pn7d_mig'@'127.0.0.1';
SHOW GRANTS FOR 'pn7d_none'@'127.0.0.1';
SQL
# Go bo khi duoc phep:
#   DROP USER 'pn7d_rt'@'localhost', 'pn7d_rt'@'127.0.0.1', 'pn7d_mig'@'localhost', 'pn7d_mig'@'127.0.0.1',
#             'pn7d_none'@'localhost', 'pn7d_none'@'127.0.0.1';
