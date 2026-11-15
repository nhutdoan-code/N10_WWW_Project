#!/usr/bin/env bash
# ==============================================================================
# DIEN TAP NANG CAP tren CLONE tao tu BAN CHUP CHI DOC cua database chinh (Giai doan 2B.7E).
# KHONG BAO GIO ket noi bandienthoai de ghi. Thu tu nang cap thuc te: (03..11 da co) -> 12 v2 -> 15 -> 16.
# KHONG chay 13 / 13b (cho xac minh catalog) va 14 / 13b-pricing (vo hieu hoa).
#
# Bien moi truong: PN_DB_PASSWORD (quan tri — CHI de tao clone), PN7D_MIG_PW, PN7D_RT_PW, PN_MARIADB_BIN, PN_JAR
# Tham so: $1 dump chi doc cua bandienthoai
# ==============================================================================
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"; MIG="$HERE/../migrations"
H=127.0.0.1; P="${PN_DB_PORT:-3307}"; MDB="${PN_MARIADB_BIN:+$PN_MARIADB_BIN/}mariadb"
DUMP="${1:?dump chi doc cua database chinh}"
: "${PN_DB_PASSWORD:?}" "${PN7D_MIG_PW:?}" "${PN7D_RT_PW:?}" "${PN_JAR:?}"
STAMP="$(date +%H%M%S)"; PASS=0; FAIL=0; OUT="${PN_REHEARSAL_OUT:-$(mktemp -d)}"
check() { if [[ "$2" == "$3" ]]; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | expected=[$3] actual=[$2]"; FAIL=$((FAIL+1)); fi; }
contains() { if grep -q -- "$3" <<<"$2"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | thieu [$3]: $(tr '\n' ' ' <<<"$2" | cut -c1-300)"; FAIL=$((FAIL+1)); fi; }
ok() { [[ "$1" =~ ^claude_2b7d_[a-z0-9_]+$ ]]; }
as() { local u="$1" pw="$2" d="$3"; shift 3; ok "$d" || { echo "REFUSED $d"; exit 99; }; MYSQL_PWD="$pw" "$MDB" -h "$H" -P "$P" -u "$u" --default-character-set=utf8mb4 -N -B -D "$d" "$@" 2>&1 | grep -v ssl-verify; }
adm() { as root "$PN_DB_PASSWORD" "$@"; }
mig() { as pn7d_mig "$PN7D_MIG_PW" "$1" < "$2" "${@:3}"; }
mk() { ok "$1" || exit 99; MYSQL_PWD="$PN_DB_PASSWORD" "$MDB" -h "$H" -P "$P" -u root -e "CREATE DATABASE \`$1\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" 2>&1 | grep -v ssl-verify
       MYSQL_PWD="$PN_DB_PASSWORD" "$MDB" -h "$H" -P "$P" -u root --default-character-set=utf8mb4 -D "$1" < "$DUMP" 2>&1 | grep -v ssl-verify; }
LICH_SU="donhang chitietdonhang thanhtoan huydonhang lichsutrangthaidonhang giohang chitietgiohang danhgia hoidap biendongkho sudungkhuyenmai yeucaudoitra chitietdoitra khuyenmai taikhoan khachhang quantrivien diachi wishlist wishlistsanpham hanhvinguoidung thongbao nhatkyhoatdong hinhanhsanpham checkout_idempotency password_reset_otp"
csls() { adm "$1" -e "CHECKSUM TABLE $(tr ' ' ',' <<<"$LICH_SU")" | awk '{print $1"="$2}' | sed 's/^[^.]*\.//' | sort | tr '\n' ' '; }
cssp() { adm "$1" -e "CHECKSUM TABLE sanpham, phienbansanpham" | awk '{print $2}' | tr '\n' ','; }
start_check() { # $1 db -> exit code cua che do chi kiem tra schema
  DB_URL="jdbc:mariadb://127.0.0.1:$P/$1" DB_USERNAME=pn7d_rt DB_PASSWORD="$PN7D_RT_PW" JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n/+=')" \
  GEMINI_API_KEY=rehearsal-dummy timeout 150 java -jar "$PN_JAR" --server.port=18095 --spring.main.web-application-type=none \
  --app.schema-check.only=true > "$OUT/$1_$2.log" 2>&1; echo $?; }

A="claude_2b7d_rh_${STAMP}"; I="claude_2b7d_rh_int_${STAMP}"; R="claude_2b7d_rh_bk_${STAMP}"
echo "== R0 tao clone tu ban chup chi doc: $A $I $R  (nhat ky: $OUT)"
mk "$A"; mk "$I"
check "R0 dung database clone" "$(adm "$A" -e "SELECT DATABASE()")" "$A"
check "R0 so san pham" "$(adm "$A" -e "SELECT COUNT(*) FROM sanpham")" "515"
check "R0 so bien the" "$(adm "$A" -e "SELECT COUNT(*) FROM phienbansanpham")" "585"
check "R0 so don hang" "$(adm "$A" -e "SELECT COUNT(*) FROM donhang")" "9"
check "R0 schema cu: chua co cot phe duyet" "$(adm "$A" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phienbansanpham' AND COLUMN_NAME LIKE '%PheDuyet%'")" "0"
check "R0 schema cu: chua co bang pn_*" "$(adm "$A" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME LIKE 'pn\\_%'")" "0"
LS0=$(csls "$A"); SP0=$(cssp "$A")
check "R0 runtime (pn7d_rt) khong co quyen DDL" "$(as pn7d_rt "$PN7D_RT_PW" "$A" -e "ALTER TABLE sanpham ADD COLUMN rh_x INT" | grep -c 'ERROR 1142')" "1"

echo "== R1 code moi + schema cu -> khong khoi dong"
c=$(start_check "$A" truoc); [[ $c != 0 && $c != 124 ]] && r=DUNG || r="EXIT_$c"
check "R1 ung dung dung lai voi schema cu" "$r" "DUNG"
contains "R1 thong bao SCHEMA_CHECK FAILED" "$(cat "$OUT/${A}_truoc.log")" "SCHEMA_CHECK FAILED"

echo "== R2 nang cap dung thu tu bang tai khoan migration rieng: 12 v2 -> 15 -> 16"
out=$(mig "$A" "$MIG/12_streamline_products_75.sql"); contains "R2 M12 COMPLETED" "$out" "^COMPLETED"
check "R2 sau M12: 75 SP dang KD" "$(adm "$A" -e "SELECT COUNT(*) FROM sanpham WHERE trangThai='DANG_KINH_DOANH'")" "75"
out=$(mig "$A" "$MIG/15_add_sales_approval_status.sql"); contains "R2 M15 COMPLETED" "$out" "^COMPLETED"
out=$(mig "$A" "$MIG/16_add_approval_verification_data.sql"); contains "R2 M16 COMPLETED" "$out" "^COMPLETED"
check "R2 khong co bien the APPROVED do he thong" "$(adm "$A" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet='APPROVED'")" "0"
check "R2 khong bien the nao duoc ban ngay sau nang cap" "$(adm "$A" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet='APPROVED' AND dauVanTayPheDuyet IS NOT NULL")" "0"
check "R2 du lieu lich su KHONG doi (26 bang)" "$(csls "$A")" "$LS0"
check "R2 khong tao procedure/trigger" "$(adm "$A" -e "SELECT (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE())+(SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE())")" "0"
c=$(start_check "$A" sau); check "R2 code moi khoi dong (chi kiem tra schema) exit 0" "$c" "0"
contains "R2 SCHEMA_CHECK OK" "$(cat "$OUT/${A}_sau.log")" "SCHEMA_CHECK OK"
echo "   phan bo sau nang cap: $(adm "$A" -e "SELECT CONCAT(trangThai,'/',trangThaiPheDuyet,'/',IFNULL(lyDoKhoa,'-'),'=',COUNT(*)) FROM phienbansanpham GROUP BY trangThai, trangThaiPheDuyet, lyDoKhoa" | tr '\n' ' ')"
UP_SP=$(cssp "$A"); UP_ND=$(nd "$A")

echo "== R3 chay lai (ke ca --force) khong thay doi gi"
for f in 12_streamline_products_75 15_add_sales_approval_status 16_add_approval_verification_data; do
  out=$(mig "$A" "$MIG/$f.sql" --force); contains "R3 $f: ALREADY_COMPLETED" "$out" "ALREADY_COMPLETED"
done
check "R3 du lieu san pham khong doi" "$(cssp "$A")" "$UP_SP"
check "R3 du lieu lich su khong doi" "$(csls "$A")" "$LS0"

echo "== R4 gian doan giua chung (M15 DML loi do trigger gia lap) tren $I"
out=$(mig "$I" "$MIG/12_streamline_products_75.sql"); contains "R4 M12 COMPLETED" "$out" "^COMPLETED"
TRG="$(mktemp)"; printf 'DELIMITER $$\nCREATE TRIGGER rh_fail BEFORE UPDATE ON phienbansanpham FOR EACH ROW BEGIN IF NEW.maPhienBan = %s THEN SIGNAL SQLSTATE %s SET MESSAGE_TEXT = %s; END IF; END$$\nDELIMITER ;\n' "'PB500476'" "'45000'" "'LOI GIA LAP GIAN DOAN'" > "$TRG"
MYSQL_PWD="$PN_DB_PASSWORD" "$MDB" -h "$H" -P "$P" -u root -D "$I" < "$TRG" 2>&1 | grep -v ssl-verify; rm -f "$TRG"
out=$(mig "$I" "$MIG/15_add_sales_approval_status.sql"); contains "R4 M15 FAILED" "$out" "FAILED"
c=$(start_check "$I" gian_doan); [[ $c != 0 && $c != 124 ]] && r=DUNG || r="EXIT_$c"
check "R4 ung dung KHONG khoi dong khi migration dang do" "$r" "DUNG"
check "R4 khong bien the nao bi khoa/duyet do (DML da rollback)" "$(adm "$I" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet<>'PENDING_REVIEW'")" "0"
adm "$I" -e "DROP TRIGGER rh_fail" >/dev/null
out=$(mig "$I" "$MIG/15_add_sales_approval_status.sql"); contains "R4 chay lai M15 sau khi sua loi: COMPLETED" "$out" "^COMPLETED"
out=$(mig "$I" "$MIG/16_add_approval_verification_data.sql"); contains "R4 M16 COMPLETED" "$out" "^COMPLETED"
c=$(start_check "$I" phuc_hoi); check "R4 sau phuc hoi: khoi dong duoc" "$c" "0"
check "R4 ket qua nghiep vu giong lan nang cap khong gian doan" "$(nd "$I")" "$UP_ND"
check "R4 du lieu lich su khong doi" "$(csls "$I")" "$LS0"

echo "== R5 rollback co dieu kien (16 -> 15 -> 12) va Admin thay doi sau nang cap duoc ton trong"
ADM_SP=$(adm "$A" -e "SELECT khoa FROM pn_data_backup WHERE migration_id='12' AND bang='sanpham' ORDER BY khoa LIMIT 1")
adm "$A" -e "UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham='$ADM_SP'" >/dev/null
out=$(mig "$A" "$MIG/15_add_sales_approval_status_rollback.sql"); contains "R5 rollback 15 truoc 16: bi tu choi" "$out" "REFUSED"
out=$(mig "$A" "$MIG/16_add_approval_verification_data_rollback.sql"); contains "R5 rollback 16 ROLLED_BACK" "$out" "ROLLED_BACK"
out=$(mig "$A" "$MIG/15_add_sales_approval_status_rollback.sql"); contains "R5 rollback 15 ROLLED_BACK" "$out" "ROLLED_BACK"
out=$(mig "$A" "$MIG/12_streamline_products_75_rollback.sql"); contains "R5 rollback 12 ROLLED_BACK" "$out" "^ROLLED_BACK"
check "R5 SP Admin doi sau nang cap duoc giu" "$(adm "$A" -e "SELECT trangThai FROM sanpham WHERE maSanPham='$ADM_SP'")" "DANG_KINH_DOANH"
check "R5 sau rollback: 514 SP dang KD nhu ban dau" "$(adm "$A" -e "SELECT COUNT(*) FROM sanpham WHERE trangThai='DANG_KINH_DOANH'")" "514"
check "R5 sau rollback: du lieu SP + bien the TRUNG KHOP ban chup (checksum)" "$(cssp "$A")" "$SP0"
check "R5 du lieu lich su khong doi" "$(csls "$A")" "$LS0"
check "R5 bang backup/log giu lai (khong DROP)" "$(adm "$A" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('pn_migration_log','pn_pheduyet_backup','pn_data_backup')")" "3"
c=$(start_check "$A" sau_rollback); [[ $c != 0 && $c != 124 ]] && r=DUNG || r="EXIT_$c"
check "R5 code moi KHONG chay tren schema da rollback" "$r" "DUNG"

echo "== R6 khoi phuc toan phan tu ban sao luu (dump chi doc) vao $R"
mk "$R"
check "R6 du lieu SP khop ban chup" "$(cssp "$R")" "$SP0"
check "R6 du lieu lich su khop ban chup" "$(csls "$R")" "$LS0"

echo
echo "TONG KET: PASS=$PASS FAIL=$FAIL   (clone giu lai: $A $I $R; log: $OUT)"
[[ $FAIL -eq 0 ]]
