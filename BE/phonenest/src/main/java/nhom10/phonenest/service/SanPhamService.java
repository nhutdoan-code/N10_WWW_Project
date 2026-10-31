package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class SanPhamService {

    private final SanPhamRepository spRepo;
    private final LoaiSanPhamRepository loaiRepo;
    private final HinhAnhSanPhamRepository anhRepo;
    private final DonHangRepository donRepo;
    private final ChiTietGioHangRepository chiTietGioHangRepo;
    private final PhienBanSanPhamRepository phienBanRepo;

    // DANH SÁCH
    public List<SanPhamDto> list(String q, String maLoai, BigDecimal min,
                                 BigDecimal max, String sort, boolean admin) {
        String keyword = q == null ? "" : q.trim().toLowerCase();
        Set<String> loais = maLoai == null || maLoai.isBlank() ? null : nhomLoai(maLoai);
        List<SanPhamDto> result = new ArrayList<>();

        for (SanPham s : spRepo.findAll()) {
            if (!((keyword.isEmpty())
                    || (s.getTenMay() != null && s.getTenMay().toLowerCase().contains(keyword))
                    || (s.getMaSanPham() != null && s.getMaSanPham().toLowerCase().contains(keyword)))) {
                continue;
            }

            if (loais != null && (s.getLoai() == null || !loais.contains(s.getLoai().getMaLoai()))) {
                continue;
            }

            SanPhamDto dto = SanPhamMapper.toDto(s);

            if (!admin && dto.trangThai() != TrangThaiSanPham.CON_HANG) continue;
            if (min != null && dto.giaBan().compareTo(min) < 0) continue;
            if (max != null && dto.giaBan().compareTo(max) > 0) continue;

            result.add(dto);
        }

        Comparator<SanPhamDto> comparator = switch (sort == null ? "" : sort) {
            case "gia_asc" -> Comparator.comparing(SanPhamDto::giaBan);
            case "gia_desc" -> Comparator.comparing(SanPhamDto::giaBan).reversed();
            case "danh_gia" -> Comparator.comparing(
                    SanPhamDto::diemDanhGiaTb,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
            default -> Comparator.comparing(SanPhamDto::maSanPham).reversed();
        };

        return result.stream().sorted(comparator).toList();
    }

    // CHI TIẾT
    public SanPhamDto get(String ma, boolean admin) {
        SanPhamDto dto = SanPhamMapper.toDto(find(ma));

        if (!admin && dto.trangThai() == TrangThaiSanPham.NGUNG_KINH_DOANH) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }

        return dto;
    }

    // SẢN PHẨM LIÊN QUAN
    public List<SanPhamDto> lienQuan(String ma) {
        SanPham s = find(ma);

        return list(null, s.getLoai().getMaLoai(), null, null, null, false)
                .stream()
                .filter(x -> !x.maSanPham().equals(ma))
                .limit(4)
                .toList();
    }

    // THÊM / CẬP NHẬT
    public SanPhamDto save(String ma, SanPhamDto d) {
        boolean taoMoi = ma == null;

        if (d.phienBans() != null && d.phienBans().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Danh sách phiên bản không được để trống");
        }

        if (taoMoi && (d.phienBans() == null || d.phienBans().isEmpty())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm phải có ít nhất một phiên bản");
        }

        SanPham s = taoMoi ? new SanPham() : find(ma);

        if (taoMoi) {
            s.setMaSanPham(IdGenerator.next("SP"));
            s.setDiemDanhGiaTb(BigDecimal.ZERO);
            s.setGiaBan(BigDecimal.ZERO);
            s.setSoLuongTon(0);
        }

        LoaiSanPham loai = loaiRepo.findById(d.maLoai())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Loại sản phẩm không tồn tại"));

        s.setLoai(loai);
        s.setTenMay(d.tenMay());
        s.setMoTa(d.moTa());

        if (d.giaBan() != null) {
            if (d.giaBan().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Giá bán không được âm");
            }
            s.setGiaBan(d.giaBan());
        }

        if (d.soLuongTon() < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng tồn không được âm");
        }

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

        if (d.phienBans() != null) {
            luuPhienBans(s, d.phienBans());
        }

        capNhatThongKe(s, d.trangThai());
        s = spRepo.save(s);

        return SanPhamMapper.toDto(s);
    }

    // LƯU PHIÊN BẢN
    private void luuPhienBans(SanPham sanPham, List<SanPhamDto.PhienBan> danhSach) {
        List<PhienBanSanPham> hienCo = phienBanRepo.findBySanPham_MaSanPham(sanPham.getMaSanPham());
        Set<String> cauHinhDaNhap = new HashSet<>();

        for (SanPhamDto.PhienBan dto : danhSach) {
            if (dto.ram() <= 0 || dto.boNho() <= 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "RAM và bộ nhớ phải lớn hơn 0");
            }

            if (dto.mauSac() == null || dto.mauSac().isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng nhập màu sắc");
            }

            if (dto.giaBan() == null || dto.giaBan().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Giá phiên bản không hợp lệ");
            }

            if (dto.giaNiemYet() != null && dto.giaNiemYet().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Giá niêm yết không được âm");
            }

            if (dto.soLuongTon() < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng tồn không được âm");
            }

            String mauSac = dto.mauSac().trim();
            String cauHinh = dto.ram() + "|" + dto.boNho() + "|" + mauSac.toLowerCase(Locale.ROOT);

            if (!cauHinhDaNhap.add(cauHinh)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Phiên bản bị trùng RAM, bộ nhớ và màu sắc");
            }

            PhienBanSanPham pb;

            if (dto.maPhienBan() == null || dto.maPhienBan().isBlank()) {
                pb = new PhienBanSanPham();
                pb.setMaPhienBan(IdGenerator.next("PB"));
            } else {
                pb = phienBanRepo.findById(dto.maPhienBan())
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản"));

                if (pb.getSanPham() == null ||
                        !pb.getSanPham().getMaSanPham().equals(sanPham.getMaSanPham())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "Phiên bản không thuộc sản phẩm này");
                }
            }

            String maPB = pb.getMaPhienBan();

            boolean trung = hienCo.stream()
                    .filter(x -> !x.getMaPhienBan().equals(maPB))
                    .anyMatch(x -> x.getRam() == dto.ram()
                            && x.getBoNho() == dto.boNho()
                            && x.getMauSac().equalsIgnoreCase(mauSac));

            if (trung) {
                throw new AppException(HttpStatus.CONFLICT, "Cấu hình phiên bản đã tồn tại");
            }

            String sku = dto.sku();
            if (sku == null || sku.isBlank()) {
                sku = pb.getSku() == null ? "SKU-" + maPB : pb.getSku();
            }

            pb.setSanPham(sanPham);
            pb.setSku(sku.trim());
            pb.setRam(dto.ram());
            pb.setBoNho(dto.boNho());
            pb.setMauSac(mauSac);
            pb.setGiaBan(dto.giaBan());
            pb.setGiaNiemYet(dto.giaNiemYet());
            pb.setSoLuongTon(dto.soLuongTon());

            TrangThaiSanPham trangThai = dto.trangThai() == null
                    ? (dto.soLuongTon() > 0 ? TrangThaiSanPham.CON_HANG : TrangThaiSanPham.HET_HANG)
                    : dto.trangThai();

            if (dto.soLuongTon() == 0 && trangThai == TrangThaiSanPham.CON_HANG) {
                trangThai = TrangThaiSanPham.HET_HANG;
            }

            pb.setTrangThai(trangThai);
            phienBanRepo.save(pb);

            if (sanPham.getPhienBans().stream().noneMatch(x -> x.getMaPhienBan().equals(maPB))) {
                sanPham.getPhienBans().add(pb);
            }
        }
    }

    // CẬP NHẬT GIÁ, TỒN KHO VÀ TRẠNG THÁI TỔNG
    private void capNhatThongKe(SanPham sanPham, TrangThaiSanPham trangThaiYeuCau) {
        List<PhienBanSanPham> ds = phienBanRepo.findBySanPham_MaSanPham(sanPham.getMaSanPham());

        if (ds.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm chưa có phiên bản");
        }

        List<PhienBanSanPham> dangKinhDoanh = ds.stream()
                .filter(p -> p.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                .toList();

        BigDecimal giaThapNhat = dangKinhDoanh.stream()
                .map(PhienBanSanPham::getGiaBan)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(ds.get(0).getGiaBan());

        int tongTonKho = dangKinhDoanh.stream()
                .mapToInt(PhienBanSanPham::getSoLuongTon)
                .sum();

        sanPham.setGiaBan(giaThapNhat);
        sanPham.setSoLuongTon(tongTonKho);

        if (trangThaiYeuCau == TrangThaiSanPham.NGUNG_KINH_DOANH || dangKinhDoanh.isEmpty()) {
            sanPham.setTrangThai(TrangThaiSanPham.NGUNG_KINH_DOANH);
            return;
        }

        boolean coHang = dangKinhDoanh.stream()
                .anyMatch(p -> p.getTrangThai() == TrangThaiSanPham.CON_HANG && p.getSoLuongTon() > 0);

        sanPham.setTrangThai(coHang ? TrangThaiSanPham.CON_HANG : TrangThaiSanPham.HET_HANG);
    }

    // XÓA SẢN PHẨM
    public void delete(String ma) {
        SanPham s = find(ma);

        if (donRepo.daCoTrongDonHang(ma)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Sản phẩm đã có trong đơn hàng, hãy chuyển sang ngừng kinh doanh");
        }

        if (chiTietGioHangRepo.existsByPhienBan_SanPham_MaSanPham(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Sản phẩm đang nằm trong giỏ hàng");
        }

        anhRepo.deleteAll(s.getHinhAnhs());
        s.getHinhAnhs().clear();

        List<PhienBanSanPham> phienBans = phienBanRepo.findBySanPham_MaSanPham(ma);
        phienBanRepo.deleteAll(phienBans);
        spRepo.delete(s);
    }

    private SanPham find(String ma) {
        return spRepo.findByIdWithPhienBans(ma)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm"
                ));
    }
    // NHÓM LOẠI SẢN PHẨM
    private Set<String> nhomLoai(String ma) {
        List<LoaiSanPham> all = loaiRepo.findAll();
        Set<String> ids = new HashSet<>();
        ids.add(ma);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (LoaiSanPham loai : all) {
                if (loai.getMaLoaiCha() != null && ids.contains(loai.getMaLoaiCha())
                        && ids.add(loai.getMaLoai())) {
                    changed = true;
                }
            }
        }
        return ids;
    }

    // QUẢN LÝ HÌNH ẢNH
    private void luuAnh(SanPham s, List<SanPhamDto.Anh> anhs, String anhChinh) {
        if (anhs == null) return;

        List<HinhAnhSanPham> hienCo = new ArrayList<>(s.getHinhAnhs());
        Set<String> daDung = new HashSet<>();
        List<HinhAnhSanPham> ketQua = new ArrayList<>();

        for (SanPhamDto.Anh dto : anhs) {
            String url = dto.duongDan() == null ? "" : dto.duongDan().trim();
            if (url.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Đường dẫn ảnh không hợp lệ");
            }

            HinhAnhSanPham anh = null;

            if (dto.maHinhAnh() != null && !dto.maHinhAnh().isBlank()) {
                anh = hienCo.stream()
                        .filter(a -> a.getMaHinhAnh().equals(dto.maHinhAnh()))
                        .findFirst().orElse(null);
            }

            if (anh == null) {
                anh = hienCo.stream()
                        .filter(a -> !daDung.contains(a.getMaHinhAnh()))
                        .filter(a -> url.equals(a.getDuongDan()))
                        .findFirst().orElse(null);
            }

            if (anh != null && !daDung.add(anh.getMaHinhAnh())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Danh sách ảnh bị trùng");
            }

            if (anh == null) {
                anh = new HinhAnhSanPham();
                anh.setMaHinhAnh(IdGenerator.next("HA"));
                anh.setSanPham(s);
                daDung.add(anh.getMaHinhAnh());
                s.getHinhAnhs().add(anh);
            }

            anh.setDuongDan(url);
            if (dto.gocChup() != null) anh.setGocChup(dto.gocChup());
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

    private void ganAnhChinh(List<HinhAnhSanPham> anhs, String chinh) {
        if (anhs.isEmpty()) return;

        String urlChinh = anhs.stream()
                .filter(a -> chinh != null && chinh.equals(a.getDuongDan()))
                .map(HinhAnhSanPham::getDuongDan)
                .findFirst().orElse(anhs.get(0).getDuongDan());

        boolean daGan = false;
        for (HinhAnhSanPham anh : anhs) {
            boolean laChinh = !daGan && urlChinh.equals(anh.getDuongDan());
            if (laChinh) daGan = true;
            anh.setAnhChinh(laChinh);
            anhRepo.save(anh);
        }
    }
}