package nhom10.phonenest.config.schema;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kiem tra tuong thich schema KHI KHOI DONG (fail-fast, 2B.7D).
 *
 * <p>Chay trong giai doan khoi tao bean ({@link InitializingBean}), tuc la TRUOC khi web server mo cong:
 * schema cu/thieu -> ngoai le -> ung dung dung lai, khong phuc vu request nao. Chi doc
 * (ket noi read-only, chi SELECT information_schema + dem dong); khong chay migration, khong sua schema.</p>
 *
 * <p>Chay doc lap (khong mo web server):
 * {@code java -jar phonenest.jar --spring.main.web-application-type=none --app.schema-check.only=true}
 * -> in "SCHEMA_CHECK OK" va thoat 0, hoac thoat voi loi liet ke tung thanh phan khong tuong thich.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.schema-check.enabled", havingValue = "true", matchIfMissing = true)
public class SchemaCompatibilityVerifier implements InitializingBean, ApplicationRunner {

    private final DataSource dataSource;
    private final ApplicationContext context;
    private final boolean chiKiemTra;

    public SchemaCompatibilityVerifier(DataSource dataSource, ApplicationContext context,
                                       @Value("${app.schema-check.only:false}") boolean chiKiemTra) {
        this.dataSource = dataSource;
        this.context = context;
        this.chiKiemTra = chiKiemTra;
    }

    @Override
    public void afterPropertiesSet() {
        SchemaCompatibilityChecker.AnhChup anh;
        String db;
        try (Connection con = dataSource.getConnection()) {
            con.setReadOnly(true);
            db = mot(con, "SELECT DATABASE()");
            anh = docAnhChup(con, db);
        } catch (SQLException e) {
            throw new IllegalStateException("SCHEMA_CHECK FAILED: không đọc được metadata database: " + e.getMessage(), e);
        }
        List<String> loi = SchemaCompatibilityChecker.kiemTra(anh);
        if (!loi.isEmpty()) {
            String msg = "SCHEMA_CHECK FAILED: database '" + db + "' không tương thích với phiên bản code hiện tại. "
                    + "Ứng dụng KHÔNG khởi động. Không tự chạy migration / không tự sửa schema.\n - "
                    + String.join("\n - ", loi);
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.info("SCHEMA_CHECK OK: database '{}' tương thích (M15 + M16, kiểu dữ liệu, enum, migration log).", db);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (chiKiemTra) {
            log.info("SCHEMA_CHECK OK — chế độ chỉ kiểm tra, thoát.");
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }

    // ------------------------------------------------------------------ doc metadata (chi SELECT)

    static SchemaCompatibilityChecker.AnhChup docAnhChup(Connection con, String db) throws SQLException {
        Set<String> bang = new HashSet<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT LOWER(TABLE_NAME) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) bang.add(rs.getString(1));
            }
        }
        Map<String, SchemaCompatibilityChecker.Cot> cot = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND LOWER(TABLE_NAME) = 'phienbansanpham'")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cot.put(rs.getString(1), new SchemaCompatibilityChecker.Cot(rs.getString(2), "YES".equals(rs.getString(3))));
                }
            }
        }
        long ttKhongHopLe = 0;
        long lyDoKhongHopLe = 0;
        if (cot.containsKey("trangThaiPheDuyet")) {
            ttKhongHopLe = dem(con, "SELECT COUNT(*) FROM phienbansanpham WHERE trangThaiPheDuyet IS NULL OR trangThaiPheDuyet = ''");
        }
        if (cot.containsKey("lyDoKhoa")) {
            lyDoKhongHopLe = dem(con, "SELECT COUNT(*) FROM phienbansanpham WHERE lyDoKhoa = ''");
        }
        Map<String, String> log = new HashMap<>();
        if (bang.contains("pn_migration_log")) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT l.migration_id, l.trang_thai FROM pn_migration_log l "
                            + "WHERE l.log_id = (SELECT MAX(x.log_id) FROM pn_migration_log x WHERE x.migration_id = l.migration_id)");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) log.put(rs.getString(1), rs.getString(2));
            }
        }
        return new SchemaCompatibilityChecker.AnhChup(bang, cot, log, ttKhongHopLe, lyDoKhongHopLe);
    }

    private static long dem(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static String mot(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
