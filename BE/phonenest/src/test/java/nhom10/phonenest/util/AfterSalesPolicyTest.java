package nhom10.phonenest.util;

import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static nhom10.phonenest.enums.TrangThaiDonHang.*;

class AfterSalesPolicyTest {
    private final LocalDateTime delivered = LocalDateTime.of(2026, 11, 1, 12, 0);
    @Test void returnBoundary() {
        assertTrue(AfterSalesPolicy.eligible(DA_GIAO, delivered, delivered.plusDays(1), false, delivered.plusDays(8)));
        assertFalse(AfterSalesPolicy.eligible(DA_GIAO, delivered, delivered.plusDays(1), false, delivered.plusDays(8).plusSeconds(1)));
        assertFalse(AfterSalesPolicy.eligible(DA_GIAO, delivered, delivered.plusDays(1), false, delivered.plusDays(9)));
        assertTrue(AfterSalesPolicy.eligible(DA_GIAO, delivered, null, false, delivered.plusDays(7)));
        assertFalse(AfterSalesPolicy.eligible(DA_GIAO, delivered, null, false, delivered.plusDays(7).plusSeconds(1)));
    }
    @Test void pendingAndFutureBlocked() {
        for (boolean warranty : new boolean[]{true,false}) {
            assertFalse(AfterSalesPolicy.eligible(CHO_KHACH_XAC_NHAN, delivered, null, warranty, delivered.plusDays(1)));
            assertFalse(AfterSalesPolicy.eligible(DA_GIAO, delivered, null, warranty, delivered.minusDays(1)));
            assertFalse(AfterSalesPolicy.eligible(DA_GIAO, null, null, warranty, delivered));
        }
        DonHang d = new DonHang(); d.setTrangThai(CHO_KHACH_XAC_NHAN); d.setNgayGiao(delivered);
        assertThrows(AppException.class, () -> AfterSalesPolicy.require(d,false));
        assertThrows(AppException.class, () -> AfterSalesPolicy.require(d,true));
    }
    @Test void warrantyConfirmationAndLegacy() {
        LocalDateTime confirmed = delivered.plusDays(3);
        assertTrue(AfterSalesPolicy.eligible(DA_GIAO, delivered, confirmed, true, confirmed.plusMonths(12)));
        assertFalse(AfterSalesPolicy.eligible(DA_GIAO, delivered, confirmed, true, confirmed.plusMonths(12).plusSeconds(1)));
        assertTrue(AfterSalesPolicy.eligible(DA_GIAO, delivered, null, true, delivered.plusMonths(12)));
        assertFalse(AfterSalesPolicy.eligible(DA_GIAO, delivered, null, true, delivered.plusMonths(12).plusDays(1)));
    }
    @Test void leapYearAndFutureConfirmation() {
        LocalDateTime leap = LocalDateTime.of(2024,2,29,12,0);
        assertEquals(LocalDateTime.of(2025,2,28,12,0), AfterSalesPolicy.end(leap,null,true));
        assertFalse(AfterSalesPolicy.eligible(DA_GIAO, delivered, delivered.plusDays(3), true, delivered.plusDays(1)));
    }
}
