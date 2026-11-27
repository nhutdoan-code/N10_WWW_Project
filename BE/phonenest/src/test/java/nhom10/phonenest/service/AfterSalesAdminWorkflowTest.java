package nhom10.phonenest.service;

import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AfterSalesAdminWorkflowTest {
    @Mock YeuCauDoiTraRepository returns;
    @Mock YeuCauHauMaiRepository after;
    @Mock NhatKyHoatDongRepository logs;
    @Mock NhatKyHoatDongService audit;
    @Mock DoiTraService returnService;
    @Mock HauMaiService afterService;
    @Mock ThongBaoService notifications;
    @Mock TaiKhoanRepository accounts;
    @Mock ThanhToanRepository payments;
    @InjectMocks AfterSalesAdminWorkflow workflow;

    @Test void rejectRequiresSeparateReasonAndNote() {
        when(returns.findByIdForUpdate("YC1")).thenReturn(Optional.of(new YeuCauDoiTra()));
        assertThrows(AppException.class, () -> workflow.execute("return", "YC1", new AfterSalesAdminWorkflow.Action("STATUS", "", "Khác", null, "TU_CHOI")));
        assertThrows(AppException.class, () -> workflow.execute("return", "YC1", new AfterSalesAdminWorkflow.Action("STATUS", "Không hợp lệ", "", null, "TU_CHOI")));
        verifyNoInteractions(returnService);
    }
    @Test void restockRequiresReceiptInspection() {
        when(returns.findByIdForUpdate("YC1")).thenReturn(Optional.of(new YeuCauDoiTra()));
        assertThrows(AppException.class, () -> workflow.restock("YC1", "AD1"));
        verifyNoInteractions(returnService);
    }
    @Test void warrantyCannotCloseBeforeHandover() {
        var y = new YeuCauHauMai(); y.setLoai(LoaiHauMai.BAO_HANH); y.setTrangThai(TrangThaiHauMai.DANG_XU_LY);
        when(after.findByIdForUpdate("HM1")).thenReturn(Optional.of(y));
        assertThrows(AppException.class, () -> workflow.checkCompletion("after", "HM1"));
        verifyNoInteractions(afterService);
    }
    @Test void exchangeCannotCloseBeforeDelivery() {
        var y = new YeuCauDoiTra(); var item = new ChiTietDoiTra(); item.setLoaiYeuCau(LoaiYeuCauDoiTra.DOI_HANG); y.getChiTiet().add(item);
        when(returns.findByIdForUpdate("YC1")).thenReturn(Optional.of(y));
        assertThrows(AppException.class, () -> workflow.checkCompletion("return", "YC1"));
        verifyNoInteractions(returnService);
    }
}
