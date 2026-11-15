package nhom10.phonenest.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UploadPathResolver uploadPathResolver;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadPathResolver.getRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        // Ảnh Admin tải lên (dữ liệu runtime, không commit): thư mục app.upload-dir.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);

        // Ảnh gốc của catalog (commit trong repo, đóng gói cùng ứng dụng): src/main/resources/catalog.
        // Tách riêng khỏi /uploads để Admin thêm/xóa ảnh mới không ảnh hưởng dữ liệu gốc.
        registry.addResourceHandler("/catalog/**")
                .addResourceLocations("classpath:/catalog/")
                .setCachePeriod(86400);
    }
}
