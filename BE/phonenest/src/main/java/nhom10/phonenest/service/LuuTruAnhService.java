package nhom10.phonenest.service;

import nhom10.phonenest.config.UploadPathResolver;
import nhom10.phonenest.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LuuTruAnhService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> DUOI_ANH = Set.of("jpg", "jpeg", "png", "webp");

    private final UploadPathResolver uploadPathResolver;

    public LuuTruAnhService(UploadPathResolver uploadPathResolver) {
        this.uploadPathResolver = uploadPathResolver;
    }

    public String luu(MultipartFile file) { return luu(file, ""); }

    private String prefix(String user) {
        return UUID.nameUUIDFromBytes(user.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "_";
    }
    public String luuHauMai(MultipartFile file, String user) { return luu(file, prefix(user)); }
    public boolean anhHauMaiHopLe(String url, String user) {
        if (url == null || !url.startsWith("/uploads/" + prefix(user))) return false;
        String name = url.substring("/uploads/".length());
        if (!name.substring(prefix(user).length()).matches("[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)")) return false;
        Path path = uploadPathResolver.resolve(name);
        try { return Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) && Files.size(path) > 0 && Files.size(path) <= MAX_BYTES; }
        catch (IOException e) { return false; }
    }
    private String luu(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Vui lòng chọn một file ảnh");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh không được vượt quá 5 MB");
        }

        String duoi = layDuoi(file.getOriginalFilename());
        if (!DUOI_ANH.contains(duoi)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP");
        }

        String loai = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!loai.startsWith("image/")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận file ảnh");
        }

        if (!hopLeVoiDuoi(duoi, loai, file)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Nội dung file không phải ảnh hợp lệ");
        }

        // Khong dung original filename lam duong dan file tren he thong.
        String tenMoi = prefix + UUID.randomUUID() + "." + duoi;
        Path dich = uploadPathResolver.resolve(tenMoi);

        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(uploadPathResolver.getRoot());
            Files.copy(in, dich, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh, vui lòng thử lại");
        }

        return "/uploads/" + tenMoi;
    }

    private String layDuoi(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        String ten = originalFilename.replace('\\', '/');
        int slash = ten.lastIndexOf('/');
        if (slash >= 0) {
            ten = ten.substring(slash + 1);
        }
        int dot = ten.lastIndexOf('.');
        if (dot < 0 || dot == ten.length() - 1) {
            return "";
        }
        return ten.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean hopLeVoiDuoi(String duoi, String loai, MultipartFile file) {
        boolean dungLoai = switch (duoi) {
            case "jpg", "jpeg" -> loai.equals("image/jpeg") || loai.equals("image/jpg");
            case "png" -> loai.equals("image/png");
            case "webp" -> loai.equals("image/webp");
            default -> false;
        };
        return dungLoai && coChuKyAnh(file, duoi);
    }

    /**
     * Kiem tra magic bytes de tranh file gia mao chi doi duoi/content-type.
     */
    private boolean coChuKyAnh(MultipartFile file, String duoi) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            return switch (duoi) {
                case "jpg", "jpeg" -> head.length >= 3
                        && (head[0] & 0xFF) == 0xFF
                        && (head[1] & 0xFF) == 0xD8
                        && (head[2] & 0xFF) == 0xFF;
                case "png" -> head.length >= 8
                        && (head[0] & 0xFF) == 0x89
                        && head[1] == 'P'
                        && head[2] == 'N'
                        && head[3] == 'G'
                        && (head[4] & 0xFF) == 0x0D
                        && (head[5] & 0xFF) == 0x0A
                        && (head[6] & 0xFF) == 0x1A
                        && (head[7] & 0xFF) == 0x0A;
                case "webp" -> head.length >= 12
                        && head[0] == 'R'
                        && head[1] == 'I'
                        && head[2] == 'F'
                        && head[3] == 'F'
                        && head[8] == 'W'
                        && head[9] == 'E'
                        && head[10] == 'B'
                        && head[11] == 'P';
                default -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }
}
