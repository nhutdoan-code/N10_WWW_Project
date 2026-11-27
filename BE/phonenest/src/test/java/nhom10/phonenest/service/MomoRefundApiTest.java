package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MomoRefundApiTest {
    private static final String PARTNER_CODE = "MOMO_TEST";
    private static final String ACCESS_KEY = "ACCESS_TEST";
    private static final String SECRET_KEY = "SECRET_TEST";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private AtomicReference<Map<String, Object>> requestBody;
    private MomoPaymentService service;
    private DonHangRepository orders;
    private ThanhToanRepository payments;
    private DonHang order;
    private ThanhToan payment;

    @BeforeEach
    void setUp() throws Exception {
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/refund", exchange -> {
            requestBody.set(objectMapper.readValue(exchange.getRequestBody(), objectMapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class)));
            byte[] response = """
                    {"resultCode":0,"message":"Successful.","requestId":"RFYC001","transId":"RF_TRANS_001"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/create-timeout", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
            byte[] response = """
                    {"resultCode":0,"message":"Successful.","payUrl":"https://momo.test/pay"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (java.io.IOException ignored) {
                // The client is expected to close the request after its read timeout.
            } finally {
                exchange.close();
            }
        });
        server.createContext("/create-http-error", exchange -> {
            byte[] response = "{\"message\":\"Forbidden\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(403, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        orders = mock(DonHangRepository.class);
        payments = mock(ThanhToanRepository.class);
        service = new MomoPaymentService(orders, payments, mock(DonHangService.class),
                mock(CheckoutPaymentSessionService.class), mock(ThongBaoService.class),
                mock(NhatKyHoatDongService.class));
        ReflectionTestUtils.setField(service, "partnerCode", PARTNER_CODE);
        ReflectionTestUtils.setField(service, "accessKey", ACCESS_KEY);
        ReflectionTestUtils.setField(service, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(service, "refundEndpoint",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/refund");

        order = new DonHang();
        order.setMaDonHang("DH001");
        ThanhToan payment = new ThanhToan();
        payment.setMaThanhToan("TT001");
        payment.setDonHang(order);
        payment.setPhuongThuc(PhuongThucTt.VI_DIEN_TU);
        payment.setTrangThai(TrangThaiThanhToan.DA_THANH_TOAN);
        payment.setSoTien(new BigDecimal("500000"));
        payment.setMaGiaoDich("MOMO_ORDER_001");
        payment.setMaGiaoDichMoMo("MOMO_TRANS_001");
        when(payments.findByDonHangMaDonHangOrderByNgayTaoDesc("DH001")).thenReturn(List.of(payment));
        this.payment = payment;
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsSignedRefundAndReturnsProviderTransactionId() throws Exception {
        String refundId = service.refund("DH001", new BigDecimal("125000"), "YC001");

        assertThat(refundId).isEqualTo("RF_TRANS_001");
        Map<String, Object> body = requestBody.get();
        assertThat(body).containsEntry("partnerCode", PARTNER_CODE)
                .containsEntry("orderId", "MOMO_ORDER_001")
                .containsEntry("requestId", "RFYC001")
                .containsEntry("amount", "125000")
                .containsEntry("transId", "MOMO_TRANS_001");
        assertThat(body.get("signature")).isEqualTo(hmac(
                "accessKey=" + ACCESS_KEY
                        + "&amount=125000&description=Hoan tien yeu cau YC001"
                        + "&orderId=MOMO_ORDER_001&partnerCode=" + PARTNER_CODE
                        + "&requestId=RFYC001&transId=MOMO_TRANS_001"));
    }

    @Test
    void rejectsRefundWhenAmountExceedsOriginalPayment() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.refund("DH001", new BigDecimal("500001"), "YC001"))
                .isInstanceOf(nhom10.phonenest.exception.AppException.class);
        assertThat(requestBody.get()).isNull();
    }

    @Test
    void reportsUncertainProviderResultWhenCheckoutRequestTimesOut() {
        ReflectionTestUtils.setField(service, "endpoint",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/create-timeout");
        ReflectionTestUtils.setField(service, "redirectUrl", "http://localhost/orders");
        ReflectionTestUtils.setField(service, "ipnUrl", "http://localhost/payment/ipn");
        ReflectionTestUtils.setField(service, "readTimeoutMs", 100);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.createCheckoutPayment("SESSION001", new BigDecimal("500000")))
                .isInstanceOfSatisfying(nhom10.phonenest.exception.AppException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PAYMENT_PROVIDER_UNCERTAIN"));
    }

    @Test
    void distinguishesProviderHttpErrorFromNetworkFailure() {
        ReflectionTestUtils.setField(service, "endpoint",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/create-http-error");
        ReflectionTestUtils.setField(service, "redirectUrl", "http://localhost/orders");
        ReflectionTestUtils.setField(service, "ipnUrl", "http://localhost/payment/ipn");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.createCheckoutPayment("SESSION002", new BigDecimal("500000")))
                .isInstanceOfSatisfying(nhom10.phonenest.exception.AppException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PAYMENT_PROVIDER_UNCERTAIN");
                    assertThat(exception.getMessage()).contains("HTTP 403");
                });
    }

    @Test
    void refundsCancelledOrderAndMarksPaymentRefundedOnlyAfterProviderSuccess() {
        payment.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
        when(orders.findByIdForUpdate("DH001")).thenReturn(java.util.Optional.of(order));
        when(payments.findByMaGiaoDichForUpdate("MOMO_ORDER_001"))
                .thenReturn(java.util.Optional.of(payment));

        var result = service.refundPendingPayment("DH001");

        assertThat(result.status()).isEqualTo(TrangThaiThanhToan.DA_HOAN_TIEN.name());
        assertThat(result.transactionId()).isEqualTo("RF_TRANS_001");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_HOAN_TIEN);
        assertThat(requestBody.get()).containsEntry("amount", "500000")
                .containsEntry("requestId", "RFTT001")
                .containsEntry("description", "Hoan tien don hang DH001");
    }

    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder result = new StringBuilder();
        for (byte value : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
