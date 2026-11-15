#!/usr/bin/env bash
# ==============================================================================
# Kiem thu Migration 12 / 13 / 13b v2 va cac script gia da vo hieu hoa (Giai doan 2B.7D)
# tren CLONE DUNG MOT LAN. Script KHONG BAO GIO ket noi database 'bandienthoai'.
#
# Bien moi truong: PN_DB_PASSWORD (bat buoc); PN_DB_HOST/PORT/USER, PN_MARIADB_BIN, PN_CLONE_PREFIX
# Tham so:
#   $1 dump TRUOC M12 (vd. dump chi doc cua database chinh, 514 SP dang kinh doanh)
#   $2 dump da chay M12/M13/13b BAN v1 (vd. dump bandienthoai_clean_acceptance)
# ==============================================================================
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
MIG="$HERE/../migrations"
HOST="${PN_DB_HOST:-127.0.0.1}"; PORT="${PN_DB_PORT:-3307}"; USER_="${PN_DB_USER:-root}"
BIN="${PN_MARIADB_BIN:-}"; MDB="${BIN:+$BIN/}mariadb"
PREFIX="${PN_CLONE_PREFIX:-claude_2b7d_m12_}"
PRE_DUMP="${1:?can dump truoc M12}"; V1_DUMP="${2:?can dump da chay ban v1}"
: "${PN_DB_PASSWORD:?Can dat PN_DB_PASSWORD}"
export MYSQL_PWD="$PN_DB_PASSWORD"
[[ "$PREFIX" =~ ^(claude_2b7[a-z]_|phonenest_it_)[a-z0-9_]*$ ]] || { echo "REFUSED: prefix"; exit 99; }
STAMP="$(date +%H%M%S)"; PASS=0; FAIL=0

db_ok() { [[ "$1" =~ ^(claude_2b7[a-z]_|phonenest_it_)[a-z0-9_]+$ && "$1" != "bandienthoai" ]]; }
q()  { db_ok "$1" || { echo "REFUSED db=$1"; exit 99; }; local d="$1"; shift; "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -N -B -D "$d" "$@" 2>&1 | grep -v "ssl-verify-server-cert"; }
run(){ local d="$1" f="$2"; shift 2; db_ok "$d" || exit 99; "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -N -B -D "$d" "$@" < "$f" 2>&1 | grep -v "ssl-verify-server-cert"; }
cs() { q "$1" -e "CHECKSUM TABLE sanpham, phienbansanpham, donhang, chitietdonhang, giohang, chitietgiohang, biendongkho" | awk '{s=s $2 ","} END{print s}'; }
check() { if [[ "$2" == "$3" ]]; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | expected=[$3] actual=[$2]"; FAIL=$((FAIL+1)); fi; }
contains() { if grep -q -- "$3" <<<"$2"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | thieu [$3] trong: $(tr '\n' ' ' <<<"$2" | cut -c1-300)"; FAIL=$((FAIL+1)); fi; }
mkclone() { local d="$1" dump="$2"; db_ok "$d" || exit 99;
  "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" -e "CREATE DATABASE \`$d\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" 2>&1 | grep -v ssl-verify;
  "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -D "$d" < "$dump" 2>&1 | grep -v ssl-verify; }

echo "== T0 phan tich tinh: khong co lenh ghi nao TRUOC buoc xac minh database dich"
for f in "$MIG"/1[2-6]*.sql; do
  r=$(python - "$f" <<'PY'
import re, sys
L = open(sys.argv[1], encoding='utf-8').read().split('\n')
guard = next((i for i, l in enumerate(L) if "IF v_db = 'bandienthoai'" in l), None)
if guard is None:
    # script khong co khoi nao: chi duoc phep chua SELECT/DELIMITER/comment
    bad = [l for l in L if l.strip() and not l.strip().startswith('--') and re.match(r'\s*(CREATE|INSERT|UPDATE|DELETE|ALTER|DROP|CALL|REPLACE|TRUNCATE)\b', l, re.I)]
    print('OK_NO_BLOCK' if not bad else 'BAD:' + bad[0].strip()); sys.exit()
in_handler = False; bad = None
for l in L[:guard]:
    s = l.strip()
    if s.startswith('DECLARE EXIT HANDLER'): in_handler = True
    if in_handler:
        if s == 'END;': in_handler = False
        continue
    if s.startswith('--'): continue
    if re.match(r'(CREATE|INSERT|UPDATE|DELETE|ALTER|DROP|CALL|REPLACE|TRUNCATE)\b', s, re.I):
        bad = s; break
print('OK' if bad is None else 'BAD:' + bad)
PY
)
  check "T0 $(basename "$f")" "${r%%:*}" "$( [[ $r == OK_NO_BLOCK ]] && echo OK_NO_BLOCK || echo OK)"
done

P="${PREFIX}p_${STAMP}"; V="${PREFIX}v_${STAMP}"; F="${PREFIX}f_${STAMP}"
echo "== Clone: $P (truoc M12), $V (da chay ban v1), $F (mo phong loi)"
mkclone "$P" "$PRE_DUMP"; mkclone "$V" "$V1_DUMP"; mkclone "$F" "$PRE_DUMP"
ORD=$(q "$P" -e "SELECT COUNT(*) FROM donhang"); LINES=$(q "$P" -e "SELECT COUNT(*) FROM chitietdonhang")
ACTIVE0=$(q "$P" -e "SELECT COUNT(*) FROM sanpham WHERE trangThai='DANG_KINH_DOANH'")
echo "   $P: don=$ORD dong don=$LINES san pham dang KD=$ACTIVE0"

echo "== T1 M12 v2 lan dau tren du lieu truoc M12 (khong gan cung so luong)"
out=$(run "$P" "$MIG/12_streamline_products_75.sql"); contains "T1 M12 COMPLETED" "$out" "^COMPLETED"
check "T1 chi con 75 SP dang KD (dung danh muc)" "$(q "$P" -e "SELECT COUNT(*) FROM sanpham WHERE trangThai='DANG_KINH_DOANH'")" "75"
check "T1 so don hang khong doi" "$(q "$P" -e "SELECT COUNT(*) FROM donhang")" "$ORD"
check "T1 so dong don khong doi" "$(q "$P" -e "SELECT COUNT(*) FROM chitietdonhang")" "$LINES"
check "T1 sao luu = so dong da doi" "$(q "$P" -e "SELECT COUNT(*) FROM pn_data_backup WHERE migration_id='12' AND giai_doan='M12_TRUOC_DML' AND bang='sanpham'")" "$((ACTIVE0-75))"
check "T1 khong tao procedure" "$(q "$P" -e "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE()")" "0"
check "T1 khong tao bang backup kieu v1" "$(q "$P" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME LIKE '%backup_pre75'")" "0"

echo "== T2 chay lai / --force khong ghi de"
c1=$(cs "$P"); out=$(run "$P" "$MIG/12_streamline_products_75.sql" --force); contains "T2 ALREADY_COMPLETED" "$out" "ALREADY_COMPLETED"
check "T2 checksum khong doi" "$(cs "$P")" "$c1"

echo "== T3 Admin doi sau M12 -> rollback chi hoan tac dong con dung gia tri M12 dat"
ADM=$(q "$P" -e "SELECT khoa FROM pn_data_backup WHERE migration_id='12' AND bang='sanpham' ORDER BY khoa LIMIT 1")
q "$P" -e "UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham='$ADM'" >/dev/null
q "$P" -e "UPDATE sanpham SET trangThai='NGUNG_KINH_DOANH' WHERE maSanPham='SP001'" >/dev/null
out=$(run "$P" "$MIG/12_streamline_products_75_rollback.sql"); contains "T3 ROLLED_BACK" "$out" "^ROLLED_BACK"
contains "T3 bao cao 1 san pham bo qua" "$out" "	1	"
check "T3 SP Admin da doi giu nguyen" "$(q "$P" -e "SELECT trangThai FROM sanpham WHERE maSanPham='$ADM'")" "DANG_KINH_DOANH"
check "T3 SP trong danh muc Admin ngung giu nguyen (M12 khong dong vao)" "$(q "$P" -e "SELECT trangThai FROM sanpham WHERE maSanPham='SP001'")" "NGUNG_KINH_DOANH"
check "T3 so SP dang KD = ban dau - 1 (SP001)" "$(q "$P" -e "SELECT COUNT(*) FROM sanpham WHERE trangThai='DANG_KINH_DOANH'")" "$((ACTIVE0-1))"
check "T3 bang sao luu KHONG bi xoa" "$(q "$P" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pn_data_backup'")" "1"
c2=$(cs "$P"); out=$(run "$P" "$MIG/12_streamline_products_75_rollback.sql" --force); contains "T3 rollback lan 2: ALREADY_ROLLED_BACK" "$out" "ALREADY_ROLLED_BACK"
check "T3 rollback lan 2 checksum khong doi" "$(cs "$P")" "$c2"
q "$P" -e "UPDATE sanpham SET trangThai='DANG_KINH_DOANH' WHERE maSanPham='SP001'" >/dev/null

echo "== T4 M13 v2: chi dien o NULL, khong ghi de gia tri Admin"
q "$P" -e "UPDATE sanpham SET pin=9999 WHERE maSanPham='SP500352'" >/dev/null
out=$(run "$P" "$MIG/13_normalize_product_specs_draft.sql"); contains "T4 M13 COMPLETED" "$out" "^COMPLETED"
check "T4 gia tri Admin (pin=9999) giu nguyen" "$(q "$P" -e "SELECT pin FROM sanpham WHERE maSanPham='SP500352'")" "9999"
check "T4 cac o NULL duoc dien" "$(q "$P" -e "SELECT COUNT(*) FROM sanpham WHERE maSanPham IN ('SP500352','SP500435','SP500445','SP500472','SP500488') AND (chip IS NULL OR kichThuocManHinh IS NULL)")" "0"
c3=$(cs "$P"); out=$(run "$P" "$MIG/13_normalize_product_specs_draft.sql" --force); contains "T4 rerun ALREADY_COMPLETED" "$out" "ALREADY_COMPLETED"
check "T4 rerun checksum khong doi" "$(cs "$P")" "$c3"
q "$P" -e "UPDATE sanpham SET chip='Admin sua tay' WHERE maSanPham='SP500435'" >/dev/null
out=$(run "$P" "$MIG/13_normalize_product_specs_draft_rollback.sql"); contains "T4 rollback 13 ROLLED_BACK" "$out" "^ROLLED_BACK"
check "T4 rollback giu o Admin sua sau M13" "$(q "$P" -e "SELECT chip FROM sanpham WHERE maSanPham='SP500435'")" "Admin sua tay"
check "T4 rollback dua o M13 dien ve NULL" "$(q "$P" -e "SELECT IFNULL(chip,'NULL') FROM sanpham WHERE maSanPham='SP500352'")" "NULL"
check "T4 rollback khong dong vao pin Admin" "$(q "$P" -e "SELECT pin FROM sanpham WHERE maSanPham='SP500352'")" "9999"

echo "== T5 M13b v2: doi cau hinh PB500488, khong doi gia; tu choi khi da co tham chieu"
G0=$(q "$P" -e "SELECT CONCAT(giaBan,'|',IFNULL(giaNiemYet,'-')) FROM phienbansanpham WHERE maPhienBan='PB500488'")
out=$(run "$P" "$MIG/13b_propose_vivo_y100_specs_only.sql"); contains "T5 13b COMPLETED" "$out" "^COMPLETED"
check "T5 cau hinh moi" "$(q "$P" -e "SELECT CONCAT(ram,'/',boNho) FROM phienbansanpham WHERE maPhienBan='PB500488'")" "8/128"
check "T5 gia giu nguyen" "$(q "$P" -e "SELECT CONCAT(giaBan,'|',IFNULL(giaNiemYet,'-')) FROM phienbansanpham WHERE maPhienBan='PB500488'")" "$G0"
out=$(run "$P" "$MIG/13b_propose_vivo_y100_specs_only_rollback.sql"); contains "T5 rollback 13b ROLLED_BACK" "$out" "^ROLLED_BACK"
check "T5 rollback ve 4/64" "$(q "$P" -e "SELECT CONCAT(ram,'/',boNho) FROM phienbansanpham WHERE maPhienBan='PB500488'")" "4/64"
q "$P" -e "INSERT INTO biendongkho (maBienDong, maPhienBan, loaiBienDong, soLuong, tonTruoc, tonSau, ghiChu) SELECT 'BDK_T5_TEST', 'PB500488', 'NHAP_KHO', 1, soLuongTon, soLuongTon, 'test 2B.7D' FROM phienbansanpham WHERE maPhienBan='PB500488'" >/dev/null
c4=$(cs "$P"); out=$(run "$P" "$MIG/13b_propose_vivo_y100_specs_only.sql" --force); contains "T5 tu choi khi bien the da co bien dong kho" "$out" "REFUSED"
check "T5 checksum khong doi khi tu choi" "$(cs "$P")" "$c4"

echo "== T6 script gia (13b pricing, 14) da vo hieu hoa: khong ghi gi"
c5=$(cs "$P")
for f in 13b_propose_vivo_y100_pricing_draft 13b_propose_vivo_y100_pricing_draft_rollback 14_propose_flagship_pricing_draft 14_propose_flagship_pricing_draft_rollback; do
  out=$(run "$P" "$MIG/$f.sql" --force); contains "T6 $f REFUSED_DEPRECATED" "$out" "REFUSED_DEPRECATED"
done
check "T6 checksum khong doi" "$(cs "$P")" "$c5"

echo "== T7 tuong thich clone da chay ban v1"
c6=$(cs "$V")
out=$(run "$V" "$MIG/12_streamline_products_75.sql" --force); contains "T7 M12 tren v1: SKIPPED_EXISTING_DATA" "$out" "SKIPPED_EXISTING_DATA"
out=$(run "$V" "$MIG/13_normalize_product_specs_draft.sql" --force); contains "T7 M13 tren v1: SKIPPED_EXISTING_DATA" "$out" "SKIPPED_EXISTING_DATA"
out=$(run "$V" "$MIG/13b_propose_vivo_y100_specs_only.sql" --force); contains "T7 13b tren v1: SKIPPED_EXISTING_DATA" "$out" "SKIPPED_EXISTING_DATA"
out=$(run "$V" "$MIG/12_streamline_products_75_rollback.sql" --force); contains "T7 rollback 12 tren v1: REFUSED (khong ghi de)" "$out" "REFUSED"
check "T7 checksum du lieu clone v1 khong doi" "$(cs "$V")" "$c6"
check "T7 bang backup v1 van con" "$(q "$V" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND LOWER(TABLE_NAME)='sanpham_backup_pre75'")" "1"

echo "== T8 chua chon database -> REFUSED, khong ghi"
out=$("$MDB" -h "$HOST" -P "$PORT" -u "$USER_" -N -B < "$MIG/12_streamline_products_75.sql" 2>&1 | grep -v ssl-verify); contains "T8 khong co database: REFUSED hoac ERROR 1046 (khong lenh nao chay)" "$out" "^REFUSED\|ERROR 1046"

echo "== T9 hai tien trinh: khoa chung"
( q "$F" -e "SELECT GET_LOCK(CONCAT('pn_mig_15_16_', DATABASE()), 5), SLEEP(6)" >/dev/null ) & HOLD=$!
for i in $(seq 1 10); do [[ "$(q "$F" -e "SELECT IS_USED_LOCK(CONCAT('pn_mig_15_16_', DATABASE())) IS NOT NULL")" == "1" ]] && break; q "$F" -e "DO SLEEP(0.3)" >/dev/null; done
c7=$(cs "$F"); out=$(run "$F" "$MIG/12_streamline_products_75.sql" --force); contains "T9 REFUSED_CONCURRENT" "$out" "REFUSED_CONCURRENT"
check "T9 checksum khong doi" "$(cs "$F")" "$c7"
wait "$HOLD"

echo "== T10 loi giua chung (trigger gia lap): DML rollback, log FAILED, chay lai hoan tat"
TRG="$(mktemp)"; printf 'DELIMITER $$\nCREATE TRIGGER pn_test_fail12 BEFORE UPDATE ON sanpham FOR EACH ROW BEGIN IF NEW.maSanPham = %s THEN SIGNAL SQLSTATE %s SET MESSAGE_TEXT = %s; END IF; END$$\nDELIMITER ;\n' "'SP500500'" "'45000'" "'LOI GIA LAP'" > "$TRG"
run "$F" "$TRG"; rm -f "$TRG"
ACT_F=$(q "$F" -e "SELECT COUNT(*) FROM sanpham WHERE trangThai='DANG_KINH_DOANH'")
out=$(run "$F" "$MIG/12_streamline_products_75.sql" --force); contains "T10 FAILED" "$out" "FAILED"
check "T10 khong san pham nao bi doi" "$(q "$F" -e "SELECT COUNT(*) FROM sanpham WHERE trangThai='DANG_KINH_DOANH'")" "$ACT_F"
check "T10 sao luu DML da rollback cung giao dich" "$(q "$F" -e "SELECT COUNT(*) FROM pn_data_backup")" "0"
check "T10 log STARTED,FAILED" "$(q "$F" -e "SELECT GROUP_CONCAT(trang_thai ORDER BY log_id) FROM pn_migration_log WHERE migration_id='12'")" "STARTED,FAILED"
q "$F" -e "DROP TRIGGER pn_test_fail12" >/dev/null
out=$(run "$F" "$MIG/12_streamline_products_75.sql"); contains "T10 chay lai COMPLETED" "$out" "^COMPLETED"

echo
echo "TONG KET: PASS=$PASS FAIL=$FAIL   (clone giu lai: $P $V $F)"
[[ $FAIL -eq 0 ]]
