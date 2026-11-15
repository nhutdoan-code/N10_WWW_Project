package nhom10.phonenest.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** 2B.7E: logic thuan (file that trong thu muc tam, khong DB) cua kiem ke upload mo coi. KHONG XOA. */
class UploadOrphanAuditServiceTest {

    private static Path file(Path dir, String ten, Duration tuoi, Instant bayGio) throws Exception {
        Path p = dir.resolve(ten);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "x".repeat(10));
        Files.setLastModifiedTime(p, FileTime.from(bayGio.minus(tuoi)));
        return p;
    }

    @Test
    void phanLoaiDungVaKhongXoaGi(@TempDir Path tmp) throws Exception {
        Instant now = Instant.now();
        Path goc = Files.createDirectories(tmp.resolve("uploads"));
        Path ngoai = file(tmp, "ngoai-thu-muc.webp", Duration.ofDays(30), now);
        Path dangDung = file(goc, "dang-dung.webp", Duration.ofDays(30), now);
        Path lichSu = file(goc, "anh-don-hang-cu.webp", Duration.ofDays(400), now);
        Path moUpload = file(goc, "vua-upload.webp", Duration.ofMinutes(10), now);
        Path moCoi = file(goc, "mo-coi.webp", Duration.ofDays(3), now);
        Path con = file(goc, "sub/mo-coi-con.webp", Duration.ofDays(3), now);
        Path readme = file(goc, "README.txt", Duration.ofDays(300), now);
        Map<String, Integer> theoCot = new TreeMap<>(Map.of("hinhanhsanpham.duongDan", 1, "chitietdonhang.hinhAnh", 1));
        UploadOrphanAuditService.BaoCao r = UploadOrphanAuditService.kiemKe(goc,
                Set.of("dang-dung.webp", "anh-don-hang-cu.webp", "da-xoa-tu-truoc.webp"), theoCot, 24, now);
        assertThat(r.chiKiemKe()).isTrue();
        assertThat(r.tongFile()).isEqualTo(6);
        assertThat(r.khongPhaiAnh()).as("README khong bao gio la ung vien").isEqualTo(1);
        assertThat(r.duocThamChieu()).isEqualTo(2);
        assertThat(r.chuaDuThoiGianCho()).isEqualTo(1);
        assertThat(r.ungVien()).extracting(UploadOrphanAuditService.FileUngVien::duongDan)
                .containsExactly("mo-coi.webp", "sub/mo-coi-con.webp");
        assertThat(r.thamChieuKhongCoFile()).isEqualTo(1);
        assertThat(r.bamDanhSachUngVien()).hasSize(64);
        for (Path p : new Path[]{ngoai, dangDung, lichSu, moUpload, moCoi, con, readme}) {
            assertThat(Files.exists(p)).as("dry-run khong duoc xoa " + p).isTrue();
        }
        // Cung du lieu -> cung ma bam (de buoc xoa sau nay kiem tra lai dung tap da xem)
        assertThat(UploadOrphanAuditService.kiemKe(goc, Set.of("dang-dung.webp", "anh-don-hang-cu.webp"), theoCot, 24, now)
                .bamDanhSachUngVien()).isEqualTo(r.bamDanhSachUngVien());
    }
}
