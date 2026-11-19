package nhom10.phonenest.service;

import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import nhom10.phonenest.repository.HanhViNguoiDungRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tổng hợp từ khóa được tìm nhiều và sản phẩm được xem nhiều từ dữ liệu hành vi (HanhViNguoiDung)
 * cho menu "Dòng sản phẩm được tìm nhiều" ở trang bán hàng. Chỉ trả về số liệu tổng hợp,
 * không có thông tin cá nhân của khách.
 */
@Service
public class TimKiemPhoBienService {

    private static final int SO_NGAY = 30;
    private static final int GIOI_HAN_TOI_DA = 20;

    private final HanhViNguoiDungRepository hanhViRepository;

    public TimKiemPhoBienService(HanhViNguoiDungRepository hanhViRepository) {
        this.hanhViRepository = hanhViRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tongHop(Integer limit) {
        int n = limit == null ? 10 : Math.max(1, Math.min(limit, GIOI_HAN_TOI_DA));
        LocalDateTime tuNgay = LocalDateTime.now().minusDays(SO_NGAY);

        List<Map<String, Object>> tuKhoa = new ArrayList<>();
        for (Object[] row : hanhViRepository.findTopKeywords(tuNgay, null, PageRequest.of(0, n))) {
            if (row[0] == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tuKhoa", row[0].toString());
            m.put("luot", ((Number) row[1]).longValue());
            tuKhoa.add(m);
        }

        // Gộp lượt xem và lượt bấm vào sản phẩm.
        Map<String, Long> luotXem = new LinkedHashMap<>();
        for (LoaiHanhViNguoiDung loai : List.of(LoaiHanhViNguoiDung.PRODUCT_VIEW, LoaiHanhViNguoiDung.PRODUCT_CLICK)) {
            for (Object[] row : hanhViRepository.findTopProductsByLoai(loai, tuNgay, null, PageRequest.of(0, n))) {
                if (row[0] == null) continue;
                luotXem.merge(row[0].toString(), ((Number) row[1]).longValue(), Long::sum);
            }
        }
        List<Map<String, Object>> sanPham = new ArrayList<>();
        luotXem.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(n)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("maSanPham", e.getKey());
                    m.put("luot", e.getValue());
                    sanPham.add(m);
                });

        Map<String, Object> ketQua = new LinkedHashMap<>();
        ketQua.put("soNgay", SO_NGAY);
        ketQua.put("tuKhoa", tuKhoa);
        ketQua.put("sanPhamXemNhieu", sanPham);
        return ketQua;
    }
}
