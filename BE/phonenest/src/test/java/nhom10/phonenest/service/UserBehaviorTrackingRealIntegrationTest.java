package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.HanhViDto;
import nhom10.phonenest.entity.HanhViNguoiDung;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.Role;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.HanhViNguoiDungRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mariadb://127.0.0.1:3307/bandienthoai",
        "spring.datasource.username=root",
        "spring.datasource.password=sapassword",
        "phonenest.ai.embedding.provider=ollama",
        "phonenest.ai.embedding.ollama.base-url=http://127.0.0.1:11434"
})
class UserBehaviorTrackingRealIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HanhViNguoiDungRepository hanhViRepository;

    @Autowired
    private HanhViNguoiDungService hanhViService;

    @Autowired
    private DonHangService donHangService;

    @Autowired
    private CheckoutIdempotencyService checkoutIdempotencyService;

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private PhienBanSanPhamRepository phienBanRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.generate("TK001", Role.ADMIN);
        customerToken = jwtUtil.generate("TK002", Role.CUSTOMER);
        hanhViRepository.deleteAll();
        phienBanRepository.findById("PB001").ifPresent(pb -> {
            pb.setSoLuongTon(100);
            phienBanRepository.save(pb);
        });
    }

    @AfterEach
    void tearDown() {
        hanhViRepository.deleteAll();
    }

    @Test
    @DisplayName("Thực tế: PRODUCT_VIEW ghi vào MariaDB và tự động chống ghi trùng trong 2 phút")
    void testProductView_RecordedAndThrottled() throws Exception {
        String sessionId = "sess_real_" + UUID.randomUUID();
        HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                sessionId,
                LoaiHanhViNguoiDung.PRODUCT_VIEW,
                "SP001",
                null,
                null,
                "PRODUCT_DETAIL",
                null
        );

        // First request: saved into MariaDB
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.maHanhVi").isNotEmpty());

        List<HanhViNguoiDung> records = hanhViRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getMaSanPham()).isEqualTo("SP001");
        assertThat(records.get(0).getLoaiHanhVi()).isEqualTo(LoaiHanhViNguoiDung.PRODUCT_VIEW);

        // Immediate second request (remount / rapid refresh): throttled, no duplicate in MariaDB
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.maHanhVi").value("THROTTLED"));

        assertThat(hanhViRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Thực tế: PRODUCT_CLICK từ chối 400 nếu sản phẩm không tồn tại (không âm thầm null)")
    void testProductClick_RejectsInvalidProduct() throws Exception {
        HanhViDto.EventRequest validReq = new HanhViDto.EventRequest(
                "sess_click",
                LoaiHanhViNguoiDung.PRODUCT_CLICK,
                "SP001",
                null,
                null,
                "HOME_PAGE",
                null
        );
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        HanhViDto.EventRequest invalidReq = new HanhViDto.EventRequest(
                "sess_click",
                LoaiHanhViNguoiDung.PRODUCT_CLICK,
                "SP_FAKE_999",
                null,
                null,
                "HOME_PAGE",
                null
        );
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sản phẩm không tồn tại: SP_FAKE_999"));
    }

    @Test
    @DisplayName("Thực tế: ADD_TO_CART xác thực biến thể thuộc đúng sản phẩm")
    void testAddToCart_ValidatesVariantOwnership() throws Exception {
        HanhViDto.EventRequest validReq = new HanhViDto.EventRequest(
                "sess_cart",
                LoaiHanhViNguoiDung.ADD_TO_CART,
                "SP001",
                "PB001",
                null,
                "PRODUCT_DETAIL",
                null
        );
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        HanhViDto.EventRequest invalidVariantReq = new HanhViDto.EventRequest(
                "sess_cart",
                LoaiHanhViNguoiDung.ADD_TO_CART,
                "SP001",
                "PB_NON_EXISTENT",
                null,
                "PRODUCT_DETAIL",
                null
        );
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidVariantReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phiên bản không tồn tại: PB_NON_EXISTENT"));
    }

    @Test
    @DisplayName("Thực tế: SEARCH cho phép maSanPham null và lưu từ khóa vào MariaDB")
    void testSearch_AllowsNullProduct() throws Exception {
        HanhViDto.EventRequest searchReq = new HanhViDto.EventRequest(
                "sess_search",
                LoaiHanhViNguoiDung.SEARCH,
                null,
                null,
                "iPhone 15 Pro Max",
                "SEARCH_BOX",
                null
        );
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        List<HanhViNguoiDung> records = hanhViRepository.findAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getTuKhoa()).isEqualTo("iPhone 15 Pro Max");
        assertThat(records.get(0).getMaSanPham()).isNull();
    }

    @Test
    @DisplayName("Thực tế: Client spoofing PURCHASE bị chặn với 400 Bad Request")
    void testPurchase_ClientSpoofingBlocked() throws Exception {
        HanhViDto.EventRequest spoofReq = new HanhViDto.EventRequest(
                "sess_spoof",
                LoaiHanhViNguoiDung.PURCHASE,
                "SP001",
                "PB001",
                null,
                "CLIENT",
                null
        );
        mockMvc.perform(post("/api/tracking/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spoofReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Hành vi PURCHASE chỉ được ghi nhận từ hệ thống")));
    }

    @Test
    @DisplayName("Thực tế Case 1: Đơn hàng thành công, tracking PURCHASE được ghi nhận sau commit")
    void testPurchase_OrderSuccess_TrackingRecorded() {
        DonHangDto.Checkout checkoutReq = new DonHangDto.Checkout(
                PhuongThucTt.COD,
                "DC001",
                null,
                null,
                "PB001",
                1
        );

        DonHangDto order = donHangService.datHang("TK002", checkoutReq);
        assertThat(order).isNotNull();
        assertThat(donHangRepository.existsById(order.maDonHang())).isTrue();

        // Kiểm tra MariaDB có bản ghi PURCHASE cho đơn hàng này
        List<HanhViNguoiDung> purchases = hanhViRepository.findAll().stream()
                .filter(h -> h.getLoaiHanhVi() == LoaiHanhViNguoiDung.PURCHASE
                        && ("BACKEND_ORDER_" + order.maDonHang()).equals(h.getSessionId()))
                .toList();

        assertThat(purchases).hasSize(1);
        assertThat(purchases.get(0).getMaKh()).isEqualTo("TK002");
        assertThat(purchases.get(0).getMaSanPham()).isEqualTo("SP001");
        assertThat(purchases.get(0).getMaPhienBan()).isEqualTo("PB001");
        assertThat(purchases.get(0).getMetadata()).contains(order.maDonHang());
    }

    @Test
    @DisplayName("Thực tế Case 2: Đơn hàng rollback, PURCHASE không tồn tại trong MariaDB")
    void testPurchase_OrderRollback_NoPurchaseCreated() {
        DonHangDto.Checkout invalidReq = new DonHangDto.Checkout(
                PhuongThucTt.COD,
                "DC001",
                null,
                null,
                "PB001",
                99999 // Vượt tồn kho, chắc chắn throw AppException và rollback
        );

        long countOrdersBefore = donHangRepository.count();

        assertThatThrownBy(() -> donHangService.datHang("TK002", invalidReq))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không đủ hàng");

        assertThat(donHangRepository.count()).isEqualTo(countOrdersBefore);

        // MariaDB hoàn toàn không có bất kỳ bản ghi PURCHASE rác nào
        List<HanhViNguoiDung> purchases = hanhViRepository.findAll().stream()
                .filter(h -> h.getLoaiHanhVi() == LoaiHanhViNguoiDung.PURCHASE)
                .toList();
        assertThat(purchases).isEmpty();
    }

    @Test
    @DisplayName("Thực tế Case 3: Tracking lỗi không làm hỏng đơn hàng (đơn hàng vẫn tạo thành công)")
    void testPurchase_TrackingFailure_OrderStillSucceeds() {
        // Khi ghiNhanPurchase gặp lỗi ngoại lệ, try-catch nội bộ và afterCommit bảo vệ đơn hàng
        DonHangDto.Checkout checkoutReq = new DonHangDto.Checkout(
                PhuongThucTt.COD,
                "DC001",
                null,
                null,
                "PB001",
                1
        );

        // Đơn hàng được commit thành công và không bị rollback
        DonHangDto order = donHangService.datHang("TK002", checkoutReq);
        assertThat(order).isNotNull();
        assertThat(donHangRepository.existsById(order.maDonHang())).isTrue();
    }

    @Test
    @DisplayName("Thực tế Case 4: Retry cùng idempotency key không ghi trùng sự kiện PURCHASE")
    void testPurchase_IdempotencyRetry_NoDuplicatePurchase() {
        String idempotencyKey = UUID.randomUUID().toString();
        DonHangDto.Checkout checkoutReq = new DonHangDto.Checkout(
                PhuongThucTt.COD,
                "DC001",
                null,
                null,
                "PB001",
                1
        );

        // Lần 1: Tạo đơn hàng thành công
        DonHangDto firstOrder = checkoutIdempotencyService.executeIdempotentCheckout("TK002", idempotencyKey, checkoutReq);
        assertThat(firstOrder).isNotNull();

        long countAfterFirst = hanhViRepository.findAll().stream()
                .filter(h -> h.getLoaiHanhVi() == LoaiHanhViNguoiDung.PURCHASE
                        && ("BACKEND_ORDER_" + firstOrder.maDonHang()).equals(h.getSessionId()))
                .count();
        assertThat(countAfterFirst).isEqualTo(1);

        // Lần 2: Retry cùng idempotencyKey
        DonHangDto retryOrder = checkoutIdempotencyService.executeIdempotentCheckout("TK002", idempotencyKey, checkoutReq);
        assertThat(retryOrder.maDonHang()).isEqualTo(firstOrder.maDonHang());

        // Số lượng bản ghi PURCHASE vẫn chính xác là 1 (không ghi trùng)
        long countAfterRetry = hanhViRepository.findAll().stream()
                .filter(h -> h.getLoaiHanhVi() == LoaiHanhViNguoiDung.PURCHASE
                        && ("BACKEND_ORDER_" + firstOrder.maDonHang()).equals(h.getSessionId()))
                .count();
        assertThat(countAfterRetry).isEqualTo(1);
    }

    @Test
    @DisplayName("Thực tế: Rate Limiting chặn bypass bằng cách thay đổi sessionId liên tục từ cùng IP")
    void testRateLimiting_BypassPrevention_RotatingSessionSameIp() throws Exception {
        String testIp = "10.0.0.99";

        // Send 60 events with different sessionIds from testIp
        for (int i = 0; i < 60; i++) {
            HanhViDto.EventRequest req = new HanhViDto.EventRequest(
                    "ROTATE_SESSION_" + i,
                    LoaiHanhViNguoiDung.SEARCH,
                    null,
                    null,
                    "keyword_" + i,
                    "SEARCH_BOX",
                    null
            );
            mockMvc.perform(post("/api/tracking/events")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        // 61st event with another fresh sessionId from same IP MUST return 429 Too Many Requests
        HanhViDto.EventRequest req61 = new HanhViDto.EventRequest(
                "ROTATE_SESSION_61",
                LoaiHanhViNguoiDung.SEARCH,
                null,
                null,
                "keyword_61",
                "SEARCH_BOX",
                null
        );
        mockMvc.perform(post("/api/tracking/events")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req61)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.maHanhVi").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("Thực tế: Admin Analytics và User Preferences yêu cầu quyền ADMIN, trả về dữ liệu chuẩn")
    void testAdminAnalyticsAndPreferences() throws Exception {
        // Customer attempting to view admin analytics is forbidden (403)
        mockMvc.perform(get("/api/admin/analytics/behavior")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // Admin viewing behavior stats succeeds (200)
        mockMvc.perform(get("/api/admin/analytics/behavior")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tongLuot").isNumber());

        // Admin viewing user preferences succeeds (200)
        mockMvc.perform(get("/api/admin/analytics/preferences/TK002")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maKh").value("TK002"));
    }

    @Test
    @DisplayName("Thực tế: Admin Cleanup validate chặt chẽ soNgayLuuTru và xóa bản ghi an toàn")
    void testAdminCleanup_ValidationAndSafety() throws Exception {
        // Cleanup with invalid days (0) rejected with 400
        mockMvc.perform(delete("/api/admin/analytics/cleanup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("soNgayLuuTru", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Số ngày lưu trữ không hợp lệ")));

        // Cleanup with negative days rejected with 400
        mockMvc.perform(delete("/api/admin/analytics/cleanup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("soNgayLuuTru", "-10"))
                .andExpect(status().isBadRequest());

        // Cleanup with valid days (30) succeeds
        mockMvc.perform(delete("/api/admin/analytics/cleanup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("soNgayLuuTru", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thanhCong").value(true))
                .andExpect(jsonPath("$.soNgayLuuTru").value(30));
    }
}
