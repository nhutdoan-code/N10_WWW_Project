#!/usr/bin/env bash
# ==============================================================================
# Kiem thu tich hop Migration 15 v2 / 16 / rollback / restore tren CLONE DUNG MOT LAN.
#
# Bien moi truong bat buoc:
#   PN_DB_PASSWORD   mat khau MariaDB (khong in ra man hinh)
# Tuy chon:
#   PN_DB_HOST (127.0.0.1)  PN_DB_PORT (3307)  PN_DB_USER (root)
#   PN_MARIADB_BIN  thu muc chua mariadb.exe (mac dinh: lay tu PATH)
#   PN_CLONE_PREFIX tien to ten clone (mac dinh claude_2b7c_mig_) - chi chap nhan
#                   claude_2b7x_* hoac phonenest_it_*
# Tham so:
#   $1  file dump CHUA ap dung Migration 15 (vd. dump bandienthoai_recovery_sandbox)
#   $2  file dump DA ap dung Migration 15 v1 (vd. dump bandienthoai_clean_acceptance)
# Script KHONG BAO GIO ket noi database 'bandienthoai'; cac clone duoc giu lai de doi chieu.
# ==============================================================================
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
MIG="$HERE/../migrations"
HOST="${PN_DB_HOST:-127.0.0.1}"; PORT="${PN_DB_PORT:-3307}"; USER_="${PN_DB_USER:-root}"
BIN="${PN_MARIADB_BIN:-}"; MDB="${BIN:+$BIN/}mariadb"
PREFIX="${PN_CLONE_PREFIX:-claude_2b7c_mig_}"
PRE15_DUMP="${1:?can file dump chua co Migration 15}"
LEGACY_DUMP="${2:?can file dump da co Migration 15 v1}"
: "${PN_DB_PASSWORD:?Can dat PN_DB_PASSWORD}"
export MYSQL_PWD="$PN_DB_PASSWORD"

[[ "$PREFIX" =~ ^(claude_2b7[a-z]_|phonenest_it_)[a-z0-9_]*$ ]] || { echo "REFUSED: prefix khong hop le"; exit 99; }
STAMP="$(date +%H%M%S)"
PASS=0; FAIL=0

db_ok() { [[ "$1" =~ ^(claude_2b7[a-z]_|phonenest_it_)[a-z0-9_]+$ && "$1" != "bandienthoai" ]]; }
q()  { db_ok "$1" || { echo "REFUSED db=$1"; exit 99; }; local d="$1"; shift; "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -N -B -D "$d" "$@" 2>&1 | grep -v "ssl-verify-server-cert"; }
run(){ local d="$1" f="$2"; shift 2; db_ok "$d" || exit 99; "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -N -B -D "$d" "$@" < "$f" 2>&1 | grep -v "ssl-verify-server-cert"; }
cs() { q "$1" -e "CHECKSUM TABLE phienbansanpham" | awk '{print $2}'; }
check() { if [[ "$2" == "$3" ]]; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | expected=[$3] actual=[$2]"; FAIL=$((FAIL+1)); fi; }
contains() { if grep -q -- "$3" <<<"$2"; then echo "PASS  $1"; PASS=$((PASS+1)); else echo "FAIL  $1 | thieu [$3] trong: $(tr '\n' ' ' <<<"$2" | cut -c1-300)"; FAIL=$((FAIL+1)); fi; }
mkclone() { local d="$1" dump="$2"; db_ok "$d" || exit 99;
  "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" -e "CREATE DATABASE \`$d\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" 2>&1 | grep -v ssl-verify;
  "$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -D "$d" < "$dump" 2>&1 | grep -v ssl-verify; }

A="${PREFIX}a_${STAMP}"; B="${PREFIX}b_${STAMP}"; C="${PREFIX}c_${STAMP}"
echo "== Clone: $A (chua M15), $B (M15 v1 legacy), $C (chua M15, mo phong loi giua chung)"
mkclone "$A" "$PRE15_DUMP"; mkclone "$B" "$LEGACY_DUMP"; mkclone "$C" "$PRE15_DUMP"
ORDERS_A=$(q "$A" -e "SELECT COUNT(*) FROM donhang")

echo "== T1 ap dung M15 lan dau (khong phu thuoc so don hang = $ORDERS_A)"
out=$(run "$A" "$MIG/15_add_sales_approval_status.sql"); contains "T1 M15 COMPLETED" "$out" "^COMPLETED"
check "T1 khong tao APPROVED" "$(q "$A" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet='APPROVED'")" "0"
check "T1 11 bien the gia seed BLOCKED" "$(q "$A" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet='BLOCKED' AND trangThai='DANG_KINH_DOANH'")" "11"
check "T1 bien the ngung KD deu BLOCKED" "$(q "$A" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThai='NGUNG_KINH_DOANH' AND trangThaiPheDuyet<>'BLOCKED'")" "0"
check "T1 so don hang khong doi" "$(q "$A" -e "SELECT COUNT(*) FROM donhang")" "$ORDERS_A"
check "T1 sao luu append-only du dong" "$(q "$A" -e "SELECT COUNT(*) FROM pn_pheduyet_backup WHERE giai_doan='M15_TRUOC_DML'")" "$(q "$A" -e "SELECT COUNT(*) FROM phienbansanpham")"

echo "== T2 chay lai M15 (thuong va --force) khong thay doi"
c0=$(cs "$A"); out=$(run "$A" "$MIG/15_add_sales_approval_status.sql"); contains "T2 M15 rerun ALREADY_COMPLETED" "$out" "ALREADY_COMPLETED"
run "$A" "$MIG/15_add_sales_approval_status.sql" --force >/dev/null; check "T2 checksum sau rerun --force" "$(cs "$A")" "$c0"

echo "== T3 ap dung M16 + chay lai"
out=$(run "$A" "$MIG/16_add_approval_verification_data.sql"); contains "T3 M16 COMPLETED" "$out" "^COMPLETED"
check "T3 BLOCKED co lyDoKhoa" "$(q "$A" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet='BLOCKED' AND (lyDoKhoa IS NULL OR giaBanKhiKhoa IS NULL)")" "0"
check "T3 11 bien the GIA_SEED" "$(q "$A" -e "SELECT COUNT(*) FROM phienbansanpham WHERE lyDoKhoa='GIA_SEED_CHUA_XAC_MINH'")" "11"
c1=$(cs "$A"); out=$(run "$A" "$MIG/16_add_approval_verification_data.sql" --force); contains "T3 M16 rerun ALREADY_COMPLETED" "$out" "ALREADY_COMPLETED"
check "T3 checksum sau rerun M16 --force" "$(cs "$A")" "$c1"

echo "== T4 quyet dinh Admin + chay lai M15/M16 voi --force + log bi xoa"
q "$A" -e "UPDATE phienbansanpham SET trangThaiPheDuyet='APPROVED', nguoiPheDuyet='TK001', ngayPheDuyet=NOW(), lyDoPheDuyet='SIM admin approve', giaPheDuyet=giaBan, giaNiemYetPheDuyet=giaNiemYet, nguonXacMinh='SIM nguon', dauVanTayPheDuyet=REPEAT('a',64) WHERE maPhienBan='PB500468';
           UPDATE phienbansanpham SET trangThaiPheDuyet='BLOCKED', nguoiPheDuyet='TK001', ngayPheDuyet=NOW(), lyDoPheDuyet='SIM admin block', lyDoKhoa='QUYET_DINH_QUAN_TRI', giaBanKhiKhoa=giaBan WHERE maPhienBan='PB500269'"
c2=$(cs "$A")
run "$A" "$MIG/15_add_sales_approval_status.sql" --force >/dev/null; run "$A" "$MIG/16_add_approval_verification_data.sql" --force >/dev/null
check "T4 --force khong ghi de quyet dinh Admin" "$(cs "$A")" "$c2"
q "$A" -e "DELETE FROM pn_migration_log WHERE migration_id IN ('15','16')"
out=$(run "$A" "$MIG/15_add_sales_approval_status.sql" --force); contains "T4 mat log -> SKIPPED_EXISTING_DATA" "$out" "SKIPPED_EXISTING_DATA"
check "T4 checksum sau khi mat log" "$(cs "$A")" "$c2"
check "T4 quyet dinh Admin con nguyen" "$(q "$A" -e "SELECT CONCAT(trangThaiPheDuyet,'/',nguoiPheDuyet) FROM phienbansanpham WHERE maPhienBan='PB500468'")" "APPROVED/TK001"

echo "== T5 rollback M15 khi M16 con ton tai -> tu choi"
out=$(run "$A" "$MIG/15_add_sales_approval_status_rollback.sql" --force); contains "T5 REFUSED" "$out" "REFUSED"
check "T5 checksum khong doi" "$(cs "$A")" "$c2"

echo "== T6 rollback M16 roi M15, chay lai rollback"
out=$(run "$A" "$MIG/16_add_approval_verification_data_rollback.sql"); contains "T6 RB16 ROLLED_BACK" "$out" "ROLLED_BACK"
RB16=$(q "$A" -e "SELECT run_id FROM pn_migration_log WHERE migration_id='16' AND trang_thai='ROLLED_BACK' ORDER BY log_id DESC LIMIT 1")
out=$(run "$A" "$MIG/16_add_approval_verification_data_rollback.sql"); contains "T6 RB16 lan 2 NOTHING" "$out" "NOTHING_TO_ROLLBACK"
out=$(run "$A" "$MIG/15_add_sales_approval_status_rollback.sql"); contains "T6 RB15 ROLLED_BACK" "$out" "ROLLED_BACK"
out=$(run "$A" "$MIG/15_add_sales_approval_status_rollback.sql"); contains "T6 RB15 lan 2 NOTHING" "$out" "NOTHING_TO_ROLLBACK"
check "T6 cot phe duyet da bi xoa" "$(q "$A" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phienbansanpham' AND COLUMN_NAME LIKE '%PheDuyet%'")" "0"
BK_RB16=$(q "$A" -e "SELECT COUNT(*) FROM pn_pheduyet_backup WHERE run_id='$RB16'")

echo "== T7 ap dung lai M15 + M16, sao luu cu van con (append-only)"
out=$(run "$A" "$MIG/15_add_sales_approval_status.sql"); contains "T7 M15 ap lai COMPLETED" "$out" "^COMPLETED"
out=$(run "$A" "$MIG/16_add_approval_verification_data.sql"); contains "T7 M16 ap lai COMPLETED" "$out" "^COMPLETED"
check "T7 ban sao luu RB16 khong bi ghi de" "$(q "$A" -e "SELECT COUNT(*) FROM pn_pheduyet_backup WHERE run_id='$RB16'")" "$BK_RB16"
check "T7 quyet dinh Admin chua tu quay lai (can restore)" "$(q "$A" -e "SELECT trangThaiPheDuyet FROM phienbansanpham WHERE maPhienBan='PB500468'")" "PENDING_REVIEW"

echo "== T8 khoi phuc tu ban sao luu RB16 (co 1 bien the doi gia sau sao luu)"
q "$A" -e "UPDATE phienbansanpham SET giaBan=giaBan+1000 WHERE maPhienBan='PB500269'"
out=$("$MDB" -h "$HOST" -P "$PORT" -u "$USER_" --default-character-set=utf8mb4 -N -B -D "$A" -e "SET @pn_restore_run_id='$RB16'; source $(cygpath -m "$MIG/15_16_restore_approval_from_backup.sql" 2>/dev/null || echo "$MIG/15_16_restore_approval_from_backup.sql");" 2>&1 | grep -v ssl-verify)
contains "T8 RESTORED" "$out" "RESTORED"
check "T8 APPROVED khoi phuc khi du lieu khop" "$(q "$A" -e "SELECT CONCAT(trangThaiPheDuyet,'/',nguoiPheDuyet,'/',IFNULL(dauVanTayPheDuyet,'-')) FROM phienbansanpham WHERE maPhienBan='PB500468'")" "APPROVED/TK001/$(printf 'a%.0s' {1..64})"
check "T8 BLOCKED giu nguyen khi du lieu da doi" "$(q "$A" -e "SELECT trangThaiPheDuyet FROM phienbansanpham WHERE maPhienBan='PB500269'")" "BLOCKED"
c3=$(cs "$A")
"$MDB" -h "$HOST" -P "$PORT" -u "$USER_" -N -B -D "$A" -e "SET @pn_restore_run_id='$RB16'; source $(cygpath -m "$MIG/15_16_restore_approval_from_backup.sql" 2>/dev/null || echo "$MIG/15_16_restore_approval_from_backup.sql");" >/dev/null 2>&1
check "T8 restore lan 2 khong ghi de (Admin da quyet dinh)" "$(cs "$A")" "$c3"

echo "== T9 database legacy (M15 v1 da ap dung) -> M15 v2 khong ghi de"
c4=$(cs "$B"); out=$(run "$B" "$MIG/15_add_sales_approval_status.sql" --force); contains "T9 SKIPPED_EXISTING_DATA" "$out" "SKIPPED_EXISTING_DATA"
check "T9 checksum legacy khong doi" "$(cs "$B")" "$c4"
out=$(run "$B" "$MIG/16_add_approval_verification_data.sql"); contains "T9 M16 tren legacy COMPLETED" "$out" "^COMPLETED"
check "T9 APPROVED legacy thieu dau van tay (fail-closed)" "$(q "$B" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet='APPROVED' AND dauVanTayPheDuyet IS NULL")" "3"

echo "== T10 loi giua chung: DDL xong, DML that bai (trigger gia lap loi) roi chay lai"
TRG="$(mktemp)"; printf 'DELIMITER $$\nCREATE TRIGGER pn_test_fail BEFORE UPDATE ON phienbansanpham FOR EACH ROW BEGIN IF NEW.maPhienBan = %s THEN SIGNAL SQLSTATE %s SET MESSAGE_TEXT = %s; END IF; END$$\nDELIMITER ;\n' "'PB500476'" "'45000'" "'LOI GIA LAP'" > "$TRG"
run "$C" "$TRG"; rm -f "$TRG"
check "T10 trigger gia lap loi da tao" "$(q "$C" -e "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME='pn_test_fail'")" "1"
out=$(run "$C" "$MIG/15_add_sales_approval_status.sql" --force); contains "T10 FAILED duoc ghi nhan" "$out" "FAILED"
check "T10 DML da rollback: khong co dong BLOCKED" "$(q "$C" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet<>'PENDING_REVIEW' OR lyDoPheDuyet IS NOT NULL")" "0"
check "T10 log: STARTED, DDL_DONE, FAILED" "$(q "$C" -e "SELECT GROUP_CONCAT(trang_thai ORDER BY log_id) FROM pn_migration_log WHERE migration_id='15'")" "STARTED,DDL_DONE,FAILED"
q "$C" -e "DROP TRIGGER pn_test_fail" >/dev/null
out=$(run "$C" "$MIG/15_add_sales_approval_status.sql"); contains "T10 chay lai sau khi sua loi COMPLETED" "$out" "^COMPLETED"
check "T10 sau khi chay lai: 11 bien the gia seed BLOCKED" "$(q "$C" -e "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet='BLOCKED' AND trangThai='DANG_KINH_DOANH'")" "11"
check "T10 khong con procedure/trigger rac" "$(q "$C" -e "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE()")" "0"

echo "== T11 (2B.7C) hai tien trinh cung chay migration"
D="${PREFIX}d_${STAMP}"; mkclone "$D" "$PRE15_DUMP"; c5=$(cs "$D")
# Phien khac giu khoa chung trong 8 giay (mo phong tien trinh dang chay)
( q "$D" -e "SELECT GET_LOCK(CONCAT('pn_mig_15_16_', DATABASE()), 5), SLEEP(8)" >/dev/null ) &
HOLD=$!
for i in 1 2 3 4 5 6 7 8 9 10; do
  [[ "$(q "$D" -e "SELECT IS_USED_LOCK(CONCAT('pn_mig_15_16_', DATABASE())) IS NOT NULL")" == "1" ]] && break
  q "$D" -e "DO SLEEP(0.3)" >/dev/null
done
out=$(run "$D" "$MIG/15_add_sales_approval_status.sql" --force); contains "T11 M15 bi tu choi khi co tien trinh khac" "$out" "REFUSED_CONCURRENT"
out=$(run "$D" "$MIG/16_add_approval_verification_data.sql" --force); contains "T11 M16 bi tu choi khi co tien trinh khac" "$out" "REFUSED_CONCURRENT"
check "T11 checksum khong doi khi bi tu choi" "$(cs "$D")" "$c5"
check "T11 khong them cot khi bi tu choi" "$(q "$D" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phienbansanpham' AND COLUMN_NAME='trangThaiPheDuyet'")" "0"
wait "$HOLD"
# Hai lan chay THAT SU dong thoi: dung mot lan COMPLETED, khong nhan doi sao luu/log
O1="$(mktemp)"; O2="$(mktemp)"
run "$D" "$MIG/15_add_sales_approval_status.sql" > "$O1" & P1=$!
run "$D" "$MIG/15_add_sales_approval_status.sql" > "$O2" & P2=$!
wait "$P1"; wait "$P2"
both="$(cat "$O1" "$O2")"; rm -f "$O1" "$O2"
check "T11 dung mot lan COMPLETED" "$(grep -c "^COMPLETED" <<<"$both")" "1"
contains "T11 lan con lai bi tu choi hoac da hoan tat" "$both" "REFUSED_CONCURRENT\|ALREADY_COMPLETED"
check "T11 log chi mot COMPLETED" "$(q "$D" -e "SELECT COUNT(*) FROM pn_migration_log WHERE migration_id='15' AND trang_thai='COMPLETED'")" "1"
check "T11 sao luu khong nhan doi" "$(q "$D" -e "SELECT COUNT(*) FROM pn_pheduyet_backup WHERE giai_doan='M15_TRUOC_DML'")" "$(q "$D" -e "SELECT COUNT(*) FROM phienbansanpham")"
out=$(run "$D" "$MIG/16_add_approval_verification_data.sql"); contains "T11 M16 sau khi khoa duoc giai phong" "$out" "^COMPLETED"

echo "== T12 (2B.7C) log COMPLETED nhung schema khong day du -> tu choi, khong tu sua"
q "$D" -e "ALTER TABLE phienbansanpham DROP COLUMN nguonXacMinh" >/dev/null
c6=$(cs "$D")
out=$(run "$D" "$MIG/16_add_approval_verification_data.sql" --force); contains "T12 M16 INCONSISTENT_SCHEMA" "$out" "INCONSISTENT_SCHEMA"
check "T12 M16 khong tu tao lai cot" "$(q "$D" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phienbansanpham' AND COLUMN_NAME='nguonXacMinh'")" "0"
check "T12 M16 log cuoi van COMPLETED (khong ghi de)" "$(q "$D" -e "SELECT trang_thai FROM pn_migration_log WHERE migration_id='16' ORDER BY log_id DESC LIMIT 1")" "COMPLETED"
q "$D" -e "ALTER TABLE phienbansanpham DROP COLUMN lyDoPheDuyet" >/dev/null
c7=$(cs "$D")
out=$(run "$D" "$MIG/15_add_sales_approval_status.sql" --force); contains "T12 M15 INCONSISTENT_SCHEMA" "$out" "INCONSISTENT_SCHEMA"
check "T12 M15 checksum khong doi" "$(cs "$D")" "$c7"
check "T12 M15 khong tu tao lai cot" "$(q "$D" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phienbansanpham' AND COLUMN_NAME='lyDoPheDuyet'")" "0"

echo
echo "TONG KET: PASS=$PASS FAIL=$FAIL   (clone giu lai: $A $B $C $D)"
[[ $FAIL -eq 0 ]]
