package nhom10.phonenest.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chot an toan cho integration test, chay TRUOC khi Spring tao bat ky bean nao
 * (truoc ca DataSource, job khoi dong, vector store...).
 *
 * <p>Tu choi neu JDBC URL khong tro vao mot clone dung mot lan:</p>
 * <ul>
 *   <li>host phai la 127.0.0.1 hoac localhost;</li>
 *   <li>ten database phai khop {@code ^(claude_2b7[a-z]_|phonenest_it_)[a-z0-9_]+$};</li>
 *   <li>khong bao gio la {@code bandienthoai}, {@code bandienthoai_test}, {@code bandienthoai_clean_acceptance}...</li>
 * </ul>
 * Dung: {@code @ContextConfiguration(initializers = ItDatabaseGuard.class)}.
 */
public class ItDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final Pattern URL = Pattern.compile(
            "^jdbc:(?:mariadb|mysql)://([^:/?]+)(?::\\d+)?/([^?;]+)(?:[?;].*)?$", Pattern.CASE_INSENSITIVE);
    static final Pattern TEN_DB_CHO_PHEP = Pattern.compile("^(claude_2b7[a-z]_|phonenest_it_)[a-z0-9_]+$");

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        kiemTra(ctx.getEnvironment().getProperty("spring.datasource.url"));
        // 2B.7E: canh bao (khong chan, de khong doi quy trinh cua nhom) khi test chay bang root.
        if ("root".equalsIgnoreCase(ctx.getEnvironment().getProperty("spring.datasource.username"))) {
            System.err.println("[IT GUARD] CANH BAO: integration test dang dung tai khoan root. Nen dung tai khoan runtime "
                    + "chi co SELECT/INSERT/UPDATE/DELETE tren clone (PHONENEST_IT_DB_USER), xem DB/admin/create_restricted_accounts_DRAFT.sql.");
        }
    }

    /** Nem IllegalStateException neu URL khong an toan. Tra ve ten database. */
    public static String kiemTra(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("IT GUARD: spring.datasource.url trong.");
        }
        Matcher m = URL.matcher(url.trim());
        if (!m.matches()) {
            throw new IllegalStateException("IT GUARD: khong nhan dien duoc JDBC URL: " + url);
        }
        String host = m.group(1).toLowerCase(Locale.ROOT);
        String db = m.group(2).toLowerCase(Locale.ROOT);
        if (!host.equals("127.0.0.1") && !host.equals("localhost")) {
            throw new IllegalStateException("IT GUARD: chi cho phep database tren may cuc bo, host=" + host);
        }
        if (db.startsWith("bandienthoai") || !TEN_DB_CHO_PHEP.matcher(db).matches()) {
            throw new IllegalStateException("IT GUARD: database '" + db
                    + "' khong phai clone dung mot lan hop le (can ^(claude_2b7[a-z]_|phonenest_it_)...). Tu choi chay test.");
        }
        return db;
    }
}
