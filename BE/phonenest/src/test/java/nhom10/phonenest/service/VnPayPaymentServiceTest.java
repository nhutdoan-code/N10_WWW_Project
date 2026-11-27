package nhom10.phonenest.service;

import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.ThanhToanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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
                donHangRepository, thanhToanRepository, donHangService, thongBaoService, checkoutPaymentSessionService);
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
}
