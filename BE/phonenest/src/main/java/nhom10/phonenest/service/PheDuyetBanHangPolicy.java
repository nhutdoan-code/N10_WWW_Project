package nhom10.phonenest.service;

import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Quy tac phe duyet ban hang dung chung (khong phu thuoc Spring) — Giai doan 2B.7B.
 *
 * <p>Mot bien the chi duoc ban khi DONG THOI:</p>
 * <ul>
 *   <li>bien the va san pham DANG_KINH_DOANH;</li>
 *   <li>trangThaiPheDuyet = APPROVED;</li>
 *   <li>co dau van tay phe duyet va dau van tay do KHOP du lieu ban hien tai
 *       (gia ban, gia niem yet, RAM, ROM, mau). Bat ky thay doi nao bo qua tang
 *       service (vd. sua SQL truc tiep) deu lam bien the khong ban duoc.</li>
 * </ul>
 * Ton kho la dieu kien rieng, kiem tra o tung luong mua.
 */
public final class PheDuyetBanHangPolicy {

    public static final int LY_DO_MIN = 10;
    public static final int LY_DO_MAX = 255;
    public static final int NGUON_MIN = 10;
    public static final int NGUON_MAX = 500;

    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHU_CAI = Pattern.compile("\\p{L}");

    private PheDuyetBanHangPolicy() {
    }

    // ------------------------------------------------------------------ dau van tay

    /** SHA-256 (hex, 64 ky tu) cua du lieu ban quan trong cua bien the. */
    public static String dauVanTay(PhienBanSanPham p) {
        String maSanPham = p.getSanPham() != null ? p.getSanPham().getMaSanPham() : "";
        String chuoi = String.join("|",
                "v1",
                nz(p.getMaPhienBan()),
                nz(maSanPham),
                String.valueOf(p.getRam()),
                String.valueOf(p.getBoNho()),
                chuanHoaMau(p.getMauSac()),
                tien(p.getGiaBan()),
                tien(p.getGiaNiemYet()));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(chuoi.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 khong kha dung", e);
        }
    }

    // ------------------------------------------------------------------ ma kiem tra dong thoi (2B.7C)

    /**
     * Ma kiem tra cho FORM SUA SAN PHAM (optimistic concurrency, khong can cot schema moi).
     * Gom du lieu ban (SKU, cau hinh, gia), trang thai kinh doanh va trang thai phe duyet.
     * 2B.7D: KHONG con gom ton kho — form san pham khong con sua ton kho cua bien the da ton tai
     * (chi qua nghiep vu Kho), nen don hang tru kho khong lam form cua Admin bi 409.
     */
    public static String maKiemTraSua(PhienBanSanPham p) {
        return bam("e2", nz(p.getMaPhienBan()), nz(p.getSku()), String.valueOf(p.getRam()), String.valueOf(p.getBoNho()),
                chuanHoaMau(p.getMauSac()), tien(p.getGiaBan()), tien(p.getGiaNiemYet()),
                String.valueOf(p.getTrangThai()),
                trangThaiPheDuyetKey(p));
    }

    /**
     * Ma kiem tra cho THAO TAC PHE DUYET/KHOA/MO KHOA. KHONG gom ton kho: don hang tru kho
     * trong luc Admin duyet khong lam phe duyet that bai (ton kho khong anh huong phe duyet).
     */
    public static String maKiemTraPheDuyet(PhienBanSanPham p) {
        String ttSp = p.getSanPham() != null ? String.valueOf(p.getSanPham().getTrangThai()) : "";
        return bam("a1", nz(p.getMaPhienBan()), String.valueOf(p.getRam()), String.valueOf(p.getBoNho()),
                chuanHoaMau(p.getMauSac()), tien(p.getGiaBan()), tien(p.getGiaNiemYet()),
                String.valueOf(p.getTrangThai()), ttSp, trangThaiPheDuyetKey(p));
    }

    /**
     * Ma kiem tra cap san pham (2B.7D): MOI truong Admin sua duoc o cap san pham — ten, mo ta, loai,
     * thuong hieu, trang thai, thong so ky thuat, bao hanh — cung tap bien the va tap anh (id, duong dan,
     * goc chup, anh chinh, mau, pham vi bien the). Bat ky thay doi nao cua Admin khac -> form cu bi 409.
     * Khong gom diem danh gia (thay doi theo danh gia cua khach, khong phai thao tac Admin).
     * Ma nay chi la phan "so sanh"; tinh dung dan dua tren viec so sanh + ghi xay ra trong CUNG giao dich
     * sau khi da khoa dong san pham (SELECT ... FOR UPDATE) — xem SanPhamService#khoaSanPham.
     */
    public static String maKiemTraSanPham(nhom10.phonenest.entity.SanPham s) {
        java.util.List<String> ds = s.getPhienBans() == null ? java.util.List.of()
                : s.getPhienBans().stream().map(PhienBanSanPham::getMaPhienBan).filter(Objects::nonNull).sorted().toList();
        java.util.List<String> anh = s.getHinhAnhs() == null ? java.util.List.of()
                : s.getHinhAnhs().stream()
                .map(a -> String.join("~", nz(a.getMaHinhAnh()), nz(a.getDuongDan()), nz(a.getGocChup()),
                        String.valueOf(a.isAnhChinh()), nz(a.getMauSac()),
                        a.getPhienBan() == null ? "" : nz(a.getPhienBan().getMaPhienBan())))
                .sorted().toList();
        return bam("s2", nz(s.getMaSanPham()), String.valueOf(s.getTrangThai()),
                nz(s.getTenMay()), nz(s.getMoTa()),
                s.getLoai() == null ? "" : nz(s.getLoai().getMaLoai()),
                s.getThuongHieu() == null ? "" : nz(s.getThuongHieu().getMaThuongHieu()),
                so(s.getKichThuocManHinh()), nz(s.getLoaiManHinh()), String.valueOf(s.getTanSoQuet()), nz(s.getChip()),
                nz(s.getCameraSau()), nz(s.getCameraTruoc()), String.valueOf(s.getPin()), String.valueOf(s.getCongSuatSac()),
                nz(s.getHeDieuHanh()), String.valueOf(s.isNfc()), String.valueOf(s.isHo5G()),
                String.valueOf(s.getTrongLuong()), String.valueOf(s.getThoiHanBaoHanhThang()),
                "pb:" + String.join(",", ds), "anh:" + String.join(",", anh));
    }

    private static String so(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static String trangThaiPheDuyetKey(PhienBanSanPham p) {
        return String.join("/", String.valueOf(p.getTrangThaiPheDuyet()), String.valueOf(p.getLyDoKhoa()),
                nz(p.getDauVanTayPheDuyet()),
                p.getNgayPheDuyet() == null ? "-"
                        : p.getNgayPheDuyet().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
    }

    private static String bam(String... phan) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(String.join("|", phan).getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 khong kha dung", e);
        }
    }

    public static boolean pheDuyetConHieuLuc(PhienBanSanPham p) {
        return p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.APPROVED
                && p.getDauVanTayPheDuyet() != null
                && p.getDauVanTayPheDuyet().equals(dauVanTay(p));
    }

    public static boolean dangKinhDoanh(PhienBanSanPham p) {
        return p.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH
                && p.getSanPham() != null
                && p.getSanPham().getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH;
    }

    // ------------------------------------------------------------------ gia cong bo (2B.7D)

    /**
     * Gia ban DUOC CONG BO cua mot bien the: chi khi bien the duoc phep ban (APPROVED + dau van tay
     * khop + dang kinh doanh). Nguoc lai tra null — KHONG bao gio tra gia seed, 0 hay gia gia.
     * Gia goc van nam nguyen trong database va chi Admin xem qua /api/admin/**.
     */
    public static BigDecimal giaCongBo(PhienBanSanPham p) {
        return duocPhepBan(p) ? p.getGiaBan() : null;
    }

    /** Gia niem yet duoc cong bo (cung dieu kien voi gia ban). */
    public static BigDecimal giaNiemYetCongBo(PhienBanSanPham p) {
        return duocPhepBan(p) ? p.getGiaNiemYet() : null;
    }

    /** Gia thap nhat duoc cong bo cua san pham: chi tinh tren cac bien the duoc phep ban; khong co -> null. */
    public static BigDecimal giaCongBo(nhom10.phonenest.entity.SanPham s) {
        if (s == null || s.getPhienBans() == null) {
            return null;
        }
        return s.getPhienBans().stream()
                .map(PheDuyetBanHangPolicy::giaCongBo)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    /** Duoc phep ban (chua xet ton kho). Thieu bat ky du lieu nao -> false. */
    public static boolean duocPhepBan(PhienBanSanPham p) {
        return p != null && dangKinhDoanh(p) && pheDuyetConHieuLuc(p);
    }

    /**
     * Nem AppException 400 voi thong diep ro rang neu bien the khong duoc ban.
     *
     * @param hauTo phan bo sung vao cuoi thong diep (vd. huong dan loai khoi gio), co the null
     */
    public static void kiemTraDuocBan(PhienBanSanPham p, String hauTo) {
        String ten = p.getSanPham() != null && p.getSanPham().getTenMay() != null
                ? p.getSanPham().getTenMay() : "Sản phẩm";
        String sau = hauTo == null ? "" : hauTo;
        if (!dangKinhDoanh(p)) {
            throw new AppException(HttpStatus.BAD_REQUEST, ten + " không còn kinh doanh" + sau);
        }
        TrangThaiPheDuyet tt = p.getTrangThaiPheDuyet();
        if (tt == TrangThaiPheDuyet.BLOCKED) {
            throw new AppException(HttpStatus.BAD_REQUEST, ten + " đã bị khóa bán do dữ liệu không hợp lệ" + sau);
        }
        if (tt != TrangThaiPheDuyet.APPROVED) {
            throw new AppException(HttpStatus.BAD_REQUEST, ten + " đang chờ phê duyệt giá, tạm ngừng nhận đơn" + sau);
        }
        if (!pheDuyetConHieuLuc(p)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    ten + " đang chờ xác minh lại dữ liệu phê duyệt, tạm ngừng nhận đơn" + sau);
        }
    }

    // ------------------------------------------------------------------ thay doi du lieu

    /** Tra ve mo ta cac truong anh huong phe duyet da thay doi, hoac null neu khong co. */
    public static String moTaThayDoi(BigDecimal giaBanCu, BigDecimal giaNiemYetCu, int ramCu, int boNhoCu, String mauCu,
                                     BigDecimal giaBanMoi, BigDecimal giaNiemYetMoi, int ramMoi, int boNhoMoi, String mauMoi) {
        StringBuilder sb = new StringBuilder();
        if (!bangTien(giaBanCu, giaBanMoi)) {
            sb.append("giá bán ").append(tien(giaBanCu)).append("→").append(tien(giaBanMoi)).append("; ");
        }
        if (!bangTien(giaNiemYetCu, giaNiemYetMoi)) {
            sb.append("giá niêm yết ").append(tien(giaNiemYetCu)).append("→").append(tien(giaNiemYetMoi)).append("; ");
        }
        if (ramCu != ramMoi) {
            sb.append("RAM ").append(ramCu).append("→").append(ramMoi).append("GB; ");
        }
        if (boNhoCu != boNhoMoi) {
            sb.append("ROM ").append(boNhoCu).append("→").append(boNhoMoi).append("GB; ");
        }
        if (!chuanHoaMau(mauCu).equals(chuanHoaMau(mauMoi))) {
            sb.append("màu ").append(nz(mauCu)).append("→").append(nz(mauMoi)).append("; ");
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    // ------------------------------------------------------------------ validate van ban

    /** Kiem tra ly do / can cu. Nem 400 neu khong dat. */
    public static String kiemTraVanBan(String giaTri, String tenTruong, int min, int max) {
        if (giaTri == null || giaTri.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, tenTruong + " không được để trống");
        }
        String s = giaTri.trim();
        if (s.length() < min || s.length() > max) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    tenTruong + " phải từ " + min + " đến " + max + " ký tự");
        }
        String boUrl = URL.matcher(s).replaceAll(" ").trim();
        if (boUrl.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    tenTruong + " không được chỉ là đường dẫn URL; cần mô tả nội dung thẩm định");
        }
        long soTu = boUrl.split("\\s+").length;
        long kyTuKhacNhau = boUrl.toLowerCase().codePoints().filter(Character::isLetterOrDigit).distinct().count();
        if (!CHU_CAI.matcher(boUrl).find() || soTu < 3 || kyTuKhacNhau < 5) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    tenTruong + " phải là mô tả có nghĩa (tối thiểu 3 từ, có chữ cái), không chỉ là chuỗi ký tự lặp hoặc số");
        }
        return s;
    }

    // ------------------------------------------------------------------ tien ich

    public static boolean bangTien(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    public static String chuanHoaMau(String mau) {
        return mau == null ? "" : mau.trim();
    }

    private static String tien(BigDecimal v) {
        return v == null ? "-" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return Objects.toString(s, "");
    }
}
