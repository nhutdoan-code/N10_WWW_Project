package nhom10.phonenest.service;

import nhom10.phonenest.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class LuuTruAnhService {

    private static final Set<String> DUOI_ANH = Set.of("jpg", "jpeg", "png", "webp");

    private final Path thuMuc;

    public LuuTruAnhService(@Value("${app.upload-dir}") String dir) {
        this.thuMuc = Path.of(dir).toAbsolutePath();
    }

    public String luu(MultipartFile file) {
        String ten = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String duoi = ten.contains(".") ? ten.substring(ten.lastIndexOf('.') + 1).toLowerCase() : "";
        String loai = file.getContentType() == null ? "" : file.getContentType();
        if (file.isEmpty() || !DUOI_ANH.contains(duoi) || !loai.startsWith("image/")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP");
        }
        String tenMoi = UUID.randomUUID() + "." + duoi;
        try {
            Files.createDirectories(thuMuc);
            file.transferTo(thuMuc.resolve(tenMoi));
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh, vui lòng thử lại");
        }
        return "/uploads/" + tenMoi;
    }
}
