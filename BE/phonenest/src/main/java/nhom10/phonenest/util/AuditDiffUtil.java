package nhom10.phonenest.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * So sanh du lieu truoc/sau cho NhatKyHoatDong.
 *
 * <p>Chi tra ve cac field thuc su thay doi, loai bo du lieu nhay cam theo ten key
 * (khong phan biet hoa/thuong), serialize bang Jackson va gioi han payload de
 * khong tao ra JSON qua lon hoac JSON hong.
 *
 * <p>Khong phu thuoc Controller, khong query DB, khong save repository.
 */
@Component
@RequiredArgsConstructor
public class AuditDiffUtil {

    public static final int GIOI_HAN_PAYLOAD = 20_000;

    /**
     * Key da chuan hoa: lowercase, bo dau '_' va '-'.
     * Vi du: matKhau -> matkhau, api_key -> apikey, accessToken -> accesstoken.
     */
    private static final Set<String> KHOA_NHAY_CAM = Set.of(
            "password",
            "matkhau",
            "token",
            "accesstoken",
            "refreshtoken",
            "authorization",
            "jwt",
            "secret",
            "apikey");

    private final ObjectMapper objectMapper;

    /**
     * Tinh diff cua mot lan ghi audit.
     *
     * <ul>
     *     <li>before rong, after co du lieu: CREATE (duLieuTruoc = null).</li>
     *     <li>before co du lieu, after rong: DELETE (duLieuSau = null).</li>
     *     <li>ca hai co du lieu: UPDATE, chi giu field thay doi.</li>
     *     <li>ca hai rong hoac khong co field thay doi: no-op.</li>
     * </ul>
     */
    public KetQuaDiff tinhDiff(Map<String, ?> duLieuTruoc, Map<String, ?> duLieuSau) {
        Map<String, Object> truoc = sanitizeMap(duLieuTruoc);
        Map<String, Object> sau = sanitizeMap(duLieuSau);

        if (truoc.isEmpty() && sau.isEmpty()) {
            return KetQuaDiff.KHONG_THAY_DOI;
        }
        if (truoc.isEmpty()) {
            return new KetQuaDiff(null, serialize(sau), true);
        }
        if (sau.isEmpty()) {
            return new KetQuaDiff(serialize(truoc), null, true);
        }

        Map<String, Object> truocThayDoi = new LinkedHashMap<>();
        Map<String, Object> sauThayDoi = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : truoc.entrySet()) {
            String key = entry.getKey();
            if (!sau.containsKey(key)) {
                truocThayDoi.put(key, entry.getValue());
            } else if (!bangNhau(entry.getValue(), sau.get(key))) {
                truocThayDoi.put(key, entry.getValue());
                sauThayDoi.put(key, sau.get(key));
            }
        }
        for (Map.Entry<String, Object> entry : sau.entrySet()) {
            if (!truoc.containsKey(entry.getKey())) {
                sauThayDoi.put(entry.getKey(), entry.getValue());
            }
        }

        if (truocThayDoi.isEmpty() && sauThayDoi.isEmpty()) {
            return KetQuaDiff.KHONG_THAY_DOI;
        }
        return new KetQuaDiff(
                truocThayDoi.isEmpty() ? null : serialize(truocThayDoi),
                sauThayDoi.isEmpty() ? null : serialize(sauThayDoi),
                true);
    }

    private Map<String, Object> sanitizeMap(Map<String, ?> source) {
        Map<String, Object> ketQua = new LinkedHashMap<>();
        if (source == null) {
            return ketQua;
        }
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || laKhoaNhayCam(key)) {
                continue;
            }
            ketQua.put(key, sanitizeValue(entry.getValue()));
        }
        return ketQua;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ketQua = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || laKhoaNhayCam(key)) {
                    continue;
                }
                ketQua.put(key, sanitizeValue(entry.getValue()));
            }
            return ketQua;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> ketQua = new ArrayList<>(collection.size());
            for (Object item : collection) {
                ketQua.add(sanitizeValue(item));
            }
            return ketQua;
        }
        if (value instanceof Object[] array) {
            List<Object> ketQua = new ArrayList<>(array.length);
            for (Object item : array) {
                ketQua.add(sanitizeValue(item));
            }
            return ketQua;
        }
        return value;
    }

    private boolean laKhoaNhayCam(String key) {
        String chuanHoa = key.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
        return KHOA_NHAY_CAM.contains(chuanHoa);
    }

    /**
     * So sanh co xu ly BigDecimal theo gia tri (compareTo), khong theo scale.
     * 19990000.00 va 19990000 duoc coi la giong nhau.
     */
    private boolean bangNhau(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Number soA && b instanceof Number soB) {
            try {
                return new BigDecimal(soA.toString()).compareTo(new BigDecimal(soB.toString())) == 0;
            } catch (NumberFormatException ignored) {
                return Objects.equals(a, b);
            }
        }
        if (a instanceof Map<?, ?> mapA && b instanceof Map<?, ?> mapB) {
            if (!mapA.keySet().equals(mapB.keySet())) {
                return false;
            }
            for (Map.Entry<?, ?> entry : mapA.entrySet()) {
                if (!bangNhau(entry.getValue(), mapB.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Collection<?> dsA && b instanceof Collection<?> dsB) {
            if (dsA.size() != dsB.size()) {
                return false;
            }
            var itA = dsA.iterator();
            var itB = dsB.iterator();
            while (itA.hasNext() && itB.hasNext()) {
                if (!bangNhau(itA.next(), itB.next())) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Object[] arrA && b instanceof Object[] arrB) {
            if (arrA.length != arrB.length) {
                return false;
            }
            for (int i = 0; i < arrA.length; i++) {
                if (!bangNhau(arrA[i], arrB[i])) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    private String serialize(Map<String, Object> duLieu) {
        try {
            String json = objectMapper.writeValueAsString(duLieu);
            if (json.length() <= GIOI_HAN_PAYLOAD) {
                return json;
            }
            return serializeBiCat(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể serialize dữ liệu audit", e);
        }
    }

    /**
     * Payload vuot 20.000 ky tu duoc thay bang mot wrapper JSON hop le.
     * Preview duoc cat theo ranh gioi ky tu an toan va luon de Jackson escape.
     */
    private String serializeBiCat(String json) throws JsonProcessingException {
        int low = 0;
        int high = json.length();
        String totNhat = null;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String ungVien = objectMapper.writeValueAsString(wrapper(json, catAnToan(json, mid)));
            if (ungVien.length() <= GIOI_HAN_PAYLOAD) {
                totNhat = ungVien;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return totNhat != null ? totNhat : objectMapper.writeValueAsString(wrapper(json, 0));
    }

    private Map<String, Object> wrapper(String json, int doDaiPreview) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("_truncated", true);
        wrapper.put("_originalLength", json.length());
        wrapper.put("_preview", json.substring(0, doDaiPreview));
        return wrapper;
    }

    private int catAnToan(String value, int viTriCat) {
        if (viTriCat <= 0) {
            return 0;
        }
        if (viTriCat >= value.length()) {
            return value.length();
        }
        // Khong cat doi mot cap surrogate.
        if (Character.isHighSurrogate(value.charAt(viTriCat - 1))) {
            return viTriCat - 1;
        }
        return viTriCat;
    }

    public record KetQuaDiff(String duLieuTruoc, String duLieuSau, boolean coThayDoi) {

        public static final KetQuaDiff KHONG_THAY_DOI = new KetQuaDiff(null, null, false);
    }
}
