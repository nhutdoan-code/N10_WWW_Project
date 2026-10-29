
package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.HinhAnhSanPham;
import nhom10.phonenest.entity.LoaiSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.ChiTietDonHangRepository;
import nhom10.phonenest.repository.ChiTietGioHangRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.HinhAnhSanPhamRepository;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.util.IdGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class SanPhamService {

    private final SanPhamRepository spRepo;
    private final LoaiSanPhamRepository loaiRepo;
    private final HinhAnhSanPhamRepository anhRepo;
    private final DonHangRepository donRepo;
    private final ChiTietDonHangRepository chiTietDonHangRepo;
    private final ChiTietGioHangRepository chiTietGioHangRepo;

    // ==================== DANH SÁCH ====================

    public List<SanPhamDto> list(
            String q,
            String maLoai,
            BigDecimal min,
            BigDecimal max,
            String sort,
            boolean admin
    ) {
        String keyword = q == null
                ? ""
                : q.trim().toLowerCase();

        Set<String> loais = maLoai == null || maLoai.isBlank()
                ? null
                : nhomLoai(maLoai);

        List<SanPhamDto> result = new ArrayList<>();

        for (SanPham s : spRepo.findAll()) {

            if (!admin
                    && s.getTrangThai()
                    != TrangThaiSanPham.CON_HANG) {
                continue;
            }

            boolean matchKeyword =
                    keyword.isEmpty()
                            || (s.getTenMay() != null
                            && s.getTenMay().toLowerCase()
                            .contains(keyword))
                            || (s.getMaSanPham() != null
                            && s.getMaSanPham().toLowerCase()
                            .contains(keyword));

            if (!matchKeyword) {
                continue;
            }

            if (loais != null
                    && (s.getLoai() == null
                    || !loais.contains(s.getLoai().getMaLoai()))) {
                continue;
            }

            SanPhamDto dto = SanPhamMapper.toDto(s);

            if (min != null
                    && dto.giaBan().compareTo(min) < 0) {
                continue;
            }

            if (max != null
                    && dto.giaBan().compareTo(max) > 0) {
                continue;
            }

            result.add(dto);
        }

        Comparator<SanPhamDto> comparator;

        if ("gia_asc".equals(sort)) {
            comparator = Comparator.comparing(
                    SanPhamDto::giaBan,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else if ("gia_desc".equals(sort)) {
            comparator = Comparator.comparing(
                    SanPhamDto::giaBan,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
        } else if ("danh_gia".equals(sort)) {
            comparator = Comparator.comparing(
                    SanPhamDto::diemDanhGiaTb,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
        } else {
            comparator = Comparator.comparing(
                    SanPhamDto::maSanPham,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
        }

        return result.stream().sorted(comparator).toList();
    }

    // ==================== CHI TIẾT ====================

    public SanPhamDto get(String ma, boolean admin) {

        SanPham s = find(ma);

        if (!admin
                && s.getTrangThai()
                == TrangThaiSanPham.NGUNG_KINH_DOANH) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy sản phẩm"
            );
        }

        return SanPhamMapper.toDto(s);
    }

    // ==================== SẢN PHẨM LIÊN QUAN ====================

    public List<SanPhamDto> lienQuan(String ma) {

        SanPham s = find(ma);

        return list(
                null,
                s.getLoai().getMaLoai(),
                null,
                null,
                null,
                false
        ).stream()
                .filter(x -> !x.maSanPham().equals(ma))
                .limit(4)
                .toList();
    }

    // ==================== THÊM / CẬP NHẬT ====================

    public SanPhamDto save(String ma, SanPhamDto d) {

        SanPham s = ma == null
                ? new SanPham()
                : find(ma);

        if (ma == null) {
            s.setMaSanPham(IdGenerator.next("SP"));
            s.setDiemDanhGiaTb(BigDecimal.ZERO);
            s.setSoLuongTon(0);
            s.setGiaBan(BigDecimal.ZERO);
        }

        LoaiSanPham loai = loaiRepo.findById(d.maLoai())
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Loại sản phẩm không tồn tại"
                ));

        s.setLoai(loai);
        s.setTenMay(d.tenMay());
        s.setMoTa(d.moTa());

        if (d.giaBan() != null) {
            if (d.giaBan().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Giá bán không được âm"
                );
            }
            s.setGiaBan(d.giaBan());
        }

        if (d.soLuongTon() < 0) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng tồn không được âm"
            );
        }
        s.setSoLuongTon(d.soLuongTon());

        s.setTrangThai(d.trangThai() == null
                ? TrangThaiSanPham.CON_HANG
                : d.trangThai());

        if (d.thongSo() != null) {
            var t = d.thongSo();

            s.setKichThuocManHinh(t.kichThuocManHinh());
            s.setLoaiManHinh(t.loaiManHinh());
            s.setTanSoQuet(t.tanSoQuet());
            s.setChip(t.chip());
            s.setRam(t.ram());
            s.setBoNho(t.boNho());
            s.setCameraSau(t.cameraSau());
            s.setCameraTruoc(t.cameraTruoc());
            s.setPin(t.pin());
            s.setCongSuatSac(t.congSuatSac());
            s.setHeDieuHanh(t.heDieuHanh());
            s.setNfc(t.nfc());
            s.setHo5G(t.ho5G());
            s.setTrongLuong(t.trongLuong());
        }

        s = spRepo.save(s);

        luuAnh(s, d.hinhAnhs(), d.anhChinh());

        return SanPhamMapper.toDto(s);
    }

    // ==================== XÓA ====================

    public void delete(String ma) {

        SanPham s = find(ma);

        // Không xóa sản phẩm đã xuất hiện trong đơn hàng.
        if (donRepo.daCoTrongDonHang(ma)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm đã có trong đơn hàng, hãy chuyển sang ngừng kinh doanh"
            );
        }

        // Không xóa nếu vẫn còn trong giỏ hàng.
        if (chiTietGioHangRepo.existsBySanPhamMaSanPham(ma)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm đang nằm trong giỏ hàng"
            );
        }

        anhRepo.deleteAll(s.getHinhAnhs());
        s.getHinhAnhs().clear();

        spRepo.delete(s);
    }

    // ==================== TÌM SẢN PHẨM ====================

    private SanPham find(String ma) {
        return spRepo.findById(ma)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));
    }

    // ==================== NHÓM LOẠI SẢN PHẨM ====================

    private Set<String> nhomLoai(String ma) {

        List<LoaiSanPham> all = loaiRepo.findAll();
        Set<String> ids = new HashSet<>();

        ids.add(ma);

        boolean changed = true;

        while (changed) {
            changed = false;

            for (LoaiSanPham loai : all) {
                if (loai.getMaLoaiCha() != null
                        && ids.contains(loai.getMaLoaiCha())
                        && ids.add(loai.getMaLoai())) {
                    changed = true;
                }
            }
        }

        return ids;
    }

    // ==================== QUẢN LÝ HÌNH ẢNH ====================

    private void luuAnh(
            SanPham s,
            List<SanPhamDto.Anh> anhs,
            String anhChinh
    ) {
        if (anhs == null) {
            return;
        }

        List<HinhAnhSanPham> hienCo =
                new ArrayList<>(s.getHinhAnhs());

        Set<String> daDung = new HashSet<>();
        List<HinhAnhSanPham> ketQua = new ArrayList<>();

        for (SanPhamDto.Anh dto : anhs) {

            String url = dto.duongDan() == null
                    ? ""
                    : dto.duongDan().trim();

            if (url.isEmpty()) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Đường dẫn ảnh không hợp lệ"
                );
            }

            HinhAnhSanPham anh = null;

            if (dto.maHinhAnh() != null
                    && !dto.maHinhAnh().isBlank()) {
                anh = hienCo.stream()
                        .filter(a -> a.getMaHinhAnh()
                                .equals(dto.maHinhAnh()))
                        .findFirst()
                        .orElse(null);
            }

            if (anh == null) {
                anh = hienCo.stream()
                        .filter(a -> !daDung.contains(a.getMaHinhAnh()))
                        .filter(a -> url.equals(a.getDuongDan()))
                        .findFirst()
                        .orElse(null);
            }

            if (anh != null
                    && !daDung.add(anh.getMaHinhAnh())) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Danh sách ảnh bị trùng"
                );
            }

            if (anh == null) {
                anh = new HinhAnhSanPham();
                anh.setMaHinhAnh(IdGenerator.next("HA"));
                anh.setSanPham(s);
                daDung.add(anh.getMaHinhAnh());
                s.getHinhAnhs().add(anh);
            }

            anh.setDuongDan(url);

            if (dto.gocChup() != null) {
                anh.setGocChup(dto.gocChup());
            }

            anh.setAnhChinh(false);

            ketQua.add(anhRepo.save(anh));
        }

        for (HinhAnhSanPham cu : hienCo) {
            if (!daDung.contains(cu.getMaHinhAnh())) {
                s.getHinhAnhs().remove(cu);
                anhRepo.delete(cu);
            }
        }

        ganAnhChinh(ketQua, anhChinh);
    }

    private void ganAnhChinh(
            List<HinhAnhSanPham> anhs,
            String chinh
    ) {
        if (anhs.isEmpty()) {
            return;
        }

        String urlChinh = anhs.stream()
                .filter(a -> chinh != null
                        && chinh.equals(a.getDuongDan()))
                .map(HinhAnhSanPham::getDuongDan)
                .findFirst()
                .orElse(anhs.get(0).getDuongDan());

        boolean daGan = false;

        for (HinhAnhSanPham anh : anhs) {
            boolean laChinh = !daGan
                    && urlChinh.equals(anh.getDuongDan());

            if (laChinh) {
                daGan = true;
            }

            anh.setAnhChinh(laChinh);
            anhRepo.save(anh);
        }
    }
}
