package nhom11.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom11.phonenest.dto.SanPhamDto;
import nhom11.phonenest.entity.HinhAnhSanPham;
import nhom11.phonenest.entity.SanPham;
import nhom11.phonenest.enums.TrangThaiSanPham;
import nhom11.phonenest.exception.AppException;
import nhom11.phonenest.mapper.SanPhamMapper;
import nhom11.phonenest.repository.DonHangRepository;
import nhom11.phonenest.repository.HinhAnhSanPhamRepository;
import nhom11.phonenest.repository.LoaiSanPhamRepository;
import nhom11.phonenest.repository.SanPhamRepository;
import nhom11.phonenest.util.IdGenerator;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import nhom11.phonenest.enums.TrangThaiDonHang;
import nhom11.phonenest.entity.LoaiSanPham;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SanPhamService {

    private static final BigDecimal GIA_TOI_DA = new BigDecimal("999999999999");

    private final SanPhamRepository sanPhamRepository;
    private final LoaiSanPhamRepository loaiSanPhamRepository;
    private final HinhAnhSanPhamRepository hinhAnhRepository;
    private final DonHangRepository donHangRepository;

    @Transactional(readOnly = true)
    public List<SanPhamDto> list(String q, String maLoai, BigDecimal min, BigDecimal max, String sort, boolean admin) {
        Set<String> loais = maLoai == null || maLoai.isBlank() ? null : nhomLoai(maLoai);
        List<SanPhamDto> kq = sanPhamRepository.search(
                        q == null ? "" : q.trim(),
                        "",
                        min == null ? BigDecimal.ZERO : min,
                        max == null ? GIA_TOI_DA : max,
                        sortOf(sort))
                .stream()
                .filter(s -> admin || s.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                .filter(s -> loais == null || loais.contains(s.getLoai().getMaLoai()))
                .map(SanPhamMapper::toDto)
                .toList();
        if ("ban_chay".equals(sort)) {
            Map<String, Integer> daBan = daBan();
            kq = kq.stream()
                    .sorted(Comparator.comparingInt((SanPhamDto s) -> daBan.getOrDefault(s.maSanPham(), 0)).reversed())
                    .toList();
        }
        return kq;
    }

    @Transactional(readOnly = true)
    public List<SanPhamDto> lienQuan(String ma) {
        SanPham goc = find(ma);
        return list(null, goc.getLoai().getMaLoai(), null, null, null, false).stream()
                .filter(s -> !s.maSanPham().equals(ma))
                .limit(4)
                .toList();
    }

    private Map<String, Integer> daBan() {
        Map<String, Integer> kq = new HashMap<>();
        donHangRepository.findAll().stream()
                .filter(d -> d.getTrangThai() != TrangThaiDonHang.DA_HUY)
                .flatMap(d -> d.getChiTiet().stream())
                .forEach(c -> kq.merge(c.getMaSanPham(), c.getSoLuong(), Integer::sum));
        return kq;
    }

    private Set<String> nhomLoai(String ma) {
        List<LoaiSanPham> tatCa = loaiSanPhamRepository.findAll();
        Set<String> ids = new HashSet<>();
        ids.add(ma);
        boolean doi = true;
        while (doi) {
            doi = false;
            for (LoaiSanPham l : tatCa) {
                if (l.getMaLoaiCha() != null && ids.contains(l.getMaLoaiCha()) && ids.add(l.getMaLoai())) {
                    doi = true;
                }
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public SanPhamDto get(String ma, boolean admin) {
        SanPham s = find(ma);
        if (!admin && s.getTrangThai() == TrangThaiSanPham.NGUNG_KINH_DOANH) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        return SanPhamMapper.toDto(s);
    }

    public SanPhamDto save(String ma, SanPhamDto d) {
        SanPham s = ma == null ? new SanPham() : find(ma);
        if (ma == null) {
            s.setMaSanPham(IdGenerator.next("SP"));
            s.setDiemDanhGiaTb(BigDecimal.ZERO);
        }
        s.setLoai(loaiSanPhamRepository.findById(d.maLoai())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Loại sản phẩm không tồn tại")));
        s.setTenMay(d.tenMay());
        s.setGiaBan(d.giaBan());
        s.setMoTa(d.moTa());
        s.setThongSoKyThuat(d.thongSoKyThuat());
        s.setSoLuongTon(d.soLuongTon());
        s.setTrangThai(d.trangThai() == TrangThaiSanPham.NGUNG_KINH_DOANH ? TrangThaiSanPham.NGUNG_KINH_DOANH
                : d.soLuongTon() > 0 ? TrangThaiSanPham.CON_HANG : TrangThaiSanPham.HET_HANG);
        s = sanPhamRepository.save(s);
        luuAnhChinh(s, d.anhChinh());
        return SanPhamMapper.toDto(s);
    }

    public void delete(String ma) {
        if (donHangRepository.daCoTrongDonHang(ma)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Sản phẩm đã có trong đơn hàng, hãy chuyển sang ngừng kinh doanh");
        }
        sanPhamRepository.delete(find(ma));
        sanPhamRepository.flush();
    }

    private SanPham find(String ma) {
        return sanPhamRepository.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    private void luuAnhChinh(SanPham s, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        HinhAnhSanPham anh = s.getHinhAnhs().stream().filter(HinhAnhSanPham::isAnhChinh).findFirst().orElseGet(() -> {
            HinhAnhSanPham moi = new HinhAnhSanPham();
            moi.setMaHinhAnh(IdGenerator.next("HA"));
            moi.setSanPham(s);
            moi.setAnhChinh(true);
            s.getHinhAnhs().add(moi);
            return moi;
        });
        anh.setDuongDan(url.trim());
        hinhAnhRepository.save(anh);
    }

    private Sort sortOf(String sort) {
        return switch (sort == null ? "" : sort) {
            case "gia_asc" -> Sort.by("giaBan");
            case "gia_desc" -> Sort.by("giaBan").descending();
            case "danh_gia" -> Sort.by("diemDanhGiaTb").descending();
            default -> Sort.by("maSanPham").descending();
        };
    }
}
