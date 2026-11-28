package nhom10.phonenest.dto;

import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.service.LuuTruAnhService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AfterSalesInputTest {
    private AfterSalesInput input(String desc, Map<String,String> details, List<String> images) {
        return new AfterSalesInput("DH1","PB1",1,null,LoaiYeuCauDoiTra.TRA_HANG_HOAN_TIEN,desc,null,images,details);
    }
    private static final String VALID = "Sản phẩm nhận được không đúng mô tả trên website.";
    private static final Map<String,String> RETURN = Map.of("reason","Máy lỗi","condition","Có lỗi","fault","Không lên nguồn","refund","Phương thức thanh toán gốc");
    @Test void descriptionBoundariesAndWhitespace() {
        assertThrows(AppException.class, () -> input(" ".repeat(40),RETURN,List.of()).description("TRA_HANG_HOAN_TIEN"));
        assertThrows(AppException.class, () -> input("\u00a0".repeat(40),RETURN,List.of()).description("TRA_HANG_HOAN_TIEN"));
        assertDoesNotThrow(() -> input("a",RETURN,List.of()).description("TRA_HANG_HOAN_TIEN"));
        assertDoesNotThrow(() -> input("a".repeat(20),RETURN,List.of()).description("TRA_HANG_HOAN_TIEN"));
        assertDoesNotThrow(() -> input("a".repeat(1000),RETURN,List.of()).description("TRA_HANG_HOAN_TIEN"));
        assertThrows(AppException.class, () -> input("a".repeat(1001),RETURN,List.of()).description("TRA_HANG_HOAN_TIEN"));
    }
    @Test void evidenceRulesAndOwnership() {
        var storage = mock(LuuTruAnhService.class);
        assertThrows(AppException.class, () -> input(VALID,RETURN,List.of()).images("TRA_HANG_HOAN_TIEN","KH1",storage));
        assertThrows(AppException.class, () -> input(VALID,null,List.of()).images("BAO_HANH","KH1",storage));
        assertNull(input(VALID,Map.of("reason","Đổi ý"),List.of()).images("DOI_HANG","KH1",storage));
        assertNull(input(VALID,Map.of("topic","Khác"),List.of()).images("KHIEU_NAI","KH1",storage));
        assertThrows(AppException.class, () -> input(VALID,RETURN,List.of("fake")).images("TRA_HANG_HOAN_TIEN","KH1",storage));
        when(storage.anhHauMaiHopLe("a","KH1")).thenReturn(true);
        when(storage.anhHauMaiHopLe("b","KH1")).thenReturn(true);
        assertEquals("a\nb", input(VALID,RETURN,List.of("a","b")).images("TRA_HANG_HOAN_TIEN","KH1",storage));
        assertThrows(AppException.class, () -> input(VALID,RETURN,List.of("a","a")).images("TRA_HANG_HOAN_TIEN","KH1",storage));
        assertThrows(AppException.class, () -> input(VALID,RETURN,List.of("a","b","c","d","e","f")).images("TRA_HANG_HOAN_TIEN","KH1",storage));
    }
    @Test void warrantyDoesNotRequireCustomerOutcome() {
        var details = Map.of("appearance", "Bình thường", "fault", "Camera / âm thanh");
        assertTrue(input(VALID,details,List.of()).description("BAO_HANH").contains("Ngoại hình: Bình thường"));
        var legacy = new HashMap<>(details); legacy.put("serial", "invalid legacy value");
        assertEquals(input(VALID,details,List.of()).description("BAO_HANH"), input(VALID,legacy,List.of()).description("BAO_HANH"));
        var old = new HashMap<>(details); old.put("outcome", "Đề nghị hoàn tiền 100%");
        assertFalse(input(VALID,old,List.of()).description("BAO_HANH").contains("Hoàn tiền"));
        assertThrows(AppException.class, () -> input(VALID,null,List.of()).description("BAO_HANH"));
    }
    @Test void requireFieldsForDynamicForm() {
        assertThrows(AppException.class, () -> input(VALID,Map.of(),List.of()).description("KHIEU_NAI"));
        assertThrows(AppException.class, () -> input(VALID,Map.of("reason","Khác"),List.of()).description("DOI_HANG"));
        assertThrows(AppException.class, () -> input(VALID,Map.of("serial","123","appearance","Bình thường"),List.of()).description("BAO_HANH"));
        assertThrows(AppException.class, () -> input(VALID,Map.of("reason","Máy lỗi","condition","Có lỗi","refund","Chuyển khoản"),List.of()).description("TRA_HANG_HOAN_TIEN"));
        assertTrue(input(VALID,RETURN,List.of()).description("TRA_HANG_HOAN_TIEN").contains("Lý do: Máy lỗi"));
    }
    @Test void rejectContradictoryConditionsAndRequireFault() {
        var invalid = new HashMap<>(RETURN); invalid.put("condition", "Còn nguyên seal");
        assertTrue(input(VALID,invalid,List.of()).description("TRA_HANG_HOAN_TIEN").contains("Tình trạng máy: Có lỗi"));
        invalid.remove("condition");
        assertTrue(input(VALID,invalid,List.of()).description("TRA_HANG_HOAN_TIEN").contains("Tình trạng máy: Có lỗi"));
        invalid.put("reason", "Đổi ý"); invalid.put("condition", "Có lỗi");
        assertThrows(AppException.class, () -> input(VALID,invalid,List.of()).description("TRA_HANG_HOAN_TIEN"));
        var missing = new HashMap<>(RETURN); missing.remove("fault");
        assertThrows(AppException.class, () -> input(VALID,missing,List.of()).description("TRA_HANG_HOAN_TIEN"));
        assertTrue(input(VALID,RETURN,List.of()).description("TRA_HANG_HOAN_TIEN").contains("Loại lỗi: Không lên nguồn"));
    }
    @Test void v5RejectsWrongDeliveryAndMismatchWithBrokenCondition() {
        for (String reason : List.of("Giao sai hàng", "Không đúng mô tả")) {
            var details = new HashMap<>(RETURN); details.put("reason", reason);
            assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN"));
            details.put("condition", "Đã kích hoạt"); details.remove("fault");
            if (reason.equals("Không đúng mô tả")) {
                assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN"));
                details.put("mismatch", "Sai dung lượng");
            }
            assertFalse(input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN").contains("Loại lỗi:"));
        }
    }
    @Test void v5BankAndCustomerMask() {
        var details = new HashMap<>(RETURN);
        details.putAll(Map.of("refund", "Chuyển khoản", "bank", "MB", "account", "00123456", "holder", "Nguyễn Văn A"));
        String description = input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN");
        assertTrue(description.contains("Chủ tài khoản: NGUYEN VAN A"));
        assertTrue(description.contains("00123456"));
        assertFalse(DoiTraDto.maskAccount(description).contains("00123456"));
        assertTrue(DoiTraDto.maskAccount(description).contains("•••• 3456"));
        details.put("account", "12345");
        assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN"));
        details.put("account", "00123456"); details.put("bank", "FAKE");
        assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN"));
        details.put("bank", "MB"); details.put("holder", "A\n123");
        assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN"));
    }
    @Test void v6MultipleFaultsAndLegacySingleFault() {
        var details = new HashMap<>(RETURN);
        details.put("fault", "Màn hình / cảm ứng; Pin / sạc");
        assertTrue(input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN").contains("Loại lỗi: Màn hình / cảm ứng; Pin / sạc"));
        for (String fault : List.of("", "Pin / sạc; Pin / sạc", "Pin / sạc; Sai", "Pin / sạc; ")) {
            details.put("fault", fault);
            assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("TRA_HANG_HOAN_TIEN"));
        }
        assertDoesNotThrow(() -> input(VALID,RETURN,List.of()).description("TRA_HANG_HOAN_TIEN"));
    }
    @Test void changeOfMindRequiresSealCommitmentAndNonblankDescription() {
        var details = new HashMap<>(Map.of("reason", "Đổi ý", "condition", "Còn nguyên seal", "refund", "Phương thức thanh toán gốc", "target", "Máy B"));
        for (String type : List.of("TRA_HANG_HOAN_TIEN", "DOI_HANG")) {
            details.remove("sealConfirmed");
            assertThrows(AppException.class, () -> input("a".repeat(10),details,List.of()).description(type));
            details.put("sealConfirmed", "false");
            assertThrows(AppException.class, () -> input("a".repeat(10),details,List.of()).description(type));
            details.put("sealConfirmed", "true");
            assertTrue(input("a".repeat(10),details,List.of()).description(type).contains("Cam kết khách hàng:"));
            assertDoesNotThrow(() -> input("a",details,List.of()).description(type));
            assertThrows(AppException.class, () -> input(" ".repeat(10),details,List.of()).description(type));
            assertThrows(AppException.class, () -> input("a".repeat(1001),details,List.of()).description(type));
        }
    }

    @Test void warrantyFaultClassificationAndPhysicalDamage() {
        var details = new HashMap<>(Map.of("appearance", "Rơi vỡ", "fault", "Pin / sạc; Camera / âm thanh"));
        String description = input(VALID,details,List.of()).description("BAO_HANH");
        assertTrue(description.contains("Loại lỗi: Pin / sạc; Camera / âm thanh"));
        assertTrue(description.contains("Ngoại hình: Rơi vỡ"));
        details.put("appearance", "Vào nước");
        assertDoesNotThrow(() -> input(VALID,details,List.of()).description("BAO_HANH"));
        for (String fault : List.of("", "Sai", "Pin / sạc; Pin / sạc", "Pin / sạc; ")) {
            details.put("fault", fault);
            assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("BAO_HANH"));
        }
        details.remove("fault");
        assertThrows(AppException.class, () -> input(VALID,details,List.of()).description("BAO_HANH"));
    }
}
