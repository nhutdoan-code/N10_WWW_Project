package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.service.ai.SemanticSearchService;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SanPhamService {

    private final SanPhamRepository spRepo;
    private final LoaiSanPhamRepository loaiRepo;
    private final ThuongHieuRepository thRepo;
    private final PhienBanSanPhamRepository pbRepo;
    private final HinhAnhSanPhamRepository anhRepo;
    private final DonHangRepository donRepo;
    private final ChiTietDonHangRepository chiTietDonHangRepo;
    private final ChiTietGioHangRepository chiTietGioHangRepo;
    private final BienDongKhoRepository bienDongKhoRepo;
    private final NhatKyHoatDongService nhatKyHoatDongService;
    private final SemanticSearchService semanticSearchService;

    public List<SanPhamDto> list(String q, String maLoai, BigDecimal min, BigDecimal max, String sort, boolean admin) {
        String qq = q == null ? "" : q.trim().toLowerCase();
        Set<String> loais = (maLoai == null || maLoai.isBlank()) ? null : nhomLoai(maLoai);

        return spRepo.findAll().stream()
                .filter(s -> admin || s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .filter(s -> qq.isEmpty() || containsIgnoreCase(s.getTenMay(), qq) || containsIgnoreCase(s.getMaSanPham(), qq))
                .filter(s -> loais == null || (s.getLoai() != null && loais.contains(s.getLoai().getMaLoai())))
                .map(SanPhamMapper::toDto)
                .filter(dto -> min == null || (dto.giaBan() != null && dto.giaBan().compareTo(min) >= 0))
                .filter(dto -> max == null || (dto.giaBan() != null && dto.giaBan().compareTo(max) <= 0))
                .sorted(buildComparator(sort, false))
                .toList();
    }

    public List<SanPhamDto> timKiem(
            String q, String maLoai, String maThuongHieu,
            BigDecimal min, BigDecimal max, Integer ram, Integer boNho,
            Boolean conHang, BigDecimal minRating, String sort, boolean admin
    ) {
        String qq = q == null ? "" : q.trim().toLowerCase();
        Set<String> loais = (maLoai == null || maLoai.isBlank()) ? null : nhomLoai(maLoai);
        boolean locBienThe = min != null || max != null || ram != null || boNho != null || Boolean.TRUE.equals(conHang);

        List<SanPhamDto> dtos = spRepo.findAll().stream()
                .filter(s -> admin || s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)
                .filter(s -> qq.isEmpty() || containsIgnoreCase(s.getTenMay(), qq) || containsIgnoreCase(s.getMaSanPham(), qq))
                .filter(s -> loais == null || (s.getLoai() != null && loais.contains(s.getLoai().getMaLoai())))
                .filter(s -> maThuongHieu == null || maThuongHieu.isBlank()
                        || (s.getThuongHieu() != null && maThuongHieu.equals(s.getThuongHieu().getMaThuongHieu())))
                .filter(s -> minRating == null || (s.getDiemDanhGiaTb() != null && s.getDiemDanhGiaTb().compareTo(minRating) >= 0))
                .filter(s -> !locBienThe || s.getPhienBans().stream().anyMatch(p -> phienBanKhop(p, min, max, ram, boNho, conHang)))
                .map(SanPhamMapper::toDto)
                .collect(Collectors.toCollection(ArrayList::new));

        // Tie-break mặc định là sản phẩm mới nhất (mã giảm dần)
        dtos.sort(Comparator.comparing(SanPhamDto::maSanPham, Comparator.nullsLast(Comparator.reverseOrder())));
        if (sort != null && !sort.isBlank() && !"moi".equals(sort)) {
            dtos.sort(buildComparator(sort, true));
        }
        return dtos;
    }

    // Tìm sản phẩm dành riêng cho ChatBox
    @Transactional(readOnly = true)
    public List<SanPhamDto> timKiemChoChatbot(String tuKhoa, int gioiHan) {
        if (tuKhoa == null || tuKhoa.isBlank()) return List.of();

        int limit = Math.max(1, Math.min(gioiHan, 10));
        String pattern = "%" + tuKhoa.trim().toLowerCase() + "%";
        Pageable pageable = PageRequest.of(0, limit);

        List<String> ids = spRepo.searchChatbotIds(pattern, TrangThaiSanPham.DANG_KINH_DOANH, pageable);
        if (ids.isEmpty()) return List.of();

        List<SanPham> products = spRepo.findChatbotProductsWithVariants(ids);
        spRepo.findChatbotProductsWithImages(ids);

        Map<String, SanPham> productMap = products.stream()
                .collect(Collectors.toMap(SanPham::getMaSanPham, Function.identity(), (a, b) -> a));

        return ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(SanPhamMapper::toDto)
                .toList();
    }

    private Comparator<SanPhamDto> buildComparator(String sort, boolean supportBestSeller) {
        return switch (sort == null ? "" : sort) {
            case "gia_asc" -> Comparator.comparing(SanPhamDto::giaBan, Comparator.nullsLast(Comparator.naturalOrder()));
            case "gia_desc" -> Comparator.comparing(SanPhamDto::giaBan, Comparator.nullsLast(Comparator.reverseOrder()));
            case "danh_gia" -> Comparator.comparing(SanPhamDto::diemDanhGiaTb, Comparator.nullsLast(Comparator.reverseOrder()));
            case "ban_chay" -> {
                if (!supportBestSeller) yield Comparator.comparing(SanPhamDto::maSanPham, Comparator.nullsLast(Comparator.reverseOrder()));
                Map<String, Long> daBan = daBanTheoSanPham();
                yield Comparator.comparing((SanPhamDto d) -> daBan.getOrDefault(d.maSanPham(), 0L)).reversed();
            }
            default -> Comparator.comparing(SanPhamDto::maSanPham, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    private boolean phienBanKhop(PhienBanSanPham p, BigDecimal min, BigDecimal max, Integer ram, Integer boNho, Boolean conHang) {
        if (p.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) return false;
        if (ram != null && p.getRam() != ram) return false;
        if (boNho != null && p.getBoNho() != boNho) return false;
        if (min != null && (p.getGiaBan() == null || p.getGiaBan().compareTo(min) < 0)) return false;
        if (max != null && (p.getGiaBan() == null || p.getGiaBan().compareTo(max) > 0)) return false;
        return !Boolean.TRUE.equals(conHang) || p.getSoLuongTon() > 0;
    }

    private Map<String, Long> daBanTheoSanPham() {
        return chiTietDonHangRepo.tongDaBanTheoSanPham().stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue(), (a, b) -> a));
    }

    public SanPhamDto get(String ma, boolean admin) {
        SanPham s = find(ma);
        if (!admin && s.getTrangThai() == TrangThaiSanPham.NGUNG_KINH_DOANH) {
            throw new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        return SanPhamMapper.toDto(s);
    }

    public List<SanPhamDto> lienQuan(String ma) {
        SanPham s = find(ma);
        return list(null, s.getLoai().getMaLoai(), null, null, null, false).stream()
                .filter(x -> !x.maSanPham().equals(ma))
                .limit(4)
                .toList();
    }

    public SanPhamDto save(String ma, SanPhamDto d) {
        boolean taoMoi = (ma == null);
        SanPham s = taoMoi ? new SanPham() : find(ma);
        Map<String, Object> duLieuTruoc = taoMoi ? null : snapshot(s);

        if (taoMoi) {
            s.setMaSanPham(IdGenerator.next("SP"));
            s.setDiemDanhGiaTb(BigDecimal.ZERO);
        }
        if (d.maThuongHieu() == null || d.maThuongHieu().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn thương hiệu");
        }

        s.setThuongHieu(thRepo.findById(d.maThuongHieu())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Thương hiệu không tồn tại")));
        s.setLoai(loaiRepo.findById(d.maLoai())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Loại sản phẩm không tồn tại")));
        s.setTenMay(d.tenMay());
        s.setMoTa(d.moTa());
        s.setTrangThai(d.trangThai() == TrangThaiSanPham.NGUNG_KINH_DOANH
                ? TrangThaiSanPham.NGUNG_KINH_DOANH
                : TrangThaiSanPham.DANG_KINH_DOANH);

        if (d.thongSo() != null) {
            var t = d.thongSo();
            s.setKichThuocManHinh(t.kichThuocManHinh());
            s.setLoaiManHinh(t.loaiManHinh());
            s.setTanSoQuet(t.tanSoQuet());
            s.setChip(t.chip());
            s.setCameraSau(t.cameraSau());
            s.setCameraTruoc(t.cameraTruoc() == null ? null : String.valueOf(t.cameraTruoc()));
            s.setPin(t.pin());
            s.setCongSuatSac(t.congSuatSac());
            s.setHeDieuHanh(t.heDieuHanh());
            s.setNfc(t.nfc());
            s.setHo5G(t.ho5G());
            s.setTrongLuong(t.trongLuong());
        }

        s = spRepo.save(s);
        luuPhienBan(s, d.phienBans());
        luuAnh(s, d.hinhAnhs(), d.anhChinh());

        Map<String, Object> duLieuSau = snapshot(s);
        nhatKyHoatDongService.ghi(
                taoMoi ? HanhDongAudit.CREATE_PRODUCT : HanhDongAudit.UPDATE_PRODUCT,
                LoaiDoiTuongAudit.PRODUCT, s.getMaSanPham(), duLieuTruoc, duLieuSau
        );
        if (semanticSearchService != null) semanticSearchService.indexProduct(s);

        return SanPhamMapper.toDto(s);
    }

    private void luuPhienBan(SanPham s, List<SanPhamDto.PhienBan> pbDtos) {
        if (pbDtos == null || pbDtos.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm phải có ít nhất một phiên bản hợp lệ");
        }

        Map<String, PhienBanSanPham> existing = s.getPhienBans() == null ? Map.of()
                : s.getPhienBans().stream().collect(Collectors.toMap(PhienBanSanPham::getMaPhienBan, Function.identity()));
        Set<String> guiLen = pbDtos.stream().map(SanPhamDto.PhienBan::maPhienBan).filter(Objects::nonNull).collect(Collectors.toSet());

        Set<String> comboDaCo = new HashSet<>();
        List<PhienBanSanPham> toRemove = s.getPhienBans() == null ? List.of()
                : s.getPhienBans().stream().filter(ep -> !guiLen.contains(ep.getMaPhienBan())).toList();

        for (PhienBanSanPham rm : toRemove) {
            if (daDuocThamChieu(rm.getMaPhienBan())) {
                rm.setTrangThai(TrangThaiSanPham.NGUNG_KINH_DOANH);
                pbRepo.save(rm);
                comboDaCo.add(comboKey(rm));
            } else {
                pbRepo.delete(rm);
                s.getPhienBans().remove(rm);
            }
        }
        pbRepo.flush();

        List<PhienBanSanPham> toKeep = new ArrayList<>();
        Set<String> seenCombo = new HashSet<>();
        Set<String> seenSku = new HashSet<>();

        for (SanPhamDto.PhienBan pbDto : pbDtos) {
            String mauSac = pbDto.mauSac() == null ? "" : pbDto.mauSac().trim();
            if (pbDto.ram() <= 0 || pbDto.boNho() <= 0) throw new AppException(HttpStatus.BAD_REQUEST, "RAM và bộ nhớ phải lớn hơn 0");
            if (mauSac.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST, "Màu sắc không được để trống");
            if (pbDto.giaBan() == null || pbDto.giaBan().compareTo(BigDecimal.ZERO) < 0) throw new AppException(HttpStatus.BAD_REQUEST, "Giá bán không hợp lệ");
            if (pbDto.giaNiemYet() != null && pbDto.giaNiemYet().compareTo(pbDto.giaBan()) < 0) throw new AppException(HttpStatus.BAD_REQUEST, "Giá niêm yết không được nhỏ hơn giá bán");
            if (pbDto.soLuongTon() < 0) throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng tồn không được âm");

            String combo = comboKey(pbDto.ram(), pbDto.boNho(), mauSac);
            String label = pbDto.ram() + "GB / " + pbDto.boNho() + "GB / " + mauSac;
            if (comboDaCo.contains(combo)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Tổ hợp " + label + " đang thuộc phiên bản đã phát sinh giao dịch, không thể tạo trùng");
            }
            if (!seenCombo.add(combo)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Tổ hợp " + label + " bị trùng");
            }

            PhienBanSanPham p = (pbDto.maPhienBan() != null && existing.containsKey(pbDto.maPhienBan()))
                    ? existing.get(pbDto.maPhienBan())
                    : new PhienBanSanPham();
            if (p.getMaPhienBan() == null) {
                p.setMaPhienBan(IdGenerator.next("PB"));
                p.setSanPham(s);
                s.getPhienBans().add(p);
            }

            String sku = pbDto.sku() == null ? "" : pbDto.sku().trim();
            if (sku.isEmpty()) {
                sku = ensureUniqueSku(generateSku(s.getMaSanPham(), pbDto.ram(), pbDto.boNho(), mauSac), p.getMaPhienBan(), seenSku);
            } else {
                PhienBanSanPham trung = pbRepo.findBySku(sku).orElse(null);
                if (trung != null && !Objects.equals(trung.getMaPhienBan(), p.getMaPhienBan())) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "SKU đã tồn tại: " + sku);
                }
            }
            if (!seenSku.add(sku.toLowerCase())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "SKU bị trùng: " + sku);
            }

            p.setSku(sku);
            p.setRam(pbDto.ram());
            p.setBoNho(pbDto.boNho());
            p.setMauSac(mauSac);
            p.setGiaBan(pbDto.giaBan());
            p.setGiaNiemYet(pbDto.giaNiemYet());
            p.setSoLuongTon(pbDto.soLuongTon());
            p.setTrangThai(pbDto.trangThai() == null ? s.getTrangThai() : pbDto.trangThai());
            pbRepo.save(p);
            toKeep.add(p);
        }

        if (s.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH
                && toKeep.stream().noneMatch(p -> p.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm đang kinh doanh phải có ít nhất một phiên bản đang kinh doanh");
        }
    }

    public void delete(String ma) {
        if (donRepo.daCoTrongDonHang(ma)) {
            throw new AppException(HttpStatus.CONFLICT, "Sản phẩm đã có trong đơn hàng, hãy chuyển sang ngừng kinh doanh");
        }
        SanPham s = find(ma);
        if (s.getPhienBans().stream().anyMatch(p -> daDuocThamChieu(p.getMaPhienBan()))) {
            throw new AppException(HttpStatus.CONFLICT, "Phiên bản sản phẩm đã phát sinh giao dịch hoặc tồn kho, hãy chuyển sang ngừng kinh doanh");
        }

        Map<String, Object> duLieuTruoc = snapshot(s);
        anhRepo.deleteAll(s.getHinhAnhs());
        pbRepo.deleteAll(s.getPhienBans());
        s.getHinhAnhs().clear();
        s.getPhienBans().clear();
        spRepo.delete(s);

        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_PRODUCT, LoaiDoiTuongAudit.PRODUCT, ma, duLieuTruoc, null);
        if (semanticSearchService != null) semanticSearchService.removeProduct(ma);
    }

    public Map<String, Object> getImpact(String ma) {
        SanPham s = find(ma);
        long activeOrders = donRepo.countActiveOrdersBySanPham(ma);
        long allOrders = donRepo.countAllOrdersBySanPham(ma);
        int variantCount = s.getPhienBans() != null ? s.getPhienBans().size() : 0;
        int totalStock = s.getSoLuongTon();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("maSanPham", s.getMaSanPham());
        res.put("tenMay", s.getTenMay());
        res.put("trangThai", s.getTrangThai().name());
        res.put("soPhienBan", variantCount);
        res.put("bienTheCount", variantCount);
        res.put("tongTonKho", totalStock);
        res.put("soLuongTon", totalStock);
        res.put("soDonHangDangXuLy", activeOrders);
        res.put("activeOrdersCount", activeOrders);
        res.put("soDonHangLichSu", allOrders);
        res.put("totalOrdersCount", allOrders);
        res.put("coDonHangDangXuLy", activeOrders > 0);
        return res;
    }

    public SanPhamDto archive(String ma) {
        return updateProductStatus(ma, TrangThaiSanPham.NGUNG_KINH_DOANH);
    }

    public SanPhamDto restore(String ma) {
        return updateProductStatus(ma, TrangThaiSanPham.DANG_KINH_DOANH);
    }

    private SanPhamDto updateProductStatus(String ma, TrangThaiSanPham targetStatus) {
        SanPham s = find(ma);
        if (s.getTrangThai() == targetStatus) return SanPhamMapper.toDto(s);

        Map<String, Object> duLieuTruoc = snapshot(s);
        s.setTrangThai(targetStatus);
        if (s.getPhienBans() != null) {
            s.getPhienBans().forEach(pb -> pb.setTrangThai(targetStatus));
        }
        SanPham saved = spRepo.save(s);
        nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_PRODUCT, LoaiDoiTuongAudit.PRODUCT, ma, duLieuTruoc, snapshot(saved));

        if (semanticSearchService != null) {
            if (targetStatus == TrangThaiSanPham.NGUNG_KINH_DOANH) semanticSearchService.removeProduct(ma);
            else semanticSearchService.indexProduct(saved);
        }
        return SanPhamMapper.toDto(saved);
    }

    private Map<String, Object> snapshot(SanPham s) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("maLoai", s.getLoai() != null ? s.getLoai().getMaLoai() : null);
        duLieu.put("maThuongHieu", s.getThuongHieu() != null ? s.getThuongHieu().getMaThuongHieu() : null);
        duLieu.put("tenMay", s.getTenMay());
        duLieu.put("moTa", s.getMoTa());
        duLieu.put("trangThai", s.getTrangThai() != null ? s.getTrangThai().name() : null);
        duLieu.put("thoiHanBaoHanhThang", s.getThoiHanBaoHanhThang());

        Map<String, Object> thongSo = new LinkedHashMap<>();
        thongSo.put("kichThuocManHinh", s.getKichThuocManHinh());
        thongSo.put("loaiManHinh", s.getLoaiManHinh());
        thongSo.put("tanSoQuet", s.getTanSoQuet());
        thongSo.put("chip", s.getChip());
        thongSo.put("cameraSau", s.getCameraSau());
        thongSo.put("cameraTruoc", s.getCameraTruoc());
        thongSo.put("pin", s.getPin());
        thongSo.put("congSuatSac", s.getCongSuatSac());
        thongSo.put("heDieuHanh", s.getHeDieuHanh());
        thongSo.put("nfc", s.isNfc());
        thongSo.put("ho5G", s.isHo5G());
        thongSo.put("trongLuong", s.getTrongLuong());
        duLieu.put("thongSo", thongSo);

        List phienBans = (s.getPhienBans() == null ? List.<PhienBanSanPham>of() : s.getPhienBans()).stream()
                .sorted(Comparator.comparing(PhienBanSanPham::getMaPhienBan, Comparator.nullsLast(String::compareTo)))
                .map(pb -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("maPhienBan", pb.getMaPhienBan());
                    m.put("sku", pb.getSku());
                    m.put("ram", pb.getRam());
                    m.put("boNho", pb.getBoNho());
                    m.put("mauSac", pb.getMauSac());
                    m.put("giaBan", pb.getGiaBan());
                    m.put("giaNiemYet", pb.getGiaNiemYet());
                    m.put("soLuongTon", pb.getSoLuongTon());
                    m.put("trangThai", pb.getTrangThai() != null ? pb.getTrangThai().name() : null);
                    return m;
                }).toList();
        duLieu.put("phienBans", phienBans);

        List hinhAnhs = (s.getHinhAnhs() == null ? List.<HinhAnhSanPham>of() : s.getHinhAnhs()).stream()
                .sorted(Comparator.comparing(HinhAnhSanPham::getMaHinhAnh, Comparator.nullsLast(String::compareTo)))
                .map(ha -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("maHinhAnh", ha.getMaHinhAnh());
                    m.put("duongDan", ha.getDuongDan());
                    m.put("gocChup", ha.getGocChup());
                    m.put("mauSac", ha.getMauSac());
                    m.put("maPhienBan", ha.getPhienBan() == null ? null : ha.getPhienBan().getMaPhienBan());
                    m.put("anhChinh", ha.isAnhChinh());
                    return m;
                }).toList();
        duLieu.put("hinhAnhs", hinhAnhs);

        return duLieu;
    }

    private SanPham find(String ma) {
        return spRepo.findById(ma)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    private Set<String> nhomLoai(String ma) {
        List<LoaiSanPham> all = loaiRepo.findAll();
        Set<String> ids = new HashSet<>(Set.of(ma));
        boolean added;
        do {
            added = false;
            for (LoaiSanPham l : all) {
                if (l.getMaLoaiCha() != null && ids.contains(l.getMaLoaiCha()) && ids.add(l.getMaLoai())) {
                    added = true;
                }
            }
        } while (added);
        return ids;
    }

    private void luuAnh(SanPham s, List<SanPhamDto.Anh> anhs, String chinh) {
        if (anhs == null) return;

        List<HinhAnhSanPham> hienCo = new ArrayList<>(s.getHinhAnhs());
        Map<String, HinhAnhSanPham> theoId = hienCo.stream()
                .collect(Collectors.toMap(HinhAnhSanPham::getMaHinhAnh, Function.identity()));
        Set<String> daDung = new HashSet<>();
        List<HinhAnhSanPham> ketQua = new ArrayList<>();

        for (SanPhamDto.Anh dto : anhs) {
            String duongDan = dto.duongDan() == null ? null : dto.duongDan().trim();
            if (duongDan == null || duongDan.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Đường dẫn ảnh không hợp lệ");
            }

            HinhAnhSanPham anh = (dto.maHinhAnh() != null && !dto.maHinhAnh().isBlank())
                    ? theoId.get(dto.maHinhAnh())
                    : hienCo.stream().filter(x -> !daDung.contains(x.getMaHinhAnh()) && duongDan.equals(x.getDuongDan())).findFirst().orElse(null);

            boolean moi = (anh == null);
            if (moi) {
                anh = new HinhAnhSanPham();
                anh.setMaHinhAnh(IdGenerator.next("HA"));
                anh.setSanPham(s);
            } else if (!daDung.add(anh.getMaHinhAnh())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Danh sách ảnh bị trùng");
            }
            if (moi) daDung.add(anh.getMaHinhAnh());

            anh.setDuongDan(duongDan);
            if (dto.gocChup() != null) anh.setGocChup(dto.gocChup());

            PhienBanSanPham pbAnh = null;
            if (dto.maPhienBan() != null && !dto.maPhienBan().isBlank()) {
                pbAnh = s.getPhienBans().stream()
                        .filter(p -> dto.maPhienBan().equals(p.getMaPhienBan()))
                        .findFirst()
                        .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Ảnh đang gán cho phiên bản không còn tồn tại, vui lòng chọn lại phạm vi áp dụng"));
            }
            anh.setPhienBan(pbAnh);
            anh.setMauSac(pbAnh != null || dto.mauSac() == null || dto.mauSac().isBlank() ? null : dto.mauSac().trim());
            anh.setAnhChinh(false);

            anh = anhRepo.save(anh);
            if (moi) s.getHinhAnhs().add(anh);
            ketQua.add(anh);
        }

        for (HinhAnhSanPham cu : hienCo) {
            if (!daDung.contains(cu.getMaHinhAnh())) {
                s.getHinhAnhs().remove(cu);
                anhRepo.delete(cu);
            }
        }
        ganAnhChinh(ketQua, chinh);
    }

    private void ganAnhChinh(List<HinhAnhSanPham> anhs, String chinh) {
        if (anhs.isEmpty()) return;
        boolean coTrongDanhSach = chinh != null && anhs.stream().anyMatch(a -> chinh.equals(a.getDuongDan()));
        String duongDanChinh = coTrongDanhSach ? chinh : anhs.get(0).getDuongDan();

        boolean daGan = false;
        for (HinhAnhSanPham a : anhs) {
            boolean laChinh = !daGan && duongDanChinh.equals(a.getDuongDan());
            if (laChinh) daGan = true;
            a.setAnhChinh(laChinh);
            anhRepo.save(a);
        }
    }

    private String generateSku(String maSanPham, int ram, int boNho, String mauSac) {
        String so = maSanPham == null ? "" : maSanPham.replaceAll("\\D+", "");
        if (so.isEmpty()) so = (maSanPham == null || maSanPham.isBlank()) ? "MOI" : slug(maSanPham);
        return "PN-" + so + "-" + ram + "-" + boNho + "-" + slug(mauSac);
    }

    private String slug(String value) {
        if (value == null) return "MAU";
        String khongDau = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'D').replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toUpperCase();
        return khongDau.isEmpty() ? "MAU" : khongDau;
    }

    private String ensureUniqueSku(String base, String maPhienBan, Set<String> daDung) {
        String candidate = base;
        int thuTu = 2;
        while (true) {
            PhienBanSanPham trung = pbRepo.findBySku(candidate).orElse(null);
            boolean trungDb = trung != null && !Objects.equals(trung.getMaPhienBan(), maPhienBan);
            if (!trungDb && !daDung.contains(candidate.toLowerCase())) return candidate;
            candidate = base + "-" + thuTu++;
        }
    }

    private String comboKey(PhienBanSanPham p) {
        return comboKey(p.getRam(), p.getBoNho(), p.getMauSac());
    }

    private String comboKey(int ram, int boNho, String mauSac) {
        return ram + "|" + boNho + "|" + (mauSac == null ? "" : mauSac.trim().toLowerCase());
    }

    private boolean daDuocThamChieu(String maPhienBan) {
        return maPhienBan != null && (
                chiTietDonHangRepo.existsByPhienBanMaPhienBan(maPhienBan)
                        || chiTietGioHangRepo.existsByPhienBanMaPhienBan(maPhienBan)
                        || bienDongKhoRepo.existsByPhienBanMaPhienBan(maPhienBan)
        );
    }
}