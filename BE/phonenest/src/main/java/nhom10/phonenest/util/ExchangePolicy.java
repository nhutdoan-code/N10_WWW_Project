package nhom10.phonenest.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Pure price and reason rules for exchanges. Prices are merchandise-only VND totals. */
public final class ExchangePolicy {
    private ExchangePolicy() {}
    public static boolean same(String reason) { return Set.of("Máy lỗi", "Giao sai hàng").contains(reason == null ? "" : reason); }
    public static boolean upgrade(String reason) { return Set.of("Đổi ý", "Không đúng mô tả").contains(reason == null ? "" : reason); }
    public static BigDecimal net(BigDecimal unit, int qty, BigDecimal subtotal, BigDecimal discount) {
        var gross = unit.multiply(BigDecimal.valueOf(qty));
        var allocated = subtotal != null && subtotal.signum() > 0 && discount != null
                ? discount.max(BigDecimal.ZERO).multiply(gross).divide(subtotal, 0, RoundingMode.FLOOR) : BigDecimal.ZERO;
        return gross.subtract(allocated).max(BigDecimal.ZERO);
    }
    public static BigDecimal difference(String reason, BigDecimal oldNet, BigDecimal newTotal) {
        if (same(reason)) return BigDecimal.ZERO;
        if (!upgrade(reason) || newTotal.compareTo(oldNet) < 0)
            throw new IllegalArgumentException("Máy đổi phải có giá bằng hoặc cao hơn giá trị thực mua sau ưu đãi");
        return newTotal.subtract(oldNet);
    }
}
