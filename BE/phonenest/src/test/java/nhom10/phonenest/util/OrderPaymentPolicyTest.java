package nhom10.phonenest.util;

import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderPaymentPolicyTest {
    private ThanhToan payment(String id, PhuongThucTt method, TrangThaiThanhToan status, int minute) {
        ThanhToan p = new ThanhToan();
        p.setMaThanhToan(id); p.setPhuongThuc(method); p.setTrangThai(status);
        p.setNgayTao(LocalDateTime.of(2026, 11, 28, 14, minute));
        return p;
    }
    @Test void capturedPaymentWinsOverLaterFailedAttempt() {
        var paid = payment("TT1", PhuongThucTt.CHUYEN_KHOAN, TrangThaiThanhToan.DA_THANH_TOAN, 1);
        var failed = payment("TT2", PhuongThucTt.CHUYEN_KHOAN, TrangThaiThanhToan.THAT_BAI, 2);
        assertSame(paid, OrderPaymentPolicy.effective(List.of(failed, paid)));
        assertFalse(OrderPaymentPolicy.canEditQuantity(List.of(failed, paid)));
    }
    @Test void evenUncollectedCodCannotBeEdited() {
        var cod = payment("TT1", PhuongThucTt.COD, TrangThaiThanhToan.CHO_THANH_TOAN, 1);
        assertFalse(OrderPaymentPolicy.canEditQuantity(List.of(cod)));
        assertFalse(OrderPaymentPolicy.canEditQuantity(List.of()));
        cod.setNgayThanhToan(LocalDateTime.now());
        assertFalse(OrderPaymentPolicy.canEditQuantity(List.of(cod)));
        cod.setNgayThanhToan(null); cod.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
        assertFalse(OrderPaymentPolicy.canEditQuantity(List.of(cod)));
    }
    @Test void codCollectionRequiresMatchingPendingPaymentAndDeliveryStage() {
        var cod = payment("TT1", PhuongThucTt.COD, TrangThaiThanhToan.CHO_THANH_TOAN, 1);
        var total = new java.math.BigDecimal("8490000");
        cod.setSoTien(total);
        for (var status : nhom10.phonenest.enums.TrangThaiDonHang.values()) {
            assertEquals(List.of("DANG_GIAO", "CHO_KHACH_XAC_NHAN", "DA_GIAO").contains(status.name()),
                    OrderPaymentPolicy.canConfirmCod(status, cod, total));
        }
        var delivered = nhom10.phonenest.enums.TrangThaiDonHang.DA_GIAO;
        assertFalse(OrderPaymentPolicy.canConfirmCod(delivered, cod, total.add(java.math.BigDecimal.ONE)));
        assertFalse(OrderPaymentPolicy.canConfirmCod(delivered, null, total));
        assertFalse(OrderPaymentPolicy.codPaid(cod, total));
        cod.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
        assertTrue(OrderPaymentPolicy.codPaid(cod, total));
        assertFalse(OrderPaymentPolicy.canConfirmCod(delivered, cod, total));
        cod.setTrangThai(TrangThaiThanhToan.CHO_THANH_TOAN);
        cod.setPhuongThuc(PhuongThucTt.CHUYEN_KHOAN);
        assertFalse(OrderPaymentPolicy.canConfirmCod(delivered, cod, total));
    }
    @Test void latestEligibleRefundStateIsDisplayed() {
        var paid = payment("TT1", PhuongThucTt.CHUYEN_KHOAN, TrangThaiThanhToan.DA_THANH_TOAN, 1);
        var refund = payment("TT2", PhuongThucTt.CHUYEN_KHOAN, TrangThaiThanhToan.DA_HOAN_TIEN, 1);
        assertSame(refund, OrderPaymentPolicy.effective(List.of(refund, paid)));
        assertNull(OrderPaymentPolicy.effective(List.of()));
        assertFalse(OrderPaymentPolicy.canEditQuantity(List.of(refund)));
    }
    @Test void autoCompletionNeedsFullRecordedPayment() {
        var total = new java.math.BigDecimal("8490000");
        assertFalse(OrderPaymentPolicy.canAutoComplete(null, total));
        for (var method : PhuongThucTt.values()) {
            var p = payment("TT1", method, TrangThaiThanhToan.CHO_THANH_TOAN, 1);
            p.setSoTien(total);
            assertFalse(OrderPaymentPolicy.canAutoComplete(p, total));
            p.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
            assertFalse(OrderPaymentPolicy.canAutoComplete(p, total));
            p.setNgayThanhToan(LocalDateTime.now());
            assertTrue(OrderPaymentPolicy.canAutoComplete(p, total));
            assertFalse(OrderPaymentPolicy.canAutoComplete(p, total.add(java.math.BigDecimal.ONE)));
            for (var status : TrangThaiThanhToan.values()) {
                p.setTrangThai(status);
                assertEquals(status == TrangThaiThanhToan.DA_THANH_TOAN,
                        OrderPaymentPolicy.canAutoComplete(p, total));
            }
        }
    }
}
