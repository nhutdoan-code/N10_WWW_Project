package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.config.UploadPathResolver;
import nhom10.phonenest.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * KIEM KE file upload mo coi — CHI DRY-RUN, KHONG XOA (Giai doan 2B.7E).
 *
 * <ul>
 *   <li>Chi quet ben trong thu muc upload da cau hinh (UploadPathResolver); khong theo symlink; file nao
 *       (sau khi phan giai) nam ngoai thu muc goc deu bi bo qua.</li>
 *   <li>"Duoc tham chieu" = xuat hien o BAT KY cot van ban nao trong database (ky ca anh trong don hang lich su,
 *       anh doi tra, anh danh gia, anh dai dien, du lieu truoc/sau trong nhat ky audit). Khong dung danh sach
 *       cot co dinh de khong bo sot anh lich su.</li>
 *   <li>File moi hon thoi gian cho (mac dinh 24 gio, toi thieu 1 gio) KHONG bao gio la ung vien: co the la anh
 *       vua upload ma giao dich luu san pham chua hoan tat.</li>
 *   <li>Bao cao co ma, thoi diem va ma bam danh sach ung vien de mot buoc xoa (neu duoc phe duyet sau nay)
 *       kiem tra lai dung tap da duoc xem.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadOrphanAuditService {

    public static final int GIO_CHO_MAC_DINH = 24;
    /** Chi file anh moi co the la ung vien; file khac (README, cau hinh...) chi duoc dem, khong bao gio de xuat. */
    private static final Set<String> DUOI_ANH = Set.of("jpg", "jpeg", "png", "webp", "gif", "svg");
    private static final Pattern THAM_CHIEU = Pattern.compile("/uploads/([^\"'\\s?#)<>,]+)");

    private final UploadPathResolver uploadPathResolver;
    private final JdbcTemplate jdbc;

    public record FileUngVien(String duongDan, long kichThuoc, long tuoiGio) {
    }

    public record BaoCao(
            String maBaoCao,
            LocalDateTime thoiDiem,
            String thuMucGoc,
            int gioCho,
            boolean chiKiemKe,
            int tongFile,
            int duocThamChieu,
            int chuaDuThoiGianCho,
            int boQuaSymlinkHoacNgoaiThuMuc,
            int khongPhaiAnh,
            int ungVienMoCoi,
            long tongDungLuongUngVien,
            String bamDanhSachUngVien,
            Map<String, Integer> thamChieuTheoCot,
            int thamChieuKhongCoFile,
            List<FileUngVien> ungVien) {
    }

    @Transactional(readOnly = true)
    public BaoCao kiemKe(Integer gioChoYeuCau) {
        int gioCho = gioChoYeuCau == null ? GIO_CHO_MAC_DINH : gioChoYeuCau;
        if (gioCho < 1) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Thời gian chờ tối thiểu là 1 giờ");
        }
        Map<String, Integer> theoCot = new TreeMap<>();
        Set<String> thamChieu = thamChieuTrongDatabase(theoCot);
        return kiemKe(uploadPathResolver.getRoot(), thamChieu, theoCot, gioCho, Instant.now());
    }

    /** Phan loi thuan (khong DB) — dung cho unit test. */
    static BaoCao kiemKe(Path goc, Set<String> thamChieu, Map<String, Integer> theoCot, int gioCho, Instant bayGio) {
        Path root;
        try {
            root = goc.toRealPath();
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Không đọc được thư mục upload");
        }
        Instant moc = bayGio.minus(Duration.ofHours(gioCho));
        int tong = 0, daThamChieu = 0, chuaDu = 0, boQua = 0, khongPhaiAnh = 0;
        long dungLuong = 0;
        List<FileUngVien> ungVien = new ArrayList<>();
        Set<String> coFile = new HashSet<>();
        try (Stream<Path> s = Files.walk(root)) { // khong FOLLOW_LINKS
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) continue;
                if (Files.isSymbolicLink(p) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                    boQua++;
                    continue;
                }
                Path that = p.toRealPath();
                if (!that.startsWith(root)) {
                    boQua++;
                    continue;
                }
                tong++;
                String rel = root.relativize(that).toString().replace('\\', '/');
                coFile.add(rel);
                if (thamChieu.contains(rel)) {
                    daThamChieu++;
                    continue;
                }
                String ten = that.getFileName().toString();
                String duoi = ten.contains(".") ? ten.substring(ten.lastIndexOf('.') + 1).toLowerCase() : "";
                if (!DUOI_ANH.contains(duoi)) {
                    khongPhaiAnh++;
                    continue;
                }
                Instant sua = Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toInstant();
                if (sua.isAfter(moc)) {
                    chuaDu++;
                    continue;
                }
                long kt = Files.size(p);
                dungLuong += kt;
                ungVien.add(new FileUngVien(rel, kt, Duration.between(sua, bayGio).toHours()));
            }
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi khi quét thư mục upload");
        }
        ungVien.sort((a, b) -> a.duongDan().compareTo(b.duongDan()));
        int khongCoFile = (int) thamChieu.stream().filter(r -> !coFile.contains(r)).count();
        String bam = bam(ungVien);
        String ma = "UPA-" + bam.substring(0, 12);
        log.info("KIEM KE UPLOAD (dry-run) {}: thu muc={}, tong={}, duoc tham chieu={}, chua du thoi gian cho={}, "
                        + "bo qua={}, ung vien mo coi={} ({} byte), tham chieu khong co file={}. KHONG XOA FILE NAO.",
                ma, root, tong, daThamChieu, chuaDu, boQua, ungVien.size(), dungLuong, khongCoFile);
        return new BaoCao(ma, LocalDateTime.ofInstant(bayGio, java.time.ZoneId.systemDefault()), root.toString(), gioCho, true,
                tong, daThamChieu, chuaDu, boQua, khongPhaiAnh, ungVien.size(), dungLuong, bam, theoCot, khongCoFile, ungVien);
    }

    /** Moi gia tri '/uploads/...' trong MOI cot van ban cua database hien tai (chi doc). */
    private Set<String> thamChieuTrongDatabase(Map<String, Integer> theoCot) {
        Set<String> kq = new HashSet<>();
        List<Map<String, Object>> cot = jdbc.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND DATA_TYPE IN ('char','varchar','tinytext','text','mediumtext','longtext')");
        for (Map<String, Object> c : cot) {
            String t = (String) c.get("TABLE_NAME");
            String col = (String) c.get("COLUMN_NAME");
            if (!t.matches("[A-Za-z0-9_]+") || !col.matches("[A-Za-z0-9_]+")) continue;
            List<String> vals = jdbc.queryForList("SELECT `" + col + "` FROM `" + t + "` WHERE `" + col + "` LIKE '%/uploads/%'", String.class);
            int n = 0;
            for (String v : vals) {
                Matcher m = THAM_CHIEU.matcher(v == null ? "" : v);
                while (m.find()) {
                    kq.add(m.group(1));
                    n++;
                }
            }
            if (n > 0) theoCot.put(t.toLowerCase() + "." + col, n);
        }
        return kq;
    }

    private static String bam(List<FileUngVien> ds) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (FileUngVien f : ds) md.update((f.duongDan() + "|" + f.kichThuoc() + "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
