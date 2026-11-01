package nhom10.phonenest.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import nhom10.phonenest.entity.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.SemanticException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for Unified Admin Search JPQL queries.
 *
 * <p>Uses an isolated, database-free Hibernate SessionFactory (configured with
 * UserSuppliedConnectionProviderImpl) to validate that all @Query annotations
 * in repositories used by TimKiemService conform strictly to the JPA metamodel.
 *
 * <p>Does NOT connect to, query, or mutate the live BanDienThoai database.
 */
class SearchAdminRepositoryQueryTest {

    private static EntityManagerFactory entityManagerFactory;

    @BeforeAll
    static void setUp() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .applySetting("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();

        MetadataSources sources = new MetadataSources(registry);
        sources.addAnnotatedClass(SanPham.class);
        sources.addAnnotatedClass(LoaiSanPham.class);
        sources.addAnnotatedClass(ThuongHieu.class);
        sources.addAnnotatedClass(PhienBanSanPham.class);
        sources.addAnnotatedClass(HinhAnhSanPham.class);
        sources.addAnnotatedClass(TaiKhoan.class);
        sources.addAnnotatedClass(KhachHang.class);
        sources.addAnnotatedClass(QuanTriVien.class);
        sources.addAnnotatedClass(DonHang.class);
        sources.addAnnotatedClass(ChiTietDonHang.class);
        sources.addAnnotatedClass(KhuyenMai.class);

        entityManagerFactory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Test
    @DisplayName("Catch class of bug: ORDER BY s.ngayTao DESC must be rejected by Hibernate metamodel")
    void shouldFailWhenQueryReferencesNonexistentNgayTaoOnSanPham() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            String buggyQuery = "SELECT s FROM SanPham s " +
                    "WHERE LOWER(s.tenMay) LIKE LOWER(:pattern) ESCAPE '\\' " +
                    "   OR LOWER(s.maSanPham) LIKE LOWER(:pattern) ESCAPE '\\' " +
                    "ORDER BY s.ngayTao DESC";

            assertThatThrownBy(() -> em.createQuery(buggyQuery))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Could not resolve attribute 'ngayTao' of 'nhom10.phonenest.entity.SanPham'");
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("SanPhamRepository.searchAdmin JPQL query must compile cleanly against SanPham metamodel")
    void shouldValidateSanPhamRepositorySearchAdminQuery() throws Exception {
        Method method = SanPhamRepository.class.getMethod("searchAdmin", String.class, Pageable.class);
        Query queryAnn = method.getAnnotation(Query.class);
        assertThat(queryAnn).isNotNull();

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            assertThatCode(() -> em.createQuery(queryAnn.value()))
                    .doesNotThrowAnyException();
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("LoaiSanPhamRepository.searchAdmin JPQL query must compile cleanly against LoaiSanPham metamodel")
    void shouldValidateLoaiSanPhamRepositorySearchAdminQuery() throws Exception {
        Method method = LoaiSanPhamRepository.class.getMethod("searchAdmin", String.class, Pageable.class);
        Query queryAnn = method.getAnnotation(Query.class);
        assertThat(queryAnn).isNotNull();

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            assertThatCode(() -> em.createQuery(queryAnn.value()))
                    .doesNotThrowAnyException();
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("TaiKhoanRepository.searchAdmin JPQL query must compile cleanly against TaiKhoan, KhachHang, QuanTriVien metamodels")
    void shouldValidateTaiKhoanRepositorySearchAdminQuery() throws Exception {
        Method method = TaiKhoanRepository.class.getMethod("searchAdmin", String.class, Pageable.class);
        Query queryAnn = method.getAnnotation(Query.class);
        assertThat(queryAnn).isNotNull();

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            assertThatCode(() -> em.createQuery(queryAnn.value()))
                    .doesNotThrowAnyException();
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("DonHangRepository.searchAdmin JPQL query must compile cleanly against DonHang, KhachHang metamodels")
    void shouldValidateDonHangRepositorySearchAdminQuery() throws Exception {
        Method method = DonHangRepository.class.getMethod("searchAdmin", String.class, Pageable.class);
        Query queryAnn = method.getAnnotation(Query.class);
        assertThat(queryAnn).isNotNull();

        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            assertThatCode(() -> em.createQuery(queryAnn.value()))
                    .doesNotThrowAnyException();
        } finally {
            em.close();
        }
    }
}
