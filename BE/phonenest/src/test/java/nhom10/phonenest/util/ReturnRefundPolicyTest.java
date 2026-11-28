package nhom10.phonenest.util;
import nhom10.phonenest.enums.PhuongThucTt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReturnRefundPolicyTest {
    @Test void onlyOriginalForVnPayAndBankForCod() {
        assertTrue(ReturnRefundPolicy.customerAllowed(PhuongThucTt.CHUYEN_KHOAN, "Phương thức thanh toán gốc"));
        assertFalse(ReturnRefundPolicy.customerAllowed(PhuongThucTt.CHUYEN_KHOAN, "Chuyển khoản"));
        assertTrue(ReturnRefundPolicy.customerAllowed(PhuongThucTt.COD, "Chuyển khoản"));
        assertFalse(ReturnRefundPolicy.customerAllowed(PhuongThucTt.COD, "Phương thức thanh toán gốc"));
        assertFalse(ReturnRefundPolicy.customerAllowed(null, "Chuyển khoản"));
        assertEquals("VNPAY", ReturnRefundPolicy.adminMethod(PhuongThucTt.CHUYEN_KHOAN, "Hoàn tiền đề nghị: Chuyển khoản"));
        assertEquals("CHUYEN_KHOAN", ReturnRefundPolicy.adminMethod(PhuongThucTt.COD, null));
    }
    @Test void momoUsesStructuredPreferenceNotFreeText() {
        assertEquals("MOMO", ReturnRefundPolicy.adminMethod(PhuongThucTt.VI_DIEN_TU, "Hoàn tiền đề nghị: Phương thức thanh toán gốc\nMô tả chi tiết: abc\nHoàn tiền đề nghị: Chuyển khoản"));
        assertEquals("CHUYEN_KHOAN", ReturnRefundPolicy.adminMethod(PhuongThucTt.VI_DIEN_TU, "Hoàn tiền đề nghị: Chuyển khoản\nMô tả chi tiết: abc"));
    }
}
