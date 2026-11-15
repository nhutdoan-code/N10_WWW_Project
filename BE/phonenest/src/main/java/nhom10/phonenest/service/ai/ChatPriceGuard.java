package nhom10.phonenest.service.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2B.7E — Kiem soat GIA trong chatbot, doc lap voi mo hinh AI (khong tin rang Gemini tuan thu prompt).
 *
 * <ul>
 *   <li>{@link #anSoTien}: che moi so tien trong lich su hoi thoai do CLIENT gui len (client co the gia mao
 *       luot "assistant" de mo hinh "xac nhan" mot gia chua duyet).</li>
 *   <li>{@link #hopLe}: moi so tien trong cau tra loi cua AI phai thuoc tap gia/so tien DUOC CONG BO
 *       (gia ban/niem yet cua bien the duoc phep ban, muc voucher, phi ship). So tien chi xuat hien trong cau hoi
 *       cua khach (vd. ngan sach) duoc nhac lai, TRU KHI no trung gia noi bo chua duyet.</li>
 * </ul>
 */
public final class ChatPriceGuard {

    /** 3.290.000 | 3,290,000 | 3290000 [đ/₫/vnd] | 3,29 triệu | 3.3tr */
    private static final Pattern SO_TIEN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(triệu|trieu|tr)(?![\\p{L}])"
                    + "|(\\d{1,3}(?:[.,\\u00A0 ]\\d{3})+|\\d{5,})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static final long NGUONG = 10_000L;

    private ChatPriceGuard() {
    }

    /** Cac so tien (VND, >= 10.000) xuat hien trong van ban. */
    public static Set<Long> soTien(String text) {
        Set<Long> kq = new LinkedHashSet<>();
        if (text == null) return kq;
        Matcher m = SO_TIEN.matcher(text);
        while (m.find()) {
            Long v = giaTri(m);
            if (v != null && v >= NGUONG) kq.add(v);
        }
        return kq;
    }

    /** Che so tien trong lich su do client gui (giu nguyen phan con lai cua noi dung). */
    public static String anSoTien(String text) {
        if (text == null) return null;
        Matcher m = SO_TIEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Long v = giaTri(m);
            m.appendReplacement(sb, v != null && v >= NGUONG ? "[số tiền đã ẩn]" : Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * @param traLoi       cau tra loi cua AI
     * @param duocCongBo   so tien duoc phep neu (gia cong bo, voucher, phi ship)
     * @param cauHoi       cau hoi hien tai cua khach (cho phep nhac lai ngan sach)
     * @param noiBoChuaDuyet gia noi bo cua bien the KHONG duoc ban (khong bao gio duoc neu)
     */
    public static boolean hopLe(String traLoi, Set<Long> duocCongBo, String cauHoi, Set<Long> noiBoChuaDuyet) {
        Set<Long> trongCauHoi = soTien(cauHoi);
        for (Long v : soTien(traLoi)) {
            if (duocCongBo.contains(v)) continue;
            if (trongCauHoi.contains(v) && !noiBoChuaDuyet.contains(v)) continue;
            return false;
        }
        return true;
    }

    public static Long vnd(BigDecimal v) {
        return v == null ? null : v.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static Long giaTri(Matcher m) {
        try {
            if (m.group(1) != null) {
                BigDecimal so = new BigDecimal(m.group(1).replace(',', '.'));
                return so.multiply(BigDecimal.valueOf(1_000_000)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            }
            String s = m.group(3).replaceAll("[.,\\u00A0 ]", "");
            return Long.parseLong(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String chuan(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
