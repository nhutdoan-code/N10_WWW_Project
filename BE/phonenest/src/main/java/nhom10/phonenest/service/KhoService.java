package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.*;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.*;
import nhom10.phonenest.util.IdGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class KhoService {

    public static final int NGUONG_SAP_HET = 10;
    public static final int MAX_IMPORT_QUANTITY = 100_000;
    public static final int MAX_TOTAL_STOCK = 1_000_000;

    private final SanPhamRepository spRepo;
    private final PhienBanSanPhamRepository pbRepo;
    private final BienDongKhoRepository bienDongRepo;
    private final TaiKhoanRepository taiKhoanRepo;
    private final NhatKyHoatDongService nhatKyHoatDongService;

    @Transactional(readOnly = true)
    public List<SanPhamDto> list(String loc) {
        return list(null, loc);
    }

    @Transactional(readOnly = true)
    public KhoPageDto page(String q, String loc, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Tham số phân trang không hợp lệ"
            );
        }

        List<KhoDongDto> all = pbRepo.findAll().stream()
                .filter(p -> matches(
                        p,
                        p.getSanPham(),
                        q == null ? "" : q.trim().toLowerCase(Locale.ROOT),
                        loc
                ))
                .sorted(
                        Comparator.comparingInt(PhienBanSanPham::getSoLuongTon)
                                .thenComparing(PhienBanSanPham::getSku)
                )
                .map(p -> new KhoDongDto(
                        p.getSanPham().getMaSanPham(),
                        p.getSanPham().getTenMay(),
                        p.getMaPhienBan(),
                        p.getSku(),
                        p.getRam(),
                        p.getBoNho(),
                        p.getMauSac(),
                        p.getGiaBan(),
                        p.getSoLuongTon(),
                        p.getTrangThai()
                ))
                .toList();

        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        return new KhoPageDto(
                all.subList(from, to),
                page,
                size,
                total
        );
    }

    @Transactional(readOnly = true)
    public List<SanPhamDto> list(String q, String loc) {
        String query = q == null
                ? ""
                : q.trim().toLowerCase(Locale.ROOT);

        return spRepo.findAll().stream()
                .filter(s -> s.getPhienBans() != null)
                .filter(s -> s.getPhienBans().stream()
                        .anyMatch(p -> matches(p, s, query, loc)))
                .sorted(Comparator.comparingInt(SanPham::getSoLuongTon))
                .map(SanPhamMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public KhoThongKeDto thongKe() {
        long total = 0;
        long available = 0;
        long low = 0;
        long empty = 0;

        for (PhienBanSanPham p : pbRepo.findAll()) {
            total++;

            if (p.getSoLuongTon() == 0) {
                empty++;
            } else if (p.getSoLuongTon() <= NGUONG_SAP_HET) {
                low++;
            } else {
                available++;
            }
        }

        return new KhoThongKeDto(
                total,
                available,
                low,
                empty
        );
    }

    public SanPhamDto nhapKho(
            String ma,
            int soLuong,
            String ghiChu,
            String actorId) {

        requirePositive(soLuong);

        PhienBanSanPham p = lockVariant(ma);
        ensureSellable(p);

        int before = p.getSoLuongTon();
        long afterLong = (long) before + soLuong;

        if (afterLong > MAX_TOTAL_STOCK) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Tổng tồn kho không được vượt quá " + MAX_TOTAL_STOCK
            );
        }

        int after = (int) afterLong;

        p.setSoLuongTon(after);

        ghiBienDong(
                p,
                LoaiBienDongKho.NHAP_KHO,
                soLuong,
                before,
                after,
                null,
                null,
                actorId,
                ghiChu
        );

        return SanPhamMapper.toDto(p.getSanPham());
    }

    public int nhapKhoHangLoat(
            KhoNhapHangLoatDto request,
            String actorId) {

        Map<String, Integer> items = new HashMap<>();

        for (KhoNhapHangLoatDto.DongNhap item : request.items()) {
            String sku = item.sku().trim();

            if (sku.isEmpty()
                    || item.soLuong() <= 0
                    || items.put(sku, item.soLuong()) != null) {

                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "File có SKU trống, số lượng không hợp lệ hoặc SKU bị trùng: " + sku
                );
            }
        }

        items.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {

                    PhienBanSanPham variant = pbRepo
                            .findBySku(entry.getKey())
                            .orElseThrow(() ->
                                    new AppException(
                                            HttpStatus.NOT_FOUND,
                                            "Không tìm thấy SKU: " + entry.getKey()
                                    )
                            );

                    nhapKho(
                            variant.getMaPhienBan(),
                            entry.getValue(),
                            request.ghiChu(),
                            actorId
                    );
                });

        return items.size();
    }

    /**
     * Compatibility overload for existing service-level tests and integrations.
     * Administrative HTTP calls must use the audited overload above.
     */
    @Deprecated
    public SanPhamDto nhapKho(String ma, int soLuong) {
        requirePositive(soLuong);

        PhienBanSanPham p = pbRepo.findById(ma)
                .orElseThrow(() ->
                        new AppException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy phiên bản"
                        )
                );

        int before = p.getSoLuongTon();
        long afterLong = (long) before + soLuong;

        if (afterLong > MAX_TOTAL_STOCK) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Tổng tồn kho không được vượt quá " + MAX_TOTAL_STOCK
            );
        }

        p.setSoLuongTon((int) afterLong);

        ghiAudit(
                ma,
                before,
                p.getSoLuongTon(),
                HanhDongAudit.STOCK_IMPORT
        );

        return SanPhamMapper.toDto(p.getSanPham());
    }

    @Deprecated
    public SanPhamDto datTon(String ma, int soLuong) {
        if (soLuong < 0 || soLuong > MAX_TOTAL_STOCK) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Tồn kho phải từ 0 đến " + MAX_TOTAL_STOCK
            );
        }

        PhienBanSanPham p = pbRepo.findById(ma)
                .orElseThrow(() ->
                        new AppException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy phiên bản"
                        )
                );

        int before = p.getSoLuongTon();

        p.setSoLuongTon(soLuong);

        ghiAudit(
                ma,
                before,
                soLuong,
                HanhDongAudit.STOCK_ADJUSTMENT
        );

        return SanPhamMapper.toDto(p.getSanPham());
    }

    public SanPhamDto dieuChinh(
            String ma,
            int soLuongThucTe,
            String lyDo,
            String actorId) {

        if (soLuongThucTe < 0
                || lyDo == null
                || lyDo.isBlank()) {

            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng kiểm kê và lý do điều chỉnh không hợp lệ"
            );
        }

        if (soLuongThucTe > MAX_TOTAL_STOCK) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Tồn kho phải từ 0 đến " + MAX_TOTAL_STOCK
            );
        }

        PhienBanSanPham p = lockVariant(ma);

        int before = p.getSoLuongTon();
        int delta = soLuongThucTe - before;

        if (delta == 0) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Số lượng kiểm kê không tạo ra chênh lệch"
            );
        }

        p.setSoLuongTon(soLuongThucTe);

        ghiBienDong(
                p,
                LoaiBienDongKho.DIEU_CHINH,
                delta,
                before,
                soLuongThucTe,
                null,
                null,
                actorId,
                lyDo
        );

        return SanPhamMapper.toDto(p.getSanPham());
    }

    public List<BienDongKhoDto> lichSu(String maPhienBan) {
        if (!pbRepo.existsById(maPhienBan)) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy phiên bản"
            );
        }

        return bienDongRepo
                .findByPhienBanMaPhienBanOrderByThoiGianDesc(maPhienBan)
                .stream()
                .map(BienDongKhoDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BienDongKhoDto> lichSuTatCa() {
        return bienDongRepo
                .findAllByOrderByThoiGianDesc()
                .stream()
                .map(BienDongKhoDto::from)
                .toList();
    }

    public void thayDoiTon(
            PhienBanSanPham variant,
            int delta,
            LoaiBienDongKho type,
            DonHang order,
            String actorId,
            String ghiChu) {

        PhienBanSanPham p = lockVariant(
                variant.getMaPhienBan()
        );

        int before = p.getSoLuongTon();
        long afterLong = (long) before + delta;

        if (afterLong < 0) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Tồn kho không đủ"
            );
        }

        if (afterLong > MAX_TOTAL_STOCK) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Tồn kho phải từ 0 đến " + MAX_TOTAL_STOCK
            );
        }

        int after = (int) afterLong;

        p.setSoLuongTon(after);

        ghiBienDong(
                p,
                type,
                Math.abs(delta),
                before,
                after,
                order,
                null,
                actorId,
                ghiChu
        );

        variant.setSoLuongTon(after);
    }

    private boolean matches(
            PhienBanSanPham p,
            SanPham s,
            String q,
            String loc) {

        int stock = p.getSoLuongTon();

        boolean status = switch (loc == null ? "" : loc) {
            case "het" ->
                    stock == 0;

            case "sap-het" ->
                    stock > 0
                            && stock <= NGUONG_SAP_HET;

            case "con-hang" ->
                    stock > NGUONG_SAP_HET;

            default ->
                    true;
        };

        boolean search =
                q.isBlank()
                        || p.getSku()
                        .toLowerCase(Locale.ROOT)
                        .contains(q)

                        || s.getMaSanPham()
                        .toLowerCase(Locale.ROOT)
                        .contains(q)

                        || s.getTenMay()
                        .toLowerCase(Locale.ROOT)
                        .contains(q);

        return status && search;
    }

    private PhienBanSanPham lockVariant(String ma) {
        return pbRepo.findByIdForUpdate(ma)
                .orElseThrow(() ->
                        new AppException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy phiên bản"
                        )
                );
    }

    private void ensureSellable(PhienBanSanPham p) {
        if (p.getTrangThai()
                != TrangThaiSanPham.DANG_KINH_DOANH

                || p.getSanPham().getTrangThai()
                != TrangThaiSanPham.DANG_KINH_DOANH) {

            throw new AppException(
                    HttpStatus.CONFLICT,
                    "Phiên bản không còn kinh doanh"
            );
        }
    }

    private void requirePositive(int value) {
        if (value <= 0) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng phải lớn hơn 0"
            );
        }

        if (value > MAX_IMPORT_QUANTITY) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "Số lượng nhập mỗi lần không vượt quá " + MAX_IMPORT_QUANTITY
            );
        }
    }

    private void ghiAudit(
            String ma,
            int before,
            int after,
            HanhDongAudit action) {

        Map<String, Object> oldValue =
                new LinkedHashMap<>();

        oldValue.put(
                "soLuongTon",
                before
        );

        Map<String, Object> newValue =
                new LinkedHashMap<>();

        newValue.put(
                "soLuongTon",
                after
        );

        nhatKyHoatDongService.ghi(
                action,
                LoaiDoiTuongAudit.INVENTORY,
                ma,
                oldValue,
                newValue
        );
    }

    private void ghiBienDong(
            PhienBanSanPham p,
            LoaiBienDongKho type,
            int quantity,
            int before,
            int after,
            DonHang order,
            YeuCauDoiTra request,
            String actorId,
            String note) {

        BienDongKho b = new BienDongKho();

        b.setMaBienDong(
                IdGenerator.next("BDK")
        );

        b.setPhienBan(p);
        b.setLoaiBienDong(type);
        b.setSoLuong(quantity);
        b.setTonTruoc(before);
        b.setTonSau(after);
        b.setDonHang(order);
        b.setYeuCau(request);
        b.setThoiGian(LocalDateTime.now());
        b.setGhiChu(note);

        if (actorId != null) {
            b.setNguoiThucHien(
                    taiKhoanRepo
                            .findById(actorId)
                            .orElse(null)
            );
        }

        bienDongRepo.save(b);
    }

    public record BienDongKhoDto(
            String maBienDong,
            String sku,
            String tenSanPham,
            LoaiBienDongKho loaiBienDong,
            int soLuong,
            int tonTruoc,
            int tonSau,
            String maDonHang,
            String nguoiThucHien,
            LocalDateTime thoiGian,
            String ghiChu) {

        static BienDongKhoDto from(
                BienDongKho b) {

            return new BienDongKhoDto(
                    b.getMaBienDong(),
                    b.getPhienBan().getSku(),
                    b.getPhienBan()
                            .getSanPham()
                            .getTenMay(),
                    b.getLoaiBienDong(),
                    b.getSoLuong(),
                    b.getTonTruoc(),
                    b.getTonSau(),

                    b.getDonHang() == null
                            ? null
                            : b.getDonHang()
                              .getMaDonHang(),

                    b.getNguoiThucHien() == null
                            ? null
                            : b.getNguoiThucHien()
                              .getMaTk(),

                    b.getThoiGian(),
                    b.getGhiChu()
            );
        }
    }

    public record KhoPageDto(
            List<KhoDongDto> content,
            int page,
            int size,
            int totalElements,
            int totalPages) {

        public KhoPageDto(
                List<KhoDongDto> content,
                int page,
                int size,
                int totalElements) {

            this(
                    content,
                    page,
                    size,
                    totalElements,

                    totalElements == 0
                            ? 0
                            : (totalElements + size - 1) / size
            );
        }
    }
}