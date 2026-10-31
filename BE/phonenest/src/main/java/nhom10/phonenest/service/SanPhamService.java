package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.HinhAnhSanPham;
import nhom10.phonenest.entity.LoaiSanPham;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.BienDongKhoRepository;
import nhom10.phonenest.repository.ChiTietDonHangRepository;
import nhom10.phonenest.repository.ChiTietGioHangRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.HinhAnhSanPhamRepository;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.ThuongHieuRepository;
import nhom10.phonenest.util.IdGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    public List<SanPhamDto> list(
            String q,
            String maLoai,
            BigDecimal min,
            BigDecimal max,
            String sort,
            boolean admin
    ) {
        List<SanPham> all = spRepo.findAll();

        /*
         * =========================================================
         * FILTER
         * =========================================================
         */

        String qq = q == null
                ? ""
                : q.trim().toLowerCase();

        Set<String> loais =
                maLoai == null || maLoai.isBlank()
                        ? null
                        : nhomLoai(maLoai);

        List<SanPham> filtered = all.stream()

                // Khách hàng chỉ thấy sản phẩm đang kinh doanh
                .filter(s ->
                        admin
                                || s.getTrangThai()
                                == TrangThaiSanPham.DANG_KINH_DOANH
                )

                // Tìm kiếm theo tên hoặc mã
                .filter(s ->
                        qq.isEmpty()
                                || (
                                s.getTenMay() != null
                                        && s.getTenMay()
                                        .toLowerCase()
                                        .contains(qq)
                        )
                                || (
                                s.getMaSanPham() != null
                                        && s.getMaSanPham()
                                        .toLowerCase()
                                        .contains(qq)
                        )
                )

                // Lọc loại sản phẩm
                .filter(s ->
                        loais == null
                                || (
                                s.getLoai() != null
                                        && loais.contains(
                                        s.getLoai().getMaLoai()
                                )
                        )
                )

                .toList();

        /*
         * =========================================================
         * ENTITY -> DTO
         * =========================================================
         */

        List<SanPhamDto> dtos = new ArrayList<>();

        for (SanPham sanPham : filtered) {

            SanPhamDto dto = SanPhamMapper.toDto(sanPham);

            // Lọc giá thấp nhất
            if (min != null) {
                if (dto.giaBan() == null
                        || dto.giaBan().compareTo(min) < 0) {
                    continue;
                }
            }

            // Lọc giá cao nhất
            if (max != null) {
                if (dto.giaBan() == null
                        || dto.giaBan().compareTo(max) > 0) {
                    continue;
                }
            }

            dtos.add(dto);
        }

        /*
         * =========================================================
         * SORT
         * =========================================================
         */

        Comparator<SanPhamDto> comparator;

        if ("gia_asc".equals(sort)) {

            comparator = Comparator.comparing(
                    SanPhamDto::giaBan,
                    Comparator.nullsLast(
                            Comparator.naturalOrder()
                    )
            );

        } else if ("gia_desc".equals(sort)) {

            comparator = Comparator.comparing(
                    SanPhamDto::giaBan,
                    Comparator.nullsLast(
                            Comparator.reverseOrder()
                    )
            );

        } else if ("danh_gia".equals(sort)) {

            comparator = Comparator.comparing(
                    SanPhamDto::diemDanhGiaTb,
                    Comparator.nullsLast(
                            Comparator.reverseOrder()
                    )
            );

        } else {

            comparator = Comparator.comparing(
                    SanPhamDto::maSanPham,
                    Comparator.nullsLast(
                            Comparator.reverseOrder()
                    )
            );
        }

        return dtos.stream()
                .sorted(comparator)
                .toList();
    }
    /**
     * Tìm kiếm + lọc nâng cao cho khách hàng.
     * RAM / bộ nhớ / giá / còn hàng được kiểm tra trên CÙNG MỘT phiên bản:
     * sản phẩm hiện ra khi có ít nhất 1 phiên bản đang kinh doanh thỏa tất cả điều kiện.
     */
    public List<SanPhamDto> timKiem(
            String q,
            String maLoai,
            String maThuongHieu,
            BigDecimal min,
            BigDecimal max,
            Integer ram,
            Integer boNho,
            Boolean conHang,
            BigDecimal minRating,
            String sort,
            boolean admin
    ) {

        String qq = q == null ? "" : q.trim().toLowerCase();

        Set<String> loais = (maLoai == null || maLoai.isBlank())
                ? null
                : nhomLoai(maLoai);

        boolean locBienThe = min != null || max != null
                || ram != null || boNho != null
                || Boolean.TRUE.equals(conHang);

        List<SanPhamDto> dtos = new ArrayList<>();

        for (SanPham s : spRepo.findAll()) {

            if (!admin && s.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
                continue;
            }

            if (!qq.isEmpty()) {
                boolean khopTen = s.getTenMay() != null
                        && s.getTenMay().toLowerCase().contains(qq);
                boolean khopMa = s.getMaSanPham() != null
                        && s.getMaSanPham().toLowerCase().contains(qq);
                if (!khopTen && !khopMa) {
                    continue;
                }
            }

            if (loais != null
                    && (s.getLoai() == null || !loais.contains(s.getLoai().getMaLoai()))) {
                continue;
            }

            if (maThuongHieu != null && !maThuongHieu.isBlank()
                    && (s.getThuongHieu() == null
                    || !maThuongHieu.equals(s.getThuongHieu().getMaThuongHieu()))) {
                continue;
            }

            if (minRating != null
                    && (s.getDiemDanhGiaTb() == null
                    || s.getDiemDanhGiaTb().compareTo(minRating) < 0)) {
                continue;
            }

            if (locBienThe && s.getPhienBans().stream()
                    .noneMatch(p -> phienBanKhop(p, min, max, ram, boNho, conHang))) {
                continue;
            }

            dtos.add(SanPhamMapper.toDto(s));
        }

        // Sort ổn định: xếp "mới nhất" trước, rồi sort theo tiêu chí chọn (tie-break = mới nhất)
        dtos.sort(Comparator.comparing(
                SanPhamDto::maSanPham,
                Comparator.nullsLast(Comparator.reverseOrder())));

        String kieu = sort == null ? "" : sort;

        if ("gia_asc".equals(kieu)) {
            dtos.sort(Comparator.comparing(
                    SanPhamDto::giaBan,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        } else if ("gia_desc".equals(kieu)) {
            dtos.sort(Comparator.comparing(
                    SanPhamDto::giaBan,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("danh_gia".equals(kieu)) {
            dtos.sort(Comparator.comparing(
                    SanPhamDto::diemDanhGiaTb,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("ban_chay".equals(kieu)) {
            Map<String, Long> daBan = daBanTheoSanPham();
            dtos.sort(Comparator
                    .comparing((SanPhamDto d) -> daBan.getOrDefault(d.maSanPham(), 0L))
                    .reversed());
        }
        // "moi" hoặc rỗng: giữ thứ tự mới nhất ở trên

        return dtos;
    }

    private boolean phienBanKhop(
            PhienBanSanPham p,
            BigDecimal min,
            BigDecimal max,
            Integer ram,
            Integer boNho,
            Boolean conHang
    ) {
        if (p.getTrangThai() != TrangThaiSanPham.DANG_KINH_DOANH) {
            return false;
        }
        if (ram != null && p.getRam() != ram.intValue()) {
            return false;
        }
        if (boNho != null && p.getBoNho() != boNho.intValue()) {
            return false;
        }
        if (min != null && (p.getGiaBan() == null || p.getGiaBan().compareTo(min) < 0)) {
            return false;
        }
        if (max != null && (p.getGiaBan() == null || p.getGiaBan().compareTo(max) > 0)) {
            return false;
        }
        if (Boolean.TRUE.equals(conHang) && p.getSoLuongTon() <= 0) {
            return false;
        }
        return true;
    }

    private Map<String, Long> daBanTheoSanPham() {
        Map<String, Long> kq = new HashMap<>();
        for (Object[] r : chiTietDonHangRepo.tongDaBanTheoSanPham()) {
            kq.put((String) r[0], ((Number) r[1]).longValue());
        }
        return kq;
    }

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

    public List<SanPhamDto> lienQuan(String ma) {

        SanPham s = find(ma);

        return list(
                null,
                s.getLoai().getMaLoai(),
                null,
                null,
                null,
                false
        )
                .stream()
                .filter(x ->
                        !x.maSanPham().equals(ma)
                )
                .limit(4)
                .toList();
    }

    public SanPhamDto save(
            String ma,
            SanPhamDto d
    ) {

        boolean taoMoi = (ma == null);
        SanPham s =
                taoMoi
                        ? new SanPham()
                        : find(ma);

        Map<String, Object> duLieuTruoc = taoMoi ? null : snapshot(s);

        if (ma == null) {

            s.setMaSanPham(
                    IdGenerator.next("SP")
            );

            s.setDiemDanhGiaTb(
                    BigDecimal.ZERO
            );
        }

        if (d.maThuongHieu() == null || d.maThuongHieu().isBlank()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn thương hiệu"
            );
        }

        s.setThuongHieu(
                thRepo.findById(d.maThuongHieu())
                        .orElseThrow(() ->
                                new AppException(
                                        HttpStatus.BAD_REQUEST,
                                        "Thương hiệu không tồn tại"
                                )
                        )
        );

        s.setLoai(
                loaiRepo.findById(d.maLoai())
                        .orElseThrow(() ->
                                new AppException(
                                        HttpStatus.BAD_REQUEST,
                                        "Loại sản phẩm không tồn tại"
                                )
                        )
        );

        s.setTenMay(d.tenMay());
        s.setMoTa(d.moTa());

        s.setTrangThai(
                d.trangThai()
                        == TrangThaiSanPham.NGUNG_KINH_DOANH

                        ? TrangThaiSanPham.NGUNG_KINH_DOANH
                        : TrangThaiSanPham.DANG_KINH_DOANH
        );

        /*
         * =========================================================
         * THÔNG SỐ KỸ THUẬT
         * =========================================================
         */

        if (d.thongSo() != null) {

            var t = d.thongSo();

            s.setKichThuocManHinh(
                    t.kichThuocManHinh()
            );

            s.setLoaiManHinh(
                    t.loaiManHinh()
            );

            s.setTanSoQuet(
                    t.tanSoQuet()
            );

            s.setChip(
                    t.chip()
            );

            s.setCameraSau(
                    t.cameraSau()
            );

            s.setCameraTruoc(
                    t.cameraTruoc() == null
                            ? null
                            : String.valueOf(
                            t.cameraTruoc()
                    )
            );

            s.setPin(
                    t.pin()
            );

            s.setCongSuatSac(
                    t.congSuatSac()
            );

            s.setHeDieuHanh(
                    t.heDieuHanh()
            );

            s.setNfc(
                    t.nfc()
            );

            s.setHo5G(
                    t.ho5G()
            );

            s.setTrongLuong(
                    t.trongLuong()
            );
        }

        s = spRepo.save(s);

        /*
         * =========================================================
         * PHIÊN BẢN
         * =========================================================
         */

        if (d.phienBans() == null || d.phienBans().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Sản phẩm phải có ít nhất một phiên bản hợp lệ");
        }

        Map<String, PhienBanSanPham> existing = new HashMap<>();
        if (s.getPhienBans() != null) {
            for (PhienBanSanPham ep : s.getPhienBans()) {
                existing.put(ep.getMaPhienBan(), ep);
            }
        }

        Set<String> guiLen = new HashSet<>();
        for (SanPhamDto.PhienBan pbDto : d.phienBans()) {
            if (pbDto.maPhienBan() != null) {
                guiLen.add(pbDto.maPhienBan());
            }
        }

        /*
         * Xử lý các phiên bản bị bỏ khỏi form TRƯỚC khi thêm/sửa phiên bản mới.
         * - Đã phát sinh giao dịch/tồn kho: chỉ ngừng kinh doanh, giữ lịch sử.
         * - Chưa phát sinh: xóa vật lý và flush ngay để tránh vi phạm
         *   ràng buộc duy nhất (maSanPham, ram, boNho, mauSac) khi thêm lại
         *   cùng một tổ hợp trong cùng một lần lưu.
         */

        Set<String> comboDaCo = new HashSet<>();
        List<PhienBanSanPham> toRemove = new ArrayList<>();

        if (s.getPhienBans() != null) {
            for (PhienBanSanPham ep : s.getPhienBans()) {
                if (!guiLen.contains(ep.getMaPhienBan())) {
                    toRemove.add(ep);
                }
            }
        }

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

        for (SanPhamDto.PhienBan pbDto : d.phienBans()) {
            String mauSac = pbDto.mauSac() == null ? "" : pbDto.mauSac().trim();

            if (pbDto.ram() <= 0 || pbDto.boNho() <= 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "RAM và bộ nhớ phải lớn hơn 0");
            }
            if (mauSac.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Màu sắc không được để trống");
            }
            if (pbDto.giaBan() == null || pbDto.giaBan().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Giá bán không hợp lệ");
            }
            if (pbDto.giaNiemYet() != null && pbDto.giaNiemYet().compareTo(pbDto.giaBan()) < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Giá niêm yết không được nhỏ hơn giá bán");
            }
            if (pbDto.soLuongTon() < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Số lượng tồn không được âm");
            }

            String combo = comboKey(pbDto.ram(), pbDto.boNho(), mauSac);

            if (comboDaCo.contains(combo)) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Tổ hợp " + pbDto.ram() + "GB / " + pbDto.boNho() + "GB / " + mauSac
                                + " đang thuộc phiên bản đã phát sinh giao dịch, không thể tạo trùng");
            }
            if (!seenCombo.add(combo)) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Tổ hợp " + pbDto.ram() + "GB / " + pbDto.boNho() + "GB / " + mauSac + " bị trùng");
            }

            PhienBanSanPham p;
            if (pbDto.maPhienBan() != null && existing.containsKey(pbDto.maPhienBan())) {
                p = existing.get(pbDto.maPhienBan());
            } else {
                p = new PhienBanSanPham();
                p.setMaPhienBan(IdGenerator.next("PB"));
                p.setSanPham(s);
                s.getPhienBans().add(p);
            }

            String sku = pbDto.sku() == null ? "" : pbDto.sku().trim();
            if (sku.isEmpty()) {
                sku = ensureUniqueSku(
                        generateSku(s.getMaSanPham(), pbDto.ram(), pbDto.boNho(), mauSac),
                        p.getMaPhienBan(),
                        seenSku
                );
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
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Sản phẩm đang kinh doanh phải có ít nhất một phiên bản đang kinh doanh");
        }

        /*
         * =========================================================
         * HÌNH ẢNH
         * =========================================================
         */

        luuAnh(
                s,
                d.hinhAnhs(),
                d.anhChinh()
        );

        Map<String, Object> duLieuSau = snapshot(s);
        if (taoMoi) {
            nhatKyHoatDongService.ghi(HanhDongAudit.CREATE_PRODUCT, LoaiDoiTuongAudit.PRODUCT,
                    s.getMaSanPham(), null, duLieuSau);
        } else {
            nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_PRODUCT, LoaiDoiTuongAudit.PRODUCT,
                    s.getMaSanPham(), duLieuTruoc, duLieuSau);
        }

        return SanPhamMapper.toDto(s);
    }

    public void delete(String ma) {

        if (donRepo.daCoTrongDonHang(ma)) {

            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Sản phẩm đã có trong đơn hàng, hãy chuyển sang ngừng kinh doanh"
            );
        }

        SanPham s = find(ma);

        boolean coThamChieu = s.getPhienBans()
                .stream()
                .anyMatch(p -> daDuocThamChieu(p.getMaPhienBan()));

        if (coThamChieu) {

            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Phiên bản sản phẩm đã phát sinh giao dịch hoặc tồn kho, hãy chuyển sang ngừng kinh doanh"
            );
        }

        Map<String, Object> duLieuTruoc = snapshot(s);

        anhRepo.deleteAll(s.getHinhAnhs());
        pbRepo.deleteAll(s.getPhienBans());

        s.getHinhAnhs().clear();
        s.getPhienBans().clear();

        spRepo.delete(s);

        nhatKyHoatDongService.ghi(HanhDongAudit.DELETE_PRODUCT, LoaiDoiTuongAudit.PRODUCT,
                ma, duLieuTruoc, null);
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

        List<Map<String, Object>> phienBans = new ArrayList<>();
        if (s.getPhienBans() != null) {
            List<PhienBanSanPham> dsPb = new ArrayList<>(s.getPhienBans());
            dsPb.sort(Comparator.comparing(PhienBanSanPham::getMaPhienBan, Comparator.nullsLast(String::compareTo)));
            for (PhienBanSanPham pb : dsPb) {
                Map<String, Object> pbMap = new LinkedHashMap<>();
                pbMap.put("maPhienBan", pb.getMaPhienBan());
                pbMap.put("sku", pb.getSku());
                pbMap.put("ram", pb.getRam());
                pbMap.put("boNho", pb.getBoNho());
                pbMap.put("mauSac", pb.getMauSac());
                pbMap.put("giaBan", pb.getGiaBan());
                pbMap.put("giaNiemYet", pb.getGiaNiemYet());
                pbMap.put("soLuongTon", pb.getSoLuongTon());
                pbMap.put("trangThai", pb.getTrangThai() != null ? pb.getTrangThai().name() : null);
                phienBans.add(pbMap);
            }
        }
        duLieu.put("phienBans", phienBans);

        List<Map<String, Object>> hinhAnhs = new ArrayList<>();
        if (s.getHinhAnhs() != null) {
            List<HinhAnhSanPham> dsAnh = new ArrayList<>(s.getHinhAnhs());
            dsAnh.sort(Comparator.comparing(HinhAnhSanPham::getMaHinhAnh, Comparator.nullsLast(String::compareTo)));
            for (HinhAnhSanPham ha : dsAnh) {
                Map<String, Object> haMap = new LinkedHashMap<>();
                haMap.put("maHinhAnh", ha.getMaHinhAnh());
                haMap.put("duongDan", ha.getDuongDan());
                haMap.put("gocChup", ha.getGocChup());
                haMap.put("mauSac", ha.getMauSac());
                haMap.put("maPhienBan", ha.getPhienBan() == null ? null : ha.getPhienBan().getMaPhienBan());
                haMap.put("anhChinh", ha.isAnhChinh());
                hinhAnhs.add(haMap);
            }
        }
        duLieu.put("hinhAnhs", hinhAnhs);

        return duLieu;
    }

    private SanPham find(String ma) {

        return spRepo.findById(ma)
                .orElseThrow(() ->
                        new AppException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy sản phẩm"
                        )
                );
    }

    private Set<String> nhomLoai(String ma) {

        List<LoaiSanPham> all =
                loaiRepo.findAll();

        Set<String> ids =
                new HashSet<>();

        ids.add(ma);

        boolean doi = true;

        while (doi) {

            doi = false;

            for (LoaiSanPham l : all) {

                if (l.getMaLoaiCha() != null
                        && ids.contains(
                        l.getMaLoaiCha()
                )
                        && ids.add(
                        l.getMaLoai()
                )) {

                    doi = true;
                }
            }
        }

        return ids;
    }

    private void luuAnh(
            SanPham s,
            List<SanPhamDto.Anh> anhs,
            String chinh
    ) {

        if (anhs == null) {
            return;
        }

        /*
         * Reconcile theo identity maHinhAnh:
         * - Ảnh có id và còn tồn tại: cập nhật tại chỗ, giữ nguyên record + metadata.
         * - Ảnh không có id: tạo record mới (ảnh vừa upload).
         * - Record cũ không còn trong payload: xóa đúng record đó.
         * Không xóa/tạo lại toàn bộ collection.
         */

        List<HinhAnhSanPham> hienCo = new ArrayList<>(s.getHinhAnhs());
        Map<String, HinhAnhSanPham> theoId = new HashMap<>();
        for (HinhAnhSanPham a : hienCo) {
            theoId.put(a.getMaHinhAnh(), a);
        }

        Set<String> daDung = new HashSet<>();
        List<HinhAnhSanPham> ketQua = new ArrayList<>();

        for (SanPhamDto.Anh dto : anhs) {

            String duongDan = dto.duongDan() == null ? null : dto.duongDan().trim();
            if (duongDan == null || duongDan.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Đường dẫn ảnh không hợp lệ");
            }

            HinhAnhSanPham anh = null;
            if (dto.maHinhAnh() != null && !dto.maHinhAnh().isBlank()) {
                anh = theoId.get(dto.maHinhAnh());
            }

            /*
             * Tương thích payload cũ chỉ gửi URL (không có maHinhAnh):
             * fallback khớp duongDan trong phạm vi sản phẩm, không dùng index.
             */
            if (anh == null && (dto.maHinhAnh() == null || dto.maHinhAnh().isBlank())) {
                for (HinhAnhSanPham x : hienCo) {
                    if (!daDung.contains(x.getMaHinhAnh()) && duongDan.equals(x.getDuongDan())) {
                        anh = x;
                        break;
                    }
                }
            }

            boolean moi = false;
            if (anh == null) {
                anh = new HinhAnhSanPham();
                anh.setMaHinhAnh(IdGenerator.next("HA"));
                anh.setSanPham(s);
                moi = true;
            } else if (!daDung.add(anh.getMaHinhAnh())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Danh sách ảnh bị trùng");
            }

            if (moi) {
                daDung.add(anh.getMaHinhAnh());
            }

            anh.setDuongDan(duongDan);

            // null = client không gửi metadata -> giữ nguyên gocChup hiện có.
            if (dto.gocChup() != null) {
                anh.setGocChup(dto.gocChup());
            }
            // Phạm vi áp dụng (loại trừ nhau): theo phiên bản > theo màu > ảnh chung
            PhienBanSanPham pbAnh = null;
            if (dto.maPhienBan() != null && !dto.maPhienBan().isBlank()) {
                pbAnh = s.getPhienBans().stream()
                        .filter(p -> dto.maPhienBan().equals(p.getMaPhienBan()))
                        .findFirst()
                        .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                                "Ảnh đang gán cho phiên bản không còn tồn tại, vui lòng chọn lại phạm vi áp dụng"));
            }
            anh.setPhienBan(pbAnh);
            anh.setMauSac(pbAnh != null || dto.mauSac() == null || dto.mauSac().isBlank()
                    ? null
                    : dto.mauSac().trim());

            anh.setAnhChinh(false);

            anh = anhRepo.save(anh);
            if (moi) {
                s.getHinhAnhs().add(anh);
            }
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

    /**
     * Đảm bảo đúng một ảnh chính, nhất quán với fallback của SanPhamMapper
     * và Product Detail: nếu URL ảnh chính không còn trong danh sách thì lấy ảnh đầu tiên.
     */
    private void ganAnhChinh(
            List<HinhAnhSanPham> anhs,
            String chinh
    ) {

        if (anhs.isEmpty()) {
            return;
        }

        String chinhHienTai = chinh;
        boolean coTrongDanhSach = chinhHienTai != null
                && anhs.stream().anyMatch(a -> chinhHienTai.equals(a.getDuongDan()));
        String duongDanChinh = coTrongDanhSach
                ? chinhHienTai
                : anhs.get(0).getDuongDan();

        boolean daGan = false;
        for (HinhAnhSanPham a : anhs) {
            boolean laChinh = !daGan && duongDanChinh.equals(a.getDuongDan());
            if (laChinh) {
                daGan = true;
            }
            a.setAnhChinh(laChinh);
            anhRepo.save(a);
        }
    }

    /**
     * Sinh SKU ổn định cho phiên bản, không phụ thuộc thứ tự khai báo.
     * Ví dụ: PN-013-6-128-DEN
     */
    private String generateSku(
            String maSanPham,
            int ram,
            int boNho,
            String mauSac
    ) {

        String so = maSanPham == null
                ? ""
                : maSanPham.replaceAll("\\D+", "");

        if (so.isEmpty()) {
            so = maSanPham == null || maSanPham.isBlank()
                    ? "MOI"
                    : slug(maSanPham);
        }

        return "PN-" + so + "-" + ram + "-" + boNho + "-" + slug(mauSac);
    }

    /**
     * Bỏ dấu tiếng Việt và ký tự đặc biệt để tạo phần màu trong SKU.
     */
    private String slug(String value) {

        if (value == null) {
            return "MAU";
        }

        String khongDau = Normalizer
                .normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'D')
                .replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .toUpperCase();

        return khongDau.isEmpty() ? "MAU" : khongDau;
    }

    /**
     * Đảm bảo SKU là duy nhất trên toàn hệ thống. Nếu trùng với phiên bản khác
     * thì thêm hậu tố -2, -3... (SKU của chính phiên bản đang sửa được giữ nguyên).
     */
    private String ensureUniqueSku(
            String base,
            String maPhienBan,
            Set<String> daDung
    ) {

        String candidate = base;
        int thuTu = 2;

        while (true) {

            PhienBanSanPham trung = pbRepo.findBySku(candidate).orElse(null);

            boolean trungTrongDb = trung != null
                    && !Objects.equals(trung.getMaPhienBan(), maPhienBan);

            boolean trungTrongPayload = daDung.contains(candidate.toLowerCase());

            if (!trungTrongDb && !trungTrongPayload) {
                return candidate;
            }

            candidate = base + "-" + thuTu++;
        }
    }

    private String comboKey(PhienBanSanPham p) {

        return comboKey(
                p.getRam(),
                p.getBoNho(),
                p.getMauSac()
        );
    }

    private String comboKey(
            int ram,
            int boNho,
            String mauSac
    ) {

        return ram
                + "|"
                + boNho
                + "|"
                + (mauSac == null ? "" : mauSac.trim().toLowerCase());
    }

    private boolean daDuocThamChieu(String maPhienBan) {

        if (maPhienBan == null) {
            return false;
        }

        return chiTietDonHangRepo.existsByPhienBanMaPhienBan(maPhienBan)
                || chiTietGioHangRepo.existsByPhienBanMaPhienBan(maPhienBan)
                || bienDongKhoRepo.existsByPhienBanMaPhienBan(maPhienBan);
    }
}