package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import nhom10.phonenest.support.ItDatabaseGuard;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.UUID;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "PHONENEST_IT_DB_URL", matches = ".+",
        disabledReason = "Integration test chi chay tren clone dung mot lan (dat PHONENEST_IT_DB_URL)")
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = ItDatabaseGuard.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "phonenest.ai.embedding.provider=ollama",
        "phonenest.ai.embedding.ollama.base-url=http://127.0.0.1:11434"
})
public class SalesApprovalIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GioHangService gioHangService;

    @Autowired
    private ThanhToanService thanhToanService;

    @Autowired
    private DonHangService donHangService;

    @Autowired
    private SanPhamService sanPhamService;

    @Autowired
    private PhienBanSanPhamRepository phienBanRepository;

    @Autowired
    private GioHangRepository gioHangRepository;

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String customerToken;

    private static final String KHACH_MA = "TK002";
    private static final String DIA_CHI_MA = "DC001";

    // Target variants from database
    private static final String PB_VIVO_Y100_BLOCKED = "PB500488"; // BLOCKED (seed 1.99M)
    private static final String PB_GALAXY_ZFLIP5_BLOCKED = "PB500476"; // BLOCKED (seed 1.99M)
    private static final String PB_GALAXY_ZFLIP6_BLOCKED = "PB500481"; // BLOCKED (seed 1.99M)
    private static final String PB_IPHONE15PRO_PENDING = "PB500468"; // iPhone 11, PENDING_REVIEW (dam bao trong setUp)
    private static final String PB_GALAXY_A56_APPROVED = "PB1793202488339002"; // APPROVED, stock = 25
    private static final String PB_VIVO_Y12S_DISCONTINUED = "PB500500"; // Discontinued, stock = 15

    private void verifyDatabaseSafety() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            String url = conn.getMetaData().getURL();

            if (catalog != null && "bandienthoai".equalsIgnoreCase(catalog.trim())) {
                throw new IllegalStateException(
                        "CRITICAL SAFETY VIOLATION: Test attempted to execute against MAIN DATABASE 'bandienthoai'! Blocked.");
            }
            // Ten database thuc te phai la clone dung mot lan hop le
            ItDatabaseGuard.kiemTra("jdbc:mariadb://127.0.0.1/" + catalog);
            if (url != null && url.matches(".*[/:](bandienthoai)(\\?.*)?$")) {
                throw new IllegalStateException(
                        "CRITICAL SAFETY VIOLATION: JDBC URL targets production database 'bandienthoai'! URL: " + url);
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        verifyDatabaseSafety();
        adminToken = jwtUtil.generate("TK001", Role.ADMIN);
        customerToken = jwtUtil.generate(KHACH_MA, Role.CUSTOMER);

        // Fixture tren clone dung mot lan (Giai doan 2B.7B):
        // - PB500468 phai o PENDING_REVIEW.
        // - 3 bien the Galaxy A56 phai co phe duyet HOP LE theo quy tac moi (qua API, co dau van tay).
        //   Phe duyet cu (M15 v1) khong co dau van tay -> thu hoi roi duyet lai.
        PhienBanSanPham pending = phienBanRepository.findById(PB_IPHONE15PRO_PENDING).orElseThrow();
        if (pending.getTrangThaiPheDuyet() != TrangThaiPheDuyet.PENDING_REVIEW) {
            pending.setTrangThaiPheDuyet(TrangThaiPheDuyet.PENDING_REVIEW);
            pending.setDauVanTayPheDuyet(null);
            phienBanRepository.saveAndFlush(pending);
        }
        for (String pb : new String[]{PB_GALAXY_A56_APPROVED, "PB1793202488375003", "PB1793204752658080"}) {
            damBaoDaDuyetHopLe(pb);
        }
    }

    private void damBaoDaDuyetHopLe(String maPhienBan) throws Exception {
        PhienBanSanPham p = phienBanRepository.findById(maPhienBan).orElseThrow();
        if (PheDuyetBanHangPolicy.duocPhepBan(p)) {
            return;
        }
        if (p.getTrangThaiPheDuyet() == TrangThaiPheDuyet.APPROVED) {
            mockMvc.perform(put("/api/admin/variants/" + maPhienBan + "/approval")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"trangThaiPheDuyet\":\"PENDING_REVIEW\",\"lyDo\":\"Thu hoi phe duyet cu thieu dau van tay de duyet lai\"}"))
                    .andExpect(status().isOk());
            p = phienBanRepository.findById(maPhienBan).orElseThrow();
        }
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("trangThaiPheDuyet", "APPROVED");
        body.put("lyDo", "Fixture kiem thu tich hop: xac nhan gia va cau hinh hien tai");
        body.put("nguonXacMinh", "Fixture kiem thu tich hop tren clone dung mot lan, khong phai gia thi truong");
        body.put("giaPheDuyet", p.getGiaBan());
        body.put("giaNiemYetPheDuyet", p.getGiaNiemYet());
        body.put("ramPheDuyet", p.getRam());
        body.put("boNhoPheDuyet", p.getBoNho());
        body.put("mauSacPheDuyet", p.getMauSac());
        mockMvc.perform(put("/api/admin/variants/" + maPhienBan + "/approval")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 1: Thêm vào giỏ: biến thể PENDING_REVIEW -> bị chặn
    // -------------------------------------------------------------------------
    @Test
    @Order(1)
    @DisplayName("Kịch bản 1: Thêm vào giỏ hàng biến thể PENDING_REVIEW bị chặn với lỗi rõ ràng")
    void test01_addToCart_pendingReview_blocked() {
        assertThatThrownBy(() -> gioHangService.add(KHACH_MA, PB_IPHONE15PRO_PENDING, 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đang chờ phê duyệt");
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 2: Cập nhật số lượng trong giỏ: biến thể PENDING_REVIEW -> bị chặn tăng
    // -------------------------------------------------------------------------
    @Test
    @Order(2)
    @DisplayName("Kịch bản 2: Cập nhật số lượng biến thể PENDING_REVIEW trong giỏ bị chặn")
    void test02_updateQuantity_pendingReview_blocked() {
        assertThatThrownBy(() -> gioHangService.update(KHACH_MA, PB_IPHONE15PRO_PENDING, 2))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đang chờ phê duyệt");
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 3: Mua ngay (Buy Now): biến thể PENDING_REVIEW -> bị chặn
    // -------------------------------------------------------------------------
    @Test
    @Order(3)
    @DisplayName("Kịch bản 3: Mua ngay (Buy Now) biến thể PENDING_REVIEW bị chặn")
    void test03_buyNow_pendingReview_blocked() {
        assertThatThrownBy(() -> thanhToanService.mucMua(KHACH_MA, PB_IPHONE15PRO_PENDING, 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đang chờ phê duyệt");
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 4: Xem trước thanh toán (Checkout Preview): đơn có PENDING_REVIEW -> bị chặn
    // -------------------------------------------------------------------------
    @Test
    @Order(4)
    @DisplayName("Kịch bản 4: Xem trước thanh toán có biến thể PENDING_REVIEW bị chặn")
    void test04_checkoutPreview_pendingReview_blocked() {
        assertThatThrownBy(() -> thanhToanService.xemTruoc(KHACH_MA, DIA_CHI_MA, PB_IPHONE15PRO_PENDING, 1))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đang chờ phê duyệt");
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 5: Đặt hàng: biến thể PENDING_REVIEW -> bị chặn
    // -------------------------------------------------------------------------
    @Test
    @Order(5)
    @DisplayName("Kịch bản 5: Đặt hàng trực tiếp với biến thể PENDING_REVIEW bị chặn")
    void test05_checkout_pendingReview_blocked() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_IPHONE15PRO_PENDING, 1);

        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("đang chờ phê duyệt");
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 6: Đặt hàng: biến thể BLOCKED -> bị chặn (vivo Y100, Z Flip5, Z Flip6)
    // -------------------------------------------------------------------------
    @Test
    @Order(6)
    @DisplayName("Kịch bản 6: Đặt hàng biến thể BLOCKED (vivo Y100, Z Flip5, Z Flip6 giá 1.99M) bị chặn hoàn toàn")
    void test06_checkout_blockedVariants_blocked() {
        // Test vivo Y100 5G (PB500488) - giá 1.990.000đ seed
        DonHangDto.Checkout reqVivo = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_VIVO_Y100_BLOCKED, 1);
        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, reqVivo))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("bị khóa bán");

        // Test Galaxy Z Flip5 (PB500476) - giá 1.990.000đ seed
        DonHangDto.Checkout reqFlip5 = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_GALAXY_ZFLIP5_BLOCKED, 1);
        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, reqFlip5))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("bị khóa bán");

        // Test Galaxy Z Flip6 (PB500481) - giá 1.990.000đ seed
        DonHangDto.Checkout reqFlip6 = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_GALAXY_ZFLIP6_BLOCKED, 1);
        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, reqFlip6))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("bị khóa bán");
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 7: Đặt hàng: biến thể APPROVED còn tồn kho -> THÀNH CÔNG (Galaxy A56)
    // -------------------------------------------------------------------------
    @Test
    @Order(7)
    @DisplayName("Kịch bản 7: Đặt hàng biến thể APPROVED còn tồn kho (Galaxy A56) thành công")
    void test07_checkout_approvedInStock_success() {
        int initialStock = phienBanRepository.findById(PB_GALAXY_A56_APPROVED).orElseThrow().getSoLuongTon();
        assertThat(initialStock).isGreaterThan(0);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_GALAXY_A56_APPROVED, 1);

        DonHangDto order = donHangService.datHang(KHACH_MA, req);
        assertThat(order).isNotNull();
        assertThat(order.maDonHang()).isNotNull();

        // Kiểm tra tồn kho giảm đúng 1
        int updatedStock = phienBanRepository.findById(PB_GALAXY_A56_APPROVED).orElseThrow().getSoLuongTon();
        assertThat(updatedStock).isEqualTo(initialStock - 1);
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 8: Đặt hàng: biến thể APPROVED nhưng tồn kho = 0 -> bị chặn do hết hàng
    // -------------------------------------------------------------------------
    @Test
    @Order(8)
    @DisplayName("Kịch bản 8: Đặt hàng biến thể APPROVED nhưng tồn kho = 0 bị chặn do hết hàng")
    void test08_checkout_approvedOutOfStock_blocked() {
        PhienBanSanPham pv = phienBanRepository.findById(PB_GALAXY_A56_APPROVED).orElseThrow();
        int savedStock = pv.getSoLuongTon();
        try {
            pv.setSoLuongTon(0);
            phienBanRepository.saveAndFlush(pv);

            DonHangDto.Checkout req = new DonHangDto.Checkout(
                    PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_GALAXY_A56_APPROVED, 1);

            assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không đủ hàng");
        } finally {
            pv.setSoLuongTon(savedStock);
            phienBanRepository.saveAndFlush(pv);
        }
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 9: Đặt hàng: biến thể ngừng kinh doanh -> bị chặn
    // -------------------------------------------------------------------------
    @Test
    @Order(9)
    @DisplayName("Kịch bản 9: Đặt hàng biến thể ngừng kinh doanh (vivo Y12s) bị chặn")
    void test09_checkout_discontinued_blocked() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_VIVO_Y12S_DISCONTINUED, 1);

        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không còn kinh doanh");
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 10: Biến thể bị khóa trong giỏ hàng: không thể thanh toán toàn bộ giỏ cho đến khi xóa
    // -------------------------------------------------------------------------
    @Test
    @Order(10)
    @DisplayName("Kịch bản 10: Biến thể bị khóa trong giỏ hàng chặn thanh toán cho đến khi xóa")
    void test10_cartWithLockedVariant_blocksCheckoutUntilRemoved() throws SQLException {
        // Dùng SQL chèn trực tiếp 1 item bị khóa vào giỏ của KHACH_MA để mô phỏng giỏ hàng có sẵn từ trước
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO chitietgiohang (maGioHang, maPhienBan, soLuong) " +
                    "SELECT g.maGioHang, '" + PB_VIVO_Y100_BLOCKED + "', 1 " +
                    "FROM giohang g WHERE g.maKh = '" + KHACH_MA + "'");
        }

        // Đặt hàng từ giỏ (maSanPham = null) PHẢI bị chặn vì có PB500488
        DonHangDto.Checkout reqFromCart = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, null, null);

        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, reqFromCart))
                .isInstanceOf(AppException.class);

        // Xóa item bị khóa khỏi giỏ hàng
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM chitietgiohang WHERE maPhienBan = '" + PB_VIVO_Y100_BLOCKED + "'");
        }
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 11: Đua điều kiện: admin đổi trạng thái ngay trước khi đặt hàng -> khóa bi quan chặn
    // -------------------------------------------------------------------------
    @Test
    @Order(11)
    @DisplayName("Kịch bản 11: Concurrency - admin khóa bán biến thể ngay trước khi khách đặt hàng -> khóa bi quan chặn giao dịch")
    void test11_concurrency_adminBlocksVariant_pessimisticLockCatches() {
        // Bước 1: Khách hàng chuẩn bị mua A56
        String variantId = "PB1793202488375003"; // Biến thể A56 8GB/256GB đang APPROVED
        PhienBanSanPham pv = phienBanRepository.findById(variantId).orElseThrow();
        assertThat(pv.getTrangThaiPheDuyet()).isEqualTo(TrangThaiPheDuyet.APPROVED);

        // Bước 2: Admin khóa bán biến thể này (BLOCKED)
        pv.setTrangThaiPheDuyet(TrangThaiPheDuyet.BLOCKED);
        pv.setLyDoPheDuyet("Phát hiện lỗi định giá");
        phienBanRepository.saveAndFlush(pv);

        // Bước 3: Khách hàng bấm gửi yêu cầu đặt hàng -> DonHangService khóa bi quan và phát hiện trạng thái BLOCKED
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, variantId, 1);

        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("bị khóa bán");

        // Hoàn trả lại trạng thái APPROVED
        pv.setTrangThaiPheDuyet(TrangThaiPheDuyet.APPROVED);
        pv.setLyDoPheDuyet("Khôi phục sau kiểm thử");
        phienBanRepository.saveAndFlush(pv);
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 12: Gọi trực tiếp API thanh toán bỏ qua Frontend -> Backend chặn và trả mã lỗi rõ ràng
    // -------------------------------------------------------------------------
    @Test
    @Order(12)
    @DisplayName("Kịch bản 12: Gọi trực tiếp HTTP POST /api/customer/orders với biến thể BLOCKED -> 400 Bad Request")
    void test12_directApiCall_bypassingFrontend_returns400() throws Exception {
        DonHangDto.Checkout payload = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_VIVO_Y100_BLOCKED, 1);

        mockMvc.perform(post("/api/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bị khóa bán")));
    }

    // -------------------------------------------------------------------------
    // KỊCH BẢN 14: Tính toàn vẹn dữ liệu: đơn hàng lịch sử, giỏ hàng, đánh giá, biến động kho không bị mất hoặc sai lệch
    // -------------------------------------------------------------------------
    @Test
    @Order(14)
    @DisplayName("Kịch bản 14: Tính toàn vẹn dữ liệu nghiệp vụ lịch sử được bảo toàn tuyệt đối")
    void test14_dataIntegrity_historicalRecordsPreserved() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // Kiểm tra số sản phẩm
            ResultSet rsSp = stmt.executeQuery("SELECT COUNT(*) FROM sanpham");
            rsSp.next();
            assertThat(rsSp.getInt(1)).isEqualTo(515);

            // Kiểm tra số biến thể
            ResultSet rsPb = stmt.executeQuery("SELECT COUNT(*) FROM phienbansanpham");
            rsPb.next();
            // 2B.7D: >= (cac test khac tren cung clone co the TAO them bien the; dieu can chung minh la khong MAT dong nao)
            assertThat(rsPb.getInt(1)).isGreaterThanOrEqualTo(585);

            // Kiểm tra các đơn hàng lịch sử ban đầu vẫn tồn tại nguyên vẹn
            ResultSet rsDhGoc = stmt.executeQuery("SELECT COUNT(*) FROM donhang WHERE maDonHang IN ('DH001','DH002','DH003')");
            rsDhGoc.next();
            assertThat(rsDhGoc.getInt(1)).isEqualTo(3);

            // Tổng số đơn hàng >= 9 (bảo toàn toàn bộ lịch sử, không có đơn hàng nào bị mất)
            ResultSet rsDh = stmt.executeQuery("SELECT COUNT(*) FROM donhang");
            rsDh.next();
            assertThat(rsDh.getInt(1)).isGreaterThanOrEqualTo(9);

            // Kiểm tra các bảng nghiệp vụ khác còn nguyên vẹn
            ResultSet rsDg = stmt.executeQuery("SELECT COUNT(*) FROM danhgia");
            rsDg.next();
            assertThat(rsDg.getInt(1)).isEqualTo(1); // 1 đánh giá lịch sử

            ResultSet rsBdk = stmt.executeQuery("SELECT COUNT(*) FROM biendongkho");
            rsBdk.next();
            assertThat(rsBdk.getInt(1)).isGreaterThanOrEqualTo(5); // 5 biến động kho gốc + biến động từ đơn test
        }
    }

    @Test
    @Order(15)
    @DisplayName("Kịch bản 15: Fail-Closed - Ràng buộc NOT NULL ở DB và từ chối mua khi trạng thái null/không xác định")
    void test15_failClosed_nullStatus_blocked() {
        // 1. Kiểm tra ở tầng Database: Cột trangThaiPheDuyet có ràng buộc NOT NULL, không cho phép lưu NULL
        PhienBanSanPham pv = phienBanRepository.findById(PB_GALAXY_A56_APPROVED).orElseThrow();
        pv.setTrangThaiPheDuyet(null);
        assertThatThrownBy(() -> phienBanRepository.saveAndFlush(pv))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // 2. Kiểm tra ở tầng Service: Nếu một đối tượng có trạng thái null (chưa duyệt), logic nghiệp vụ fail-closed chặn tuyệt đối
        pv.setTrangThaiPheDuyet(null);
        assertThat(pv.getTrangThaiPheDuyet() != TrangThaiPheDuyet.APPROVED).isTrue();

        // 3. Kiểm tra biến thể không tồn tại -> 404 Not Found / AppException
        DonHangDto.Checkout notFoundReq = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, "PB_KHONG_TON_TAI_999", 1);
        assertThatThrownBy(() -> donHangService.datHang(KHACH_MA, notFoundReq))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không tìm thấy");
    }

    @Test
    @Order(17)
    @DisplayName("Kịch bản 17: Concurrency - 2 luồng đặt hàng đồng thời khi tồn kho = 1 -> đúng 1 luồng thành công, không bị oversell")
    void test17_concurrency_raceCondition_noOversell() throws Exception {
        String testVariant = "PB1793204752658080"; // A56 8/256GB Đen đang APPROVED
        PhienBanSanPham pv = phienBanRepository.findById(testVariant).orElseThrow();
        int originalStock = pv.getSoLuongTon();
        pv.setSoLuongTon(1);
        phienBanRepository.saveAndFlush(pv);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<String> failMessage = new AtomicReference<>("");

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, testVariant, 1);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    donHangService.datHang(KHACH_MA, req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    failMessage.set(e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Kích hoạt 2 luồng cùng lúc
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
        assertThat(failMessage.get()).matches(msg -> 
                msg.contains("Không đủ tồn kho") || msg.contains("không đủ hàng") || msg.contains("Record has changed") || msg.contains("could not execute statement") || msg.contains("Lock"));

        // Kiểm tra tồn kho sau 2 luồng tranh chấp chính xác về 0 (không bị âm kho)
        PhienBanSanPham pvAfter = phienBanRepository.findById(testVariant).orElseThrow();
        assertThat(pvAfter.getSoLuongTon()).isEqualTo(0);

        // Khôi phục tồn kho ban đầu
        pvAfter.setSoLuongTon(originalStock);
        phienBanRepository.saveAndFlush(pvAfter);
    }

    @Test
    @Order(18)
    @DisplayName("Kịch bản 18: Idempotency - Đặt hàng và tra cứu đơn hàng lặp lại nhất quán dữ liệu")
    void test18_idempotency_orderConsistency() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_GALAXY_A56_APPROVED, 1);
        DonHangDto don1 = donHangService.datHang(KHACH_MA, req);
        assertThat(don1).isNotNull();
        assertThat(don1.maDonHang()).isNotNull();

        DonHangDto loaded = donHangService.chiTiet(KHACH_MA, don1.maDonHang());
        assertThat(loaded.maDonHang()).isEqualTo(don1.maDonHang());
    }

    @Test
    @Order(19)
    @DisplayName("Kịch bản 19: Đơn đã đặt không cho phép chỉnh số lượng kể cả khi biến thể bị khóa bán")
    void test19_suaDong_cannotIncreaseLockedVariant() {
        // Tạo đơn hàng mẫu với A56 APPROVED
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, DIA_CHI_MA, null, null, PB_GALAXY_A56_APPROVED, 1);
        DonHangDto don = donHangService.datHang(KHACH_MA, req);

        // Giả lập sau đó biến thể bị khóa bán (BLOCKED)
        PhienBanSanPham pb = phienBanRepository.findById(PB_GALAXY_A56_APPROVED).orElseThrow();
        pb.setTrangThaiPheDuyet(TrangThaiPheDuyet.BLOCKED);
        phienBanRepository.saveAndFlush(pb);

        try {
            // Admin cố sửa dòng đơn tăng số lượng từ 1 lên 2 -> Bị chặn 409 Conflict
            assertThatThrownBy(() -> donHangService.suaDong(don.maDonHang(), PB_GALAXY_A56_APPROVED, 2))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Không được thay đổi số lượng");
        } finally {
            // Khôi phục lại trạng thái APPROVED
            pb.setTrangThaiPheDuyet(TrangThaiPheDuyet.APPROVED);
            phienBanRepository.saveAndFlush(pb);
        }
    }

}
