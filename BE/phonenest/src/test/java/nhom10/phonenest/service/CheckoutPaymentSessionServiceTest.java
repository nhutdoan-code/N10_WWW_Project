package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nhom10.phonenest.dto.CheckoutDto;
import nhom10.phonenest.dto.CheckoutPaymentPayload;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.PhienThanhToan;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiPhienThanhToan;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.PhienThanhToanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutPaymentSessionServiceTest {
    private static final String CUSTOMER_ID = "KH001";
    private static final String SESSION_ID = "PC001";
    private static final String IDEMPOTENCY_KEY = "123e4567-e89b-42d3-a456-426614174000";
    private static final BigDecimal AMOUNT = new BigDecimal("1100.00");

    @Mock
    private PhienThanhToanRepository sessionRepository;
    @Mock
    private KhachHangRepository khachHangRepository;
    @Mock
    private ThanhToanService thanhToanService;
    @Mock
    private ThanhToanDaChonService thanhToanDaChonService;
    @Mock
    private CheckoutIdempotencyService checkoutIdempotencyService;
    @Mock
    private CheckoutPaymentOrderFinalizer orderFinalizer;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private NhatKyHoatDongService nhatKyHoatDongService;

    private CheckoutPaymentSessionService service;
    private KhachHang customer;
    private DonHangDto.Checkout checkout;

    @BeforeEach
    void setUp() {
        service = new CheckoutPaymentSessionService(
                sessionRepository,
                khachHangRepository,
                thanhToanService,
                thanhToanDaChonService,
                checkoutIdempotencyService,
                orderFinalizer,
                new ObjectMapper(),
                eventPublisher,
                nhatKyHoatDongService);
        customer = new KhachHang();
        customer.setMaKh(CUSTOMER_ID);
        checkout = new DonHangDto.Checkout(PhuongThucTt.VI_DIEN_TU, "DC001", null, null, null, null);
    }

    @Test
    void checkoutStartPersistsSessionWithoutCreatingOrder() {
        when(thanhToanService.xemTruoc(CUSTOMER_ID, "DC001", null, null, PhuongThucTt.VI_DIEN_TU))
                .thenReturn(preview());
        when(sessionRepository.findByKhachHangMaKhAndIdempotencyKey(CUSTOMER_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(khachHangRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(sessionRepository.saveAndFlush(any(PhienThanhToan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PhienThanhToan session = service.prepare(CUSTOMER_ID, IDEMPOTENCY_KEY, checkout, null);

        assertThat(session.getTrangThai()).isEqualTo(TrangThaiPhienThanhToan.CHO_THANH_TOAN);
        assertThat(session.getSoTien()).isEqualByComparingTo(AMOUNT);
        assertThat(session.getDuLieuCheckout()).contains("PB001").contains("\"soLuong\":1");
        verify(orderFinalizer, never()).finalizePaidCheckout(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void failedProviderCallbackMarksSessionFailedWithoutCreatingOrder() {
        PhienThanhToan session = pendingSession();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByMaPhienThanhToanForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        var result = service.processCallback(
                SESSION_ID, PhuongThucTt.VI_DIEN_TU, AMOUNT, false, "MOMO-TXN-1");

        assertThat(result).hasValueSatisfying(callback ->
                assertThat(callback.status()).isEqualTo(CheckoutPaymentCallbackResult.Status.PAYMENT_FAILED));
        assertThat(session.getTrangThai()).isEqualTo(TrangThaiPhienThanhToan.THAT_BAI);
        verify(sessionRepository).save(session);
        verifyNoInteractions(orderFinalizer);
    }

    @Test
    void successfulCallbackCreatesOrderOnce() {
        checkout = new DonHangDto.Checkout(PhuongThucTt.CHUYEN_KHOAN, "DC001", null, null, null, null);
        PhienThanhToan session = pendingSession();
        LocalDateTime createDate = LocalDateTime.of(2026, 11, 27, 18, 30);
        session.setNgayTaoGiaoDichVnPay(createDate);
        DonHangDto order = orderDto();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByMaPhienThanhToanForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(orderFinalizer.finalizePaidCheckout(
                eq(CUSTOMER_ID), eq(SESSION_ID), eq(session.getDuLieuCheckout()), eq(AMOUNT),
                eq("VNPAY-TXN-1"), eq(PhuongThucTt.CHUYEN_KHOAN), eq(createDate)))
                .thenReturn(order);

        var result = service.processCallback(
                SESSION_ID, PhuongThucTt.CHUYEN_KHOAN, AMOUNT, true, "VNPAY-TXN-1");

        assertThat(result).hasValueSatisfying(callback ->
                assertThat(callback.status()).isEqualTo(CheckoutPaymentCallbackResult.Status.ORDER_CREATED));
        assertThat(session.getTrangThai()).isEqualTo(TrangThaiPhienThanhToan.DA_TAO_DON);
        assertThat(session.getMaDonHang()).isEqualTo("DH001");

        service.processCallback(SESSION_ID, PhuongThucTt.CHUYEN_KHOAN, AMOUNT, true, "VNPAY-TXN-1");
        verify(orderFinalizer, times(1)).finalizePaidCheckout(
                any(), any(), any(), any(), any(), any(), any());
        verify(orderFinalizer).finalizePaidCheckout(
                eq(CUSTOMER_ID), eq(SESSION_ID), eq(session.getDuLieuCheckout()), eq(AMOUNT),
                eq("VNPAY-TXN-1"), eq(PhuongThucTt.CHUYEN_KHOAN), eq(createDate));
    }

    @Test
    void vnpayCreateDateIsSavedAndReusedForThePaymentUrl() {
        PhienThanhToan session = pendingSession();
        when(sessionRepository.findByMaPhienThanhToanForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);

        LocalDateTime first = service.getOrCreateVnPayCreateDate(SESSION_ID);
        LocalDateTime second = service.getOrCreateVnPayCreateDate(SESSION_ID);

        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
        assertThat(session.getNgayTaoGiaoDichVnPay()).isEqualTo(first);
        verify(sessionRepository, times(1)).save(session);
    }

    @Test
    void adminCanRecordManualRefundForPendingSession() {
        PhienThanhToan session = pendingSession();
        session.setPhuongThuc(PhuongThucTt.CHUYEN_KHOAN);
        session.setTrangThai(TrangThaiPhienThanhToan.CAN_HOAN_TIEN);
        when(sessionRepository.findByMaPhienThanhToanForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        var result = service.recordManualRefund(SESSION_ID, " BANK-REFUND-1 ");

        assertThat(result.status()).isEqualTo(TrangThaiPhienThanhToan.DA_HOAN_TIEN.name());
        assertThat(session.getMaGiaoDichHoanTien()).isEqualTo("BANK-REFUND-1");
        verify(nhatKyHoatDongService).ghi(
                eq(HanhDongAudit.REFUND_PAYMENT),
                eq(LoaiDoiTuongAudit.PAYMENT),
                eq(SESSION_ID),
                anyMap(),
                anyMap());
    }

    private CheckoutDto preview() {
        return new CheckoutDto(
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                null,
                null,
                null,
                List.of(),
                List.of(new CheckoutDto.Dong("PB001", "Phone", null, 1, new BigDecimal("1000.00"))),
                null);
    }

    private PhienThanhToan pendingSession() {
        PhienThanhToan session = new PhienThanhToan();
        session.setMaPhienThanhToan(SESSION_ID);
        session.setKhachHang(customer);
        session.setPhuongThuc(checkout.phuongThucTt());
        session.setSoTien(AMOUNT);
        session.setTrangThai(TrangThaiPhienThanhToan.CHO_THANH_TOAN);
        session.setDuLieuCheckout(new ObjectMapper().valueToTree(
                new CheckoutPaymentPayload(
                        checkout,
                        List.of(new CheckoutPaymentPayload.CheckoutLine("PB001", 1))))
                .toString());
        return session;
    }

    private DonHangDto orderDto() {
        LocalDateTime now = LocalDateTime.now();
        return new DonHangDto(
                "DH001", CUSTOMER_ID, null, null, now, PhuongThucTt.VI_DIEN_TU,
                TrangThaiDonHang.CHO_XAC_NHAN, AMOUNT, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, AMOUNT, "Address", now, null, "Customer",
                null, null, null, null, List.of());
    }
}
