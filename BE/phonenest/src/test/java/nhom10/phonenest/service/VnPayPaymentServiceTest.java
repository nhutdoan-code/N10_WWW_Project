package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnPayPaymentServiceTest {
    private static final String TMN_CODE = "TESTTMNC";
    private static final String HASH_SECRET = "TEST_HASH_SECRET";

    private DonHangRepository donHangRepository;
    private ThanhToanRepository thanhToanRepository;
    private DonHangService donHangService;
    private ThongBaoService thongBaoService;
    private CheckoutPaymentSessionService checkoutPaymentSessionService;
    private VnPayPaymentService service;
    private DonHang order;
    private ThanhToan payment;

    @BeforeEach
    void setUp() {
        donHangRepository = mock(DonHangRepository.class);
        thanhToanRepository = mock(ThanhToanRepository.class);
        donHangService = mock(DonHangService.class);
        thongBaoService = mock(ThongBaoService.class);
        checkoutPaymentSessionService = mock(CheckoutPaymentSessionService.class);
        service = new VnPayPaymentService(
                donHangRepository, thanhToanRepository, donHangService, thongBaoService,
                checkoutPaymentSessionService, mock(NhatKyHoatDongService.class),
                mock(YeuCauDoiTraRepository.class));
        ReflectionTestUtils.setField(service, "tmnCode", TMN_CODE);
        ReflectionTestUtils.setField(service, "hashSecret", HASH_SECRET);
        ReflectionTestUtils.setField(service, "endpoint", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(service, "returnUrl", "https://merchant.example/api/payment/vnpay/return");
        ReflectionTestUtils.setField(service, "frontendReturnUrl", "https://shop.example/orders");

        KhachHang customer = new KhachHang();
        customer.setMaKh("KH001");
        order = new DonHang();
        order.setMaDonHang("DH001");
        order.setKhachHang(customer);
        order.setTongThanhToan(new BigDecimal("12500.00"));
        order.setTrangThai(TrangThaiDonHang.CHO_THANH_TOAN);

        payment = new ThanhToan();
        payment.setMaThanhToan("TT001");
        payment.setDonHang(order);
        payment.setPhuongThuc(PhuongThucTt.CHUYEN_KHOAN);
        payment.setSoTien(new BigDecimal("12500.00"));
        payment.setTrangThai(TrangThaiThanhToan.CHO_THANH_TOAN);
        when(donHangRepository.findByIdForUpdate("DH001")).thenReturn(Optional.of(order));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH001"))
                .thenReturn(List.of(payment));
    }

    @Test
    void createsSignedVnPayUrlUsingDongPaymentReference() throws Exception {
        var result = service.create("KH001", "DH001", "203.0.113.8");

        assertThat(result.transactionId()).isEqualTo("TT001");
        assertThat(payment.getMaGiaoDich()).isEqualTo("TT001");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CHO_THANH_TOAN);
        Map<String, String> params = parseQuery(result.payUrl());
        assertThat(params).containsEntry("vnp_TmnCode", TMN_CODE)
                .containsEntry("vnp_TxnRef", "TT001")
                .containsEntry("vnp_Amount", "1250000")
                .containsEntry("vnp_IpAddr", "203.0.113.8")
                .containsEntry("vnp_ReturnUrl", "https://merchant.example/api/payment/vnpay/return");
        assertThat(params.get("vnp_SecureHash")).isEqualTo(signature(params));
        verify(thanhToanRepository).saveAndFlush(payment);
    }

    @Test
    void confirmsPaymentOnlyFromValidSignedIpn() throws Exception {
        payment.setMaGiaoDich("TT001");
        Map<String, String> ipn = signed(Map.of(
                "vnp_TmnCode", TMN_CODE,
                "vnp_TxnRef", "TT001",
                "vnp_Amount", "1250000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "BANKTX001"));
        when(thanhToanRepository.findByMaGiaoDich("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH001"))
                .thenReturn(List.of(payment));

        var response = service.processIpn(ipn);

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        assertThat(payment.getMaGiaoDichNganHang()).isEqualTo("BANKTX001");
        assertThat(payment.getNgayThanhToan()).isNotNull();
        verify(thanhToanRepository).save(payment);
        verify(donHangService).xacNhanSauThanhToan(order);
        verify(thongBaoService).gui(eq("KH001"), eq("Thanh toán VNPay thành công cho đơn hàng DH001."),
                eq("/orders/DH001"));
    }

    @Test
    void rejectsInvalidSignatureAndDoesNotLookUpPayment() {
        var response = service.processIpn(Map.of(
                "vnp_TxnRef", "TT001",
                "vnp_SecureHash", "bad"));

        assertThat(response.rspCode()).isEqualTo("97");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CHO_THANH_TOAN);
    }

    @Test
    void rejectsIncorrectAmountWithoutMarkingOrderPaid() throws Exception {
        payment.setMaGiaoDich("TT001");
        Map<String, String> ipn = signed(Map.of(
                "vnp_TmnCode", TMN_CODE,
                "vnp_TxnRef", "TT001",
                "vnp_Amount", "1250001",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "BANKTX001"));
        when(thanhToanRepository.findByMaGiaoDich("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("TT001")).thenReturn(Optional.of(payment));

        var response = service.processIpn(ipn);

        assertThat(response.rspCode()).isEqualTo("04");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CHO_THANH_TOAN);
        verify(donHangService, never()).xacNhanSauThanhToan(any());
    }

    @Test
    void failedIpnKeepsOrderWaitingForPayment() throws Exception {
        payment.setMaGiaoDich("TT001");
        Map<String, String> ipn = signed(Map.of(
                "vnp_TmnCode", TMN_CODE,
                "vnp_TxnRef", "TT001",
                "vnp_Amount", "1250000",
                "vnp_ResponseCode", "24",
                "vnp_TransactionStatus", "02"));
        when(thanhToanRepository.findByMaGiaoDich("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("TT001")).thenReturn(Optional.of(payment));

        assertThat(service.processIpn(ipn).rspCode()).isEqualTo("00");

        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.THAT_BAI);
        assertThat(order.getTrangThai()).isEqualTo(TrangThaiDonHang.CHO_THANH_TOAN);
        verify(donHangService, never()).xacNhanSauThanhToan(any());
    }

    @Test
    void allowsRetryWhilePreviousAttemptIsStillPending() {
        payment.setMaGiaoDich("TT001");

        var result = service.create("KH001", "DH001", "203.0.113.8");

        assertThat(result.transactionId()).isNotEqualTo("TT001");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CHO_THANH_TOAN);
        verify(thanhToanRepository).saveAndFlush(any(ThanhToan.class));
    }

    @Test
    void createsCheckoutUrlWithThePersistedVnPayCreationDate() throws Exception {
        LocalDateTime createDate = LocalDateTime.of(2026, 11, 27, 18, 30);

        var result = service.createCheckoutPayment("PC001", new BigDecimal("12500.00"),
                "203.0.113.8", createDate);

        Map<String, String> params = parseQuery(result.payUrl());
        assertThat(params).containsEntry("vnp_CreateDate", "20261127183000")
                .containsEntry("vnp_ExpireDate", "20261127184500");
        assertThat(params.get("vnp_SecureHash")).isEqualTo(signature(params));
    }

    @Test
    void repeatedSuccessfulIpnIsIdempotent() throws Exception {
        payment.setMaGiaoDich("TT001");
        Map<String, String> ipn = successfulIpn();
        when(thanhToanRepository.findByMaGiaoDich("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH001"))
                .thenReturn(List.of(payment));

        assertThat(service.processIpn(ipn).rspCode()).isEqualTo("00");
        assertThat(service.processIpn(ipn).rspCode()).isEqualTo("00");

        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        verify(thanhToanRepository, times(1)).save(payment);
        verify(thongBaoService, times(1)).gui(eq("KH001"),
                eq("Thanh toán VNPay thành công cho đơn hàng DH001."), eq("/orders/DH001"));
        verify(donHangService, times(1)).xacNhanSauThanhToan(order);
    }

    @Test
    void lateSuccessfulIpnForCancelledOrderMarksPaymentForRefund() throws Exception {
        payment.setMaGiaoDich("TT001");
        order.setTrangThai(TrangThaiDonHang.DA_HUY);
        when(thanhToanRepository.findByMaGiaoDich("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByMaGiaoDichForUpdate("TT001")).thenReturn(Optional.of(payment));
        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc("DH001"))
                .thenReturn(List.of(payment));

        var response = service.processIpn(successfulIpn());

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CAN_HOAN_TIEN);
        verify(thongBaoService).guiQuanTri("Giao dịch VNPay của đơn DH001 cần hoàn tiền/đối soát (mã GD: TT001).");
    }

    @Test
    void submitsSignedFullRefundAndMarksPaidOnlyAfterProviderConfirmsCompletion() throws Exception {
        RefundFixture fixture = requestRefund("00", "00");

        assertThat(fixture.result().status()).isEqualTo(TrangThaiThanhToan.DA_HOAN_TIEN.name());
        assertThat(fixture.result().refundTransactionId()).isEqualTo("REFUND_TXN_001");
        assertThat(fixture.payment().getMaYeuCauHoanVnPay()).isEqualTo(fixture.result().refundRequestId());
        assertThat(fixture.payment().getMaGiaoDichHoanVnPay()).isEqualTo("REFUND_TXN_001");
        assertThat(fixture.request())
                .containsEntry("vnp_RequestId", fixture.result().refundRequestId())
                .containsEntry("vnp_CreateBy", "ADMIN001")
                .containsEntry("vnp_Command", "refund")
                .containsEntry("vnp_TransactionType", "02")
                .containsEntry("vnp_TxnRef", "TT001")
                .containsEntry("vnp_Amount", "1250000")
                .containsEntry("vnp_TransactionNo", "123456789012345")
                .containsEntry("vnp_TransactionDate", "20261127183000")
                .containsEntry("vnp_IpAddr", "203.0.113.8");
        assertThat(fixture.result().refundRequestId()).hasSizeLessThanOrEqualTo(32);
        assertThat(fixture.request().get("vnp_SecureHash"))
                .isEqualTo(refundRequestSignature(fixture.request()));
    }

    @Test
    void keepsRefundPendingWhenVnPayHasOnlySentRequestToBank() throws Exception {
        RefundFixture fixture = requestRefund("00", "06");

        assertThat(fixture.result().status()).isEqualTo(TrangThaiThanhToan.DANG_HOAN_TIEN.name());
        assertThat(fixture.payment().getTrangThai()).isEqualTo(TrangThaiThanhToan.DANG_HOAN_TIEN);
        assertThat(fixture.payment().getMaYeuCauHoanVnPay()).isNotBlank();
    }

    @Test
    void retryingPendingRefundReusesOriginalRequestIdentityAndTimestamp() throws Exception {
        payment.setMaGiaoDich("TT001");
        payment.setMaGiaoDichNganHang("123456789012345");
        payment.setNgayTao(LocalDateTime.of(2026, 11, 27, 17, 0));
        payment.setNgayTaoGiaoDichVnPay(LocalDateTime.of(2026, 11, 27, 18, 30));
        payment.setTrangThai(TrangThaiThanhToan.DANG_HOAN_TIEN);
        payment.setMaYeuCauHoanVnPay("REFUND-REQUEST-001");
        payment.setNgayYeuCauHoanVnPay(LocalDateTime.of(2026, 11, 28, 9, 15));
        payment.setNguoiYeuCauHoanVnPay("ADMIN001");
        when(thanhToanRepository.findByMaGiaoDichForUpdate("TT001")).thenReturn(Optional.of(payment));

        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, String>> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/refund", exchange -> {
            Map<String, String> request = objectMapper.readValue(exchange.getRequestBody(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            requests.add(request);
            Map<String, String> response = new LinkedHashMap<>();
            response.put("vnp_ResponseId", "RESP001");
            response.put("vnp_Command", "refund");
            response.put("vnp_ResponseCode", "00");
            response.put("vnp_Message", "Processing.");
            response.put("vnp_TmnCode", TMN_CODE);
            response.put("vnp_TxnRef", request.get("vnp_TxnRef"));
            response.put("vnp_Amount", request.get("vnp_Amount"));
            response.put("vnp_BankCode", "NCB");
            response.put("vnp_PayDate", "20261127203000");
            response.put("vnp_TransactionNo", "REFUND_TXN_001");
            response.put("vnp_TransactionType", "02");
            response.put("vnp_TransactionStatus", "06");
            response.put("vnp_OrderInfo", request.get("vnp_OrderInfo"));
            try {
                response.put("vnp_SecureHash", refundResponseSignature(response));
            } catch (Exception ex) {
                throw new java.io.IOException(ex);
            }
            byte[] body = objectMapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ReflectionTestUtils.setField(service, "refundEndpoint",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/refund");
            ReflectionTestUtils.setField(service, "serverIp", "203.0.113.8");

            service.refundPendingPayment("DH001", "ADMIN002");
            service.refundPendingPayment("DH001", "ADMIN003");

            assertThat(requests).hasSize(2);
            assertThat(requests.get(1)).containsEntry("vnp_RequestId", requests.get(0).get("vnp_RequestId"))
                    .containsEntry("vnp_CreateDate", requests.get(0).get("vnp_CreateDate"))
                    .containsEntry("vnp_CreateBy", "ADMIN001")
                    .containsEntry("vnp_TransactionDate", "20261127183000")
                    .containsEntry("vnp_SecureHash", requests.get(0).get("vnp_SecureHash"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnUrlDoesNotConfirmPayment() throws Exception {
        payment.setMaGiaoDich("TT001");
        Map<String, String> callback = signed(Map.of(
                "vnp_TmnCode", TMN_CODE,
                "vnp_TxnRef", "TT001",
                "vnp_ResponseCode", "00"));
        when(thanhToanRepository.findByMaGiaoDich("TT001")).thenReturn(Optional.of(payment));

        String redirectUrl = service.processReturn(callback);

        assertThat(redirectUrl).isEqualTo("https://shop.example/orders/DH001?vnpayReturn=1");
        assertThat(payment.getTrangThai()).isEqualTo(TrangThaiThanhToan.CHO_THANH_TOAN);
    }

    private Map<String, String> successfulIpn() throws Exception {
        return signed(Map.of(
                "vnp_TmnCode", TMN_CODE,
                "vnp_TxnRef", "TT001",
                "vnp_Amount", "1250000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "BANKTX001"));
    }

    private static Map<String, String> parseQuery(String url) {
        String query = java.net.URI.create(url).getRawQuery();
        Map<String, String> params = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            params.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private static Map<String, String> signed(Map<String, String> params) throws Exception {
        Map<String, String> result = new LinkedHashMap<>(params);
        result.put("vnp_SecureHash", signature(result));
        return result;
    }

    private static String signature(Map<String, String> params) throws Exception {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.remove("vnp_SecureHash");
        sorted.remove("vnp_SecureHashType");
        String data = sorted.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder hex = new StringBuilder();
        for (byte value : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private RefundFixture requestRefund(String responseCode, String transactionStatus) throws Exception {
        payment.setMaGiaoDich("TT001");
        payment.setMaGiaoDichNganHang("123456789012345");
        payment.setNgayTao(LocalDateTime.of(2026, 11, 27, 17, 0, 0));
        payment.setNgayTaoGiaoDichVnPay(LocalDateTime.of(2026, 11, 27, 18, 30, 0));
        payment.setTrangThai(TrangThaiThanhToan.CAN_HOAN_TIEN);
        when(thanhToanRepository.findByMaGiaoDichForUpdate("TT001")).thenReturn(Optional.of(payment));

        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<Map<String, String>> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/refund", exchange -> {
            Map<String, String> request = objectMapper.readValue(exchange.getRequestBody(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            requestBody.set(request);
            Map<String, String> response = new LinkedHashMap<>();
            response.put("vnp_ResponseId", "RESP001");
            response.put("vnp_Command", "refund");
            response.put("vnp_ResponseCode", responseCode);
            response.put("vnp_Message", "Successful.");
            response.put("vnp_TmnCode", TMN_CODE);
            response.put("vnp_TxnRef", request.get("vnp_TxnRef"));
            response.put("vnp_Amount", request.get("vnp_Amount"));
            response.put("vnp_BankCode", "NCB");
            response.put("vnp_PayDate", "20261127203000");
            response.put("vnp_TransactionNo", "REFUND_TXN_001");
            response.put("vnp_TransactionType", "02");
            response.put("vnp_TransactionStatus", transactionStatus);
            response.put("vnp_OrderInfo", request.get("vnp_OrderInfo"));
            try {
                response.put("vnp_SecureHash", refundResponseSignature(response));
            } catch (Exception ex) {
                throw new java.io.IOException(ex);
            }
            byte[] body = objectMapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ReflectionTestUtils.setField(service, "refundEndpoint",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/refund");
            ReflectionTestUtils.setField(service, "serverIp", "203.0.113.8");
            var result = service.refundPendingPayment("DH001", "ADMIN001");
            return new RefundFixture(result, requestBody.get(), payment);
        } finally {
            server.stop(0);
        }
    }

    private static String refundRequestSignature(Map<String, String> request) throws Exception {
        return hmacRaw(String.join("|",
                request.get("vnp_RequestId"),
                request.get("vnp_Version"),
                request.get("vnp_Command"),
                request.get("vnp_TmnCode"),
                request.get("vnp_TransactionType"),
                request.get("vnp_TxnRef"),
                request.get("vnp_Amount"),
                request.get("vnp_TransactionNo"),
                request.get("vnp_TransactionDate"),
                request.get("vnp_CreateBy"),
                request.get("vnp_CreateDate"),
                request.get("vnp_IpAddr"),
                request.get("vnp_OrderInfo")));
    }

    private static String refundResponseSignature(Map<String, String> response) throws Exception {
        return hmacRaw(String.join("|",
                response.get("vnp_ResponseId"),
                response.get("vnp_Command"),
                response.get("vnp_ResponseCode"),
                response.get("vnp_Message"),
                response.get("vnp_TmnCode"),
                response.get("vnp_TxnRef"),
                response.get("vnp_Amount"),
                response.get("vnp_BankCode"),
                response.get("vnp_PayDate"),
                response.get("vnp_TransactionNo"),
                response.get("vnp_TransactionType"),
                response.get("vnp_TransactionStatus"),
                response.get("vnp_OrderInfo")));
    }

    private static String hmacRaw(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        StringBuilder hex = new StringBuilder();
        for (byte value : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private record RefundFixture(
            nhom10.phonenest.dto.VnPayRefundStatusDto result,
            Map<String, String> request,
            ThanhToan payment) {
    }
}
