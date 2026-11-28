package nhom10.phonenest.util;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExchangePolicyTest {
    @Test void priceAfterDiscountAndNoDowngrade() {
        var net = ExchangePolicy.net(new BigDecimal("16790000"), 1, new BigDecimal("16790000"), new BigDecimal("800000"));
        assertEquals(new BigDecimal("15990000"), net);
        assertEquals(new BigDecimal("2000000"), ExchangePolicy.difference("Đổi ý", net, new BigDecimal("17990000")));
        assertThrows(IllegalArgumentException.class, () -> ExchangePolicy.difference("Không đúng mô tả", net, new BigDecimal("15989999")));
        assertEquals(BigDecimal.ZERO, ExchangePolicy.difference("Máy lỗi", net, new BigDecimal("19990000")));
    }
}
