package nhom10.phonenest.util;
import org.junit.jupiter.api.Test;
import nhom10.phonenest.exception.AppException;
import static org.junit.jupiter.api.Assertions.*;
class WarrantyDevicePolicyTest {
    @Test void imeiAndSerial() {
        assertTrue(WarrantyDevicePolicy.validImei("490154203237518"));
        assertFalse(WarrantyDevicePolicy.validImei("490154203237519"));
        assertFalse(WarrantyDevicePolicy.validImei("49015420323751"));
        assertEquals("Serial: AB-1234", WarrantyDevicePolicy.requireDevice("SERIAL", "AB-1234"));
        assertThrows(AppException.class, () -> WarrantyDevicePolicy.requireDevice("IMEI", "490154203237519"));
        assertThrows(AppException.class, () -> WarrantyDevicePolicy.requireDevice("SERIAL", "x"));
    }
    @Test void onlyStoreResolutionsAllowed() {
        for (String option : WarrantyDevicePolicy.OUTCOMES) assertTrue(WarrantyDevicePolicy.validResolution("Kết quả bảo hành: " + option + ". Đã kiểm tra lỗi."));
        assertFalse(WarrantyDevicePolicy.validResolution("Kết quả bảo hành: Hoàn tiền 100%. Ghi chú"));
        assertFalse(WarrantyDevicePolicy.validResolution("Kết quả bảo hành: Sửa chữa miễn phí. "));
    }
}
