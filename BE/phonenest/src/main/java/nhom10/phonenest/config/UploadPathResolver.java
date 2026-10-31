package nhom10.phonenest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mot nguon duy nhat de resolve thu muc upload cho ca static resource handler
 * va service luu file. Duong dan tuong doi (mac dinh "uploads") duoc resolve
 * theo working directory cua backend, khong hardcode duong dan may.
 */
@Component
public class UploadPathResolver {

    private static final Logger log = LoggerFactory.getLogger(UploadPathResolver.class);

    private final Path root;

    public UploadPathResolver(@Value("${app.upload-dir:uploads}") String uploadDir) {
        Path configured = Path.of(uploadDir);
        if (!configured.isAbsolute()) {
            configured = Path.of(System.getProperty("user.dir")).resolve(configured);
        }
        this.root = configured.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Khong the tao thu muc upload: " + root, e);
        }
        log.info("Thu muc upload: {}", root);
    }

    public Path getRoot() {
        return root;
    }

    /**
     * Resolve mot ten file trong thu muc upload va chan path traversal.
     * Chi chap nhan ten file don (khong chua duong dan).
     */
    public Path resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Ten file khong hop le");
        }
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("Duong dan file khong hop le");
        }
        return target;
    }
}
