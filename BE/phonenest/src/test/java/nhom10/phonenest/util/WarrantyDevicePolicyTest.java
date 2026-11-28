package nhom10.phonenest.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WarrantyDevicePolicyTest {
    @Test void onlyStoreResolutionsAllowed() {
        for (String option : WarrantyDevicePolicy.OUTCOMES) assertTrue(WarrantyDevicePolicy.validResolution("Kết quả bảo hành: " + option + ". Đã kiểm tra lỗi."));
        assertFalse(WarrantyDevicePolicy.validResolution("Kết quả bảo hành: Hoàn tiền 100%. Ghi chú"));
        assertFalse(WarrantyDevicePolicy.validResolution("Kết quả bảo hành: Sửa chữa miễn phí. "));
    }
}
