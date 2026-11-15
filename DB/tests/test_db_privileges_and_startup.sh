#!/usr/bin/env bash
# ==============================================================================
# Kiem thu quyen database va kiem tra schema khi khoi dong (Giai doan 2B.7D) — CHI tren clone.
#
# Can: tai khoan thu nghiem pn7d_rt / pn7d_mig / pn7d_none (DB/admin/setup_test_accounts_on_clone.sh)
# Bien moi truong:
#   PN_DB_PASSWORD   tai khoan quan tri — CHI dung de tao clone fixture (claude_2b7d_*)
#   PN7D_RT_PW, PN7D_MIG_PW, PN7D_NONE_PW
#   PN_MARIADB_BIN, PN_JAR (duong dan phonenest-*.jar)
# Tham so: $1 dump CHUA co M15 (recovery sandbox), $2 dump nguon cho clone hop le (clean_acceptance)
#
# Moi cau lenh thu ghi vao bandienthoai la cau lenh KHONG anh huong dong nao (WHERE 1=0): ke ca neu
# quyen bi cau hinh sai, du lieu chinh van khong doi. Checksum database chinh duoc so sanh truoc/sau.
# ==============================================================================
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"; MIG="$HERE/../migrations"
H=127.0.0.1; P="${PN_DB_PORT:-3307}"; MDB="${PN_MARIADB_BIN:+$PN_MARIADB_BIN/}mariadb"
OLD_DUMP="${1:?dump chua M15}"; OK_DUMP="${2:?dump nguon clone hop le}"
: "${PN_DB_PASSWORD:?}" "${PN7D_RT_PW:?}" "${PN7D_MIG_PW:?}" "${PN7D_NONE_PW:?}" "${PN_JAR:?}"
STAMP="$(date +%H%M%S)"; PASS=0; FAIL=0
check() { if [[ "$2" == "$3" ]]; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | expected=[$3] actual=[$2]"; FAIL=$((FAIL+1)); fi; }
contains() { if grep -q -- "$3" <<<"$2"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | thieu [$3] trong: $(tr '\n' ' ' <<<"$2" | cut -c1-400)"; FAIL=$((FAIL+1)); fi; }
clone_ok() { [[ "$1" =~ ^claude_2b7d_[a-z0-9_]+$ ]]; }
as() { local u="$1" pw="$2" d="$3"; shift 3; MYSQL_PWD="$pw" "$MDB" -h "$H" -P "$P" -u "$u" --default-character-set=utf8mb4 -N -B ${d:+-D "$d"} "$@" 2>&1 | grep -v ssl-verify; }
root() { local d="$1"; shift; clone_ok "$d" || { echo "REFUSED root tren $d"; exit 99; }; as root "$PN_DB_PASSWORD" "$d" "$@"; }
mk() { clone_ok "$1" || exit 99; as root "$PN_DB_PASSWORD" "" -e "CREATE DATABASE \`$1\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"; MYSQL_PWD="$PN_DB_PASSWORD" "$MDB" -h "$H" -P "$P" -u root --default-character-set=utf8mb4 -D "$1" < "$2" 2>&1 | grep -v ssl-verify; }
main_cs() { MYSQL_PWD="$PN_DB_PASSWORD" "$MDB" -h "$H" -P "$P" -u root -N -B -D bandienthoai --init-command="SET SESSION TRANSACTION READ ONLY" \
  -e "CHECKSUM TABLE sanpham, phienbansanpham, donhang, chitietdonhang, giohang, chitietgiohang, biendongkho, taikhoan, khuyenmai, nhatkyhoatdong" 2>&1 | grep -v ssl-verify | awk '{s=s $2 ","} END{print s}'; }
cs() { root "$1" -e "CHECKSUM TABLE sanpham, phienbansanpham, donhang" | awk '{s=s $2 ","} END{print s}'; }

MAIN0=$(main_cs)
OK="claude_2b7d_it_okpriv_${STAMP}"; OLD="claude_2b7d_old_${STAMP}"; M15="claude_2b7d_m15only_${STAMP}"
LOGX="claude_2b7d_logx_${STAMP}"; MIGC="claude_2b7d_mig_${STAMP}"
echo "== Chuan bi clone (quan tri): $OK $OLD $M15 $LOGX $MIGC"
mk "$OK" "$OK_DUMP";  as pn7d_mig "$PN7D_MIG_PW" "$OK" < "$MIG/15_add_sales_approval_status.sql" >/dev/null; as pn7d_mig "$PN7D_MIG_PW" "$OK" < "$MIG/16_add_approval_verification_data.sql" >/dev/null
mk "$OLD" "$OLD_DUMP"
mk "$M15" "$OLD_DUMP"; root "$M15" < "$MIG/15_add_sales_approval_status.sql" >/dev/null
mk "$LOGX" "$OK_DUMP"; root "$LOGX" < "$MIG/15_add_sales_approval_status.sql" >/dev/null; root "$LOGX" < "$MIG/16_add_approval_verification_data.sql" >/dev/null
root "$LOGX" -e "DELETE FROM pn_migration_log WHERE migration_id='16'" >/dev/null
mk "$MIGC" "$OLD_DUMP"

echo "== D1 runtime (pn7d_rt) doc/ghi du lieu can thiet trong clone (giao dich rollback)"
out=$(as pn7d_rt "$PN7D_RT_PW" "$OK" -e "START TRANSACTION; SELECT COUNT(*) FROM sanpham; UPDATE phienbansanpham SET soLuongTon=soLuongTon WHERE maPhienBan='PB010'; INSERT INTO biendongkho (maBienDong, maPhienBan, loaiBienDong, soLuong, tonTruoc, tonSau, ghiChu) VALUES ('BDK_PN7D_TEST','PB010','DIEU_CHINH',0,0,0,'pn7d'); DELETE FROM biendongkho WHERE maBienDong='BDK_PN7D_TEST'; ROLLBACK; SELECT 'DML_OK';")
contains "D1 SELECT/UPDATE/INSERT/DELETE duoc phep" "$out" "DML_OK"

echo "== D2 runtime KHONG thay doi duoc schema"
for sql in "ALTER TABLE sanpham ADD COLUMN pn7d_x INT" "CREATE TABLE pn7d_t (x INT)" "DROP TABLE IF EXISTS pn7d_khong_ton_tai" "CREATE INDEX pn7d_i ON sanpham (tenMay)"; do
  out=$(as pn7d_rt "$PN7D_RT_PW" "$OK" -e "$sql"); contains "D2 tu choi: $sql" "$out" "ERROR 1142"
done
check "D2 schema clone khong doi" "$(root "$OK" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME LIKE 'pn7d%'")" "0"

echo "== D3 tai khoan runtime / migration / test KHONG ghi (va khong doc) duoc bandienthoai"
for u in pn7d_rt pn7d_mig pn7d_none; do
  pw_var="PN7D_$(tr a-z A-Z <<<"${u#pn7d_}")_PW"; pw="${!pw_var}"
  out=$(as "$u" "$pw" "" -e "USE bandienthoai"); contains "D3 $u: USE bandienthoai bi tu choi" "$out" "ERROR 1044"
  out=$(as "$u" "$pw" "" -e "UPDATE bandienthoai.sanpham SET maSanPham=maSanPham WHERE 1=0"); contains "D3 $u: UPDATE (0 dong) bi tu choi" "$out" "ERROR 1142"
  out=$(as "$u" "$pw" "" -e "DELETE FROM bandienthoai.donhang WHERE 1=0"); contains "D3 $u: DELETE (0 dong) bi tu choi" "$out" "ERROR 1142"
  out=$(as "$u" "$pw" "" -e "SELECT COUNT(*) FROM bandienthoai.taikhoan"); contains "D3 $u: SELECT bi tu choi" "$out" "ERROR 1142"
done

echo "== D4 migration chi chay voi tai khoan du quyen"
c0=$(cs "$MIGC")
out=$(as pn7d_rt "$PN7D_RT_PW" "$MIGC" < "$MIG/15_add_sales_approval_status.sql"); contains "D4 M15 bang runtime: that bai (thieu quyen)" "$out" "FAILED\|ERROR 1142"
check "D4 runtime: khong them cot" "$(root "$MIGC" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phienbansanpham' AND COLUMN_NAME='trangThaiPheDuyet'")" "0"
check "D4 runtime: du lieu khong doi" "$(cs "$MIGC")" "$c0"
out=$(as pn7d_mig "$PN7D_MIG_PW" "$MIGC" < "$MIG/15_add_sales_approval_status.sql"); contains "D4 M15 bang pn7d_mig: COMPLETED" "$out" "^COMPLETED"
out=$(as pn7d_mig "$PN7D_MIG_PW" "$MIGC" < "$MIG/16_add_approval_verification_data.sql"); contains "D4 M16 bang pn7d_mig: COMPLETED" "$out" "^COMPLETED"
out=$(as pn7d_mig "$PN7D_MIG_PW" "$MIGC" -e "DROP DATABASE \`$MIGC\`"); contains "D4 pn7d_mig khong DROP DATABASE duoc" "$out" "ERROR 1044"

echo "== G kiem tra schema khi khoi dong — tien trinh Backend THAT, tai khoan runtime (khong root)"
start() { # $1 db  $2 user  $3 pw  $4 port  $5 log  [extra...]
  local db="$1" u="$2" pw="$3" port="$4" log="$5"; shift 5
  DB_URL="jdbc:mariadb://127.0.0.1:$P/$db" DB_USERNAME="$u" DB_PASSWORD="$pw" JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n/+=')" \
  GEMINI_API_KEY=startup-test-dummy timeout 150 java -jar "$PN_JAR" --server.port="$port" "$@" > "$log" 2>&1; echo $?; }
TMPD="$(mktemp -d)"
for t in "OLD:$OLD:phienbansanpham.trangThaiPheDuyet" "M15:$M15:dauVanTayPheDuyet" "LOGX:$LOGX:Migration 16"; do
  ten="${t%%:*}"; rest="${t#*:}"; db="${rest%%:*}"; can="${rest#*:}"
  truoc=$(cs "$db"); bang0=$(root "$db" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")
  code=$(start "$db" pn7d_rt "$PN7D_RT_PW" 18090 "$TMPD/$ten.log")
  [[ "$code" != "0" && "$code" != "124" ]] && r=EXIT_LOI || r="EXIT_$code"
  check "G $ten: ung dung dung lai (exit != 0)" "$r" "EXIT_LOI"
  contains "G $ten: SCHEMA_CHECK FAILED + thanh phan ($can)" "$(grep -a -A12 SCHEMA_CHECK "$TMPD/$ten.log")" "$can"
  check "G $ten: KHONG mo cong HTTP" "$(grep -c 'Tomcat started on port' "$TMPD/$ten.log")" "0"
  check "G $ten: khong ghi gi vao database (checksum)" "$(cs "$db")" "$truoc"
  check "G $ten: khong tao bang nao" "$(root "$db" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")" "$bang0"
done
code=$(start "$OK" pn7d_rt "$PN7D_RT_PW" 18091 "$TMPD/ok.log" --spring.main.web-application-type=none --app.schema-check.only=true)
check "G OK: che do chi kiem tra thoat 0" "$code" "0"
contains "G OK: SCHEMA_CHECK OK" "$(cat "$TMPD/ok.log")" "SCHEMA_CHECK OK"
code=$(start "$OK" pn7d_none "$PN7D_NONE_PW" 18092 "$TMPD/none.log")
[[ "$code" != "0" && "$code" != "124" ]] && r=EXIT_LOI || r="EXIT_$code"
check "G thieu quyen (pn7d_none): ung dung dung lai an toan" "$r" "EXIT_LOI"
check "G thieu quyen: KHONG mo cong HTTP" "$(grep -c 'Tomcat started on port' "$TMPD/none.log")" "0"
echo "   log: $TMPD"

check "Database chinh khong doi (checksum 10 bang)" "$(main_cs)" "$MAIN0"
echo
echo "TONG KET: PASS=$PASS FAIL=$FAIL   (clone giu lai: $OK $OLD $M15 $LOGX $MIGC)"
[[ $FAIL -eq 0 ]]
