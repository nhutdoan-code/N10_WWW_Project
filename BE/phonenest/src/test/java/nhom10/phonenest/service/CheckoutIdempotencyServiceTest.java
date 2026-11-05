package nhom10.phonenest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.CheckoutIdempotency;
import nhom10.phonenest.enums.IdempotencyStatus;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.CheckoutIdempotencyRepository;
import nhom10.phonenest.repository.DonHangRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutIdempotencyServiceTest {

    @Mock
    private CheckoutIdempotencyRepository repository;

    @Mock
    private CheckoutIdempotencyTxHelper txHelper;

    @Mock
    private DonHangService donHangService;

    @Mock
    private DonHangRepository donHangRepository;

    private ObjectMapper objectMapper;
    private CheckoutIdempotencyService idempotencyService;

    private static final String USER_ID = "KH001";
    private static final String VALID_KEY = "123e4567-e89b-12d3-a456-426614174000";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        idempotencyService = new CheckoutIdempotencyService(
                repository,
                txHelper,
                donHangService,
                donHangRepository,
                objectMapper
        );
    }

    private DonHangDto createSampleDto(String maDonHang) {
        return new DonHangDto(
                maDonHang,
                USER_ID,
                "KM01",
                null,
                LocalDateTime.now(),
                PhuongThucTt.COD,
                TrangThaiDonHang.CHO_XAC_NHAN,
                new BigDecimal("25000000"),
                new BigDecimal("500000"),
                new BigDecimal("30000"),
                BigDecimal.ZERO,
                new BigDecimal("24530000"),
                "123 Nguyễn Huệ, Q1, TP.HCM",
                LocalDateTime.now(),
                null,
                "Nguyễn Văn A",
                null,
                null,
                null,
                null,
                List.of(new DonHangDto.Dong("SP01", "PB01", "iPhone 15", "/img.jpg", 1, new BigDecimal("25000000")))
        );
    }

    @Test
    @DisplayName("Test 1: Checkout một lần thành công (Single checkout success)")
    void test01_singleCheckout_Success() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "KM01", null, "PB001", 1
        );
        DonHangDto expectedDto = createSampleDto("DH001");

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.empty());

        CheckoutIdempotency inProgress = new CheckoutIdempotency();
        inProgress.setId(100L);
        inProgress.setUserId(USER_ID);
        inProgress.setIdempotencyKey(VALID_KEY);
        inProgress.setStatus(IdempotencyStatus.IN_PROGRESS);

        when(txHelper.saveNewInProgress(eq(USER_ID), eq(VALID_KEY), anyString())).thenReturn(inProgress);
        when(donHangService.datHang(USER_ID, req, 100L)).thenReturn(expectedDto);

        DonHangDto result = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);

        assertThat(result).isNotNull();
        assertThat(result.maDonHang()).isEqualTo("DH001");
        verify(txHelper).saveNewInProgress(eq(USER_ID), eq(VALID_KEY), anyString());
        verify(donHangService, times(1)).datHang(USER_ID, req, 100L);
    }

    @Test
    @DisplayName("Test 2: Retry cùng key và payload trả cùng order ID (Idempotent replay)")
    void test02_retrySameKeySamePayload_ReturnsExistingOrderWithoutRecreating() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "KM01", null, "PB001", 1
        );
        String fingerprint = idempotencyService.computeFingerprint(USER_ID, req);
        DonHangDto cachedDto = createSampleDto("DH001");
        String jsonPayload = objectMapper.writeValueAsString(cachedDto);

        CheckoutIdempotency existing = new CheckoutIdempotency();
        existing.setId(100L);
        existing.setUserId(USER_ID);
        existing.setIdempotencyKey(VALID_KEY);
        existing.setRequestHash(fingerprint);
        existing.setStatus(IdempotencyStatus.SUCCESS);
        existing.setOrderId("DH001");
        existing.setResponsePayload(jsonPayload);

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(existing));

        DonHangDto result = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);

        assertThat(result).isNotNull();
        assertThat(result.maDonHang()).isEqualTo("DH001");
        assertThat(result.tongTien()).isEqualTo(cachedDto.tongTien());
        verify(donHangService, never()).datHang(anyString(), any(), any());
        verify(txHelper, never()).saveNewInProgress(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Test 3: Cùng key nhưng khác payload ném 409 Conflict với mã lỗi IDEMPOTENCY_PAYLOAD_MISMATCH")
    void test03_sameKeyDifferentPayload_Throws409ConflictWithPayloadMismatchCode() {
        DonHangDto.Checkout reqOriginal = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "KM01", null, "PB001", 1
        );
        String originalFingerprint = idempotencyService.computeFingerprint(USER_ID, reqOriginal);

        CheckoutIdempotency existing = new CheckoutIdempotency();
        existing.setId(100L);
        existing.setUserId(USER_ID);
        existing.setIdempotencyKey(VALID_KEY);
        existing.setRequestHash(originalFingerprint);
        existing.setStatus(IdempotencyStatus.SUCCESS);
        existing.setOrderId("DH001");

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(existing));

        DonHangDto.Checkout reqModified = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC002", "KM01", null, "PB001", 1
        );

        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, reqModified))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException appEx = (AppException) e;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(appEx.getCode()).isEqualTo(CheckoutIdempotencyService.CODE_PAYLOAD_MISMATCH);
                })
                .hasMessageContaining("Idempotency-Key đã được sử dụng với thông tin đặt hàng khác");

        verify(donHangService, never()).datHang(anyString(), any(), any());
    }

    @Test
    @DisplayName("Test 4: Hai khách khác nhau dùng cùng key không ảnh hưởng nhau")
    void test04_twoDifferentUsers_SameKey_BothSucceed() {
        String userA = "KH001";
        String userB = "KH002";
        String commonKey = "33333333-3333-3333-3333-333333333333";

        DonHangDto.Checkout reqA = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, "PB01", 1);
        DonHangDto.Checkout reqB = new DonHangDto.Checkout(PhuongThucTt.COD, "DC002", null, null, "PB02", 1);

        DonHangDto dtoA = createSampleDto("DH001");
        DonHangDto dtoB = createSampleDto("DH002");

        when(repository.findByUserIdAndIdempotencyKey(userA, commonKey)).thenReturn(Optional.empty());
        when(repository.findByUserIdAndIdempotencyKey(userB, commonKey)).thenReturn(Optional.empty());

        CheckoutIdempotency recA = new CheckoutIdempotency(); recA.setId(1L);
        CheckoutIdempotency recB = new CheckoutIdempotency(); recB.setId(2L);
        when(txHelper.saveNewInProgress(eq(userA), eq(commonKey), anyString())).thenReturn(recA);
        when(txHelper.saveNewInProgress(eq(userB), eq(commonKey), anyString())).thenReturn(recB);

        when(donHangService.datHang(userA, reqA, 1L)).thenReturn(dtoA);
        when(donHangService.datHang(userB, reqB, 2L)).thenReturn(dtoB);

        DonHangDto resA = idempotencyService.executeIdempotentCheckout(userA, commonKey, reqA);
        DonHangDto resB = idempotencyService.executeIdempotentCheckout(userB, commonKey, reqB);

        assertThat(resA.maDonHang()).isEqualTo("DH001");
        assertThat(resB.maDonHang()).isEqualTo("DH002");
        verify(donHangService).datHang(userA, reqA, 1L);
        verify(donHangService).datHang(userB, reqB, 2L);
    }

    @Test
    @DisplayName("Test 5: 10 request đồng thời cùng key chỉ tạo một đơn (Concurrency race-condition protection)")
    void test05_tenConcurrentRequests_SameKey_OnlyOneCreatesOrder() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 1
        );
        DonHangDto dto = createSampleDto("DH001");

        AtomicInteger insertAttempts = new AtomicInteger(0);
        when(repository.findByUserIdAndIdempotencyKey(eq(USER_ID), eq(VALID_KEY)))
                .thenAnswer(inv -> {
                    if (insertAttempts.get() > 0) {
                        CheckoutIdempotency active = new CheckoutIdempotency();
                        active.setId(1L);
                        active.setUserId(USER_ID);
                        active.setIdempotencyKey(VALID_KEY);
                        active.setStatus(IdempotencyStatus.IN_PROGRESS);
                        active.setCreatedAt(LocalDateTime.now());
                        active.setUpdatedAt(LocalDateTime.now());
                        return Optional.of(active);
                    }
                    return Optional.empty();
                });

        when(txHelper.saveNewInProgress(eq(USER_ID), eq(VALID_KEY), anyString()))
                .thenAnswer(inv -> {
                    if (insertAttempts.incrementAndGet() == 1) {
                        CheckoutIdempotency rec = new CheckoutIdempotency();
                        rec.setId(1L);
                        rec.setStatus(IdempotencyStatus.IN_PROGRESS);
                        rec.setCreatedAt(LocalDateTime.now());
                        rec.setUpdatedAt(LocalDateTime.now());
                        return rec;
                    }
                    throw new DataIntegrityViolationException("Duplicate entry for key uq_user_idempotency");
                });

        when(donHangService.datHang(USER_ID, req, 1L)).thenReturn(dto);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    return idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);
                } catch (Exception e) {
                    return e;
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        int successCount = 0;
        int conflictCount = 0;

        for (Future<Object> f : futures) {
            Object res = f.get();
            if (res instanceof DonHangDto d) {
                successCount++;
                assertThat(d.maDonHang()).isEqualTo("DH001");
            } else if (res instanceof AppException ex) {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ex.getCode()).isEqualTo(CheckoutIdempotencyService.CODE_IN_PROGRESS);
                conflictCount++;
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(9);
        verify(donHangService, times(1)).datHang(USER_ID, req, 1L);
    }

    @Test
    @DisplayName("Test 6: Timeout sau commit, retry trả lại đơn cũ mà không sinh đơn mới")
    void test06_timeoutAfterCommit_RetryReturnsOldOrder() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 1
        );
        String fingerprint = idempotencyService.computeFingerprint(USER_ID, req);
        DonHangDto committedDto = createSampleDto("DH_COMMITTED_999");
        String jsonPayload = objectMapper.writeValueAsString(committedDto);

        CheckoutIdempotency committed = new CheckoutIdempotency();
        committed.setId(50L);
        committed.setUserId(USER_ID);
        committed.setIdempotencyKey(VALID_KEY);
        committed.setRequestHash(fingerprint);
        committed.setStatus(IdempotencyStatus.SUCCESS);
        committed.setOrderId("DH_COMMITTED_999");
        committed.setResponsePayload(jsonPayload);

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(committed));

        DonHangDto result = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);

        assertThat(result.maDonHang()).isEqualTo("DH_COMMITTED_999");
        verify(donHangService, never()).datHang(anyString(), any(), any());
    }

    @Test
    @DisplayName("Test 7: Rollback transaction, FAILED chỉ được ghi sau khi transaction rollback; retry an toàn")
    void test07_rollbackTransaction_MarkFailedAfterRollback_RetrySafe() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 1
        );
        String fingerprint = idempotencyService.computeFingerprint(USER_ID, req);

        CheckoutIdempotency inProgress = new CheckoutIdempotency();
        inProgress.setId(77L);
        inProgress.setUserId(USER_ID);
        inProgress.setIdempotencyKey(VALID_KEY);
        inProgress.setStatus(IdempotencyStatus.IN_PROGRESS);

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.empty());
        when(txHelper.saveNewInProgress(eq(USER_ID), eq(VALID_KEY), anyString())).thenReturn(inProgress);

        when(donHangService.datHang(USER_ID, req, 77L))
                .thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Tồn kho không đủ"))
                .thenReturn(createSampleDto("DH_RETRY_SUCCESS"));

        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST);

        verify(txHelper).markFailed(77L);

        // Lần 2: Người dùng retry lại sau khi sửa lỗi, bản ghi ở trạng thái FAILED
        CheckoutIdempotency failedRecord = new CheckoutIdempotency();
        failedRecord.setId(77L);
        failedRecord.setUserId(USER_ID);
        failedRecord.setIdempotencyKey(VALID_KEY);
        failedRecord.setStatus(IdempotencyStatus.FAILED);
        failedRecord.setRequestHash(fingerprint);

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(failedRecord));
        when(txHelper.transitionFromFailedToInProgress(77L, fingerprint)).thenReturn(true);

        DonHangDto retryResult = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);

        assertThat(retryResult).isNotNull();
        assertThat(retryResult.maDonHang()).isEqualTo("DH_RETRY_SUCCESS");
        verify(txHelper).transitionFromFailedToInProgress(77L, fingerprint);
    }

    @Test
    @DisplayName("Test 8, 9, 10: Retry không trừ kho, không ghi nhận voucher, không tạo payment lần hai")
    void test08_09_10_retryDoesNotInvokeDatHangAgain() throws Exception {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.VI_DIEN_TU, "DC001", "KM01", "KM_SHIP", "PB001", 1
        );
        String fingerprint = idempotencyService.computeFingerprint(USER_ID, req);
        DonHangDto existingDto = createSampleDto("DH001");

        CheckoutIdempotency existing = new CheckoutIdempotency();
        existing.setId(99L);
        existing.setUserId(USER_ID);
        existing.setIdempotencyKey(VALID_KEY);
        existing.setRequestHash(fingerprint);
        existing.setStatus(IdempotencyStatus.SUCCESS);
        existing.setOrderId("DH001");
        existing.setResponsePayload(objectMapper.writeValueAsString(existingDto));

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(existing));

        // Gọi 3 lần liên tiếp với cùng key
        DonHangDto res1 = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);
        DonHangDto res2 = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);
        DonHangDto res3 = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);

        assertThat(res1.maDonHang()).isEqualTo("DH001");
        assertThat(res2.maDonHang()).isEqualTo("DH001");
        assertThat(res3.maDonHang()).isEqualTo("DH001");

        verify(donHangService, never()).datHang(anyString(), any(), any());
    }

    @Test
    @DisplayName("Test 11: Thiếu hoặc rỗng header Idempotency-Key ném 400 Bad Request")
    void test11_missingOrBlankKey_Throws400BadRequest() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, null, req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                .hasMessageContaining("Thiếu header Idempotency-Key bắt buộc");

        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, "   ", req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                .hasMessageContaining("Thiếu header Idempotency-Key bắt buộc");
    }

    @Test
    @DisplayName("Test 12: Idempotency-Key không đúng định dạng UUID ném 400 Bad Request")
    void test12_invalidUuidKey_Throws400BadRequest() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, "not-a-valid-uuid", req))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                .hasMessageContaining("định dạng UUID hợp lệ");
    }

    @Test
    @DisplayName("Test 13: Request A đang chạy, request B retry cùng key bị từ chối 409 với mã IDEMPOTENCY_IN_PROGRESS")
    void test13_requestA_Running_RequestB_RejectedWith409() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        CheckoutIdempotency inProgress = new CheckoutIdempotency();
        inProgress.setId(10L);
        inProgress.setUserId(USER_ID);
        inProgress.setIdempotencyKey(VALID_KEY);
        inProgress.setStatus(IdempotencyStatus.IN_PROGRESS);
        inProgress.setUpdatedAt(LocalDateTime.now().minusSeconds(15));

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(CheckoutIdempotencyService.CODE_IN_PROGRESS);
                })
                .hasMessageContaining("đang được xử lý hoặc đang chờ đối soát, vui lòng không gửi lại");

        verify(donHangService, never()).datHang(anyString(), any(), any());
    }

    @Test
    @DisplayName("Test 14: Request A chạy quá 2 phút chưa xong, request B retry cùng key VẪN BỊ TỪ CHỐI 409, không tự ý chạy lại")
    void test14_requestA_RunsLongerThan2Minutes_RequestB_StillRejected409_NoDuplicateOrder() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        CheckoutIdempotency longRunning = new CheckoutIdempotency();
        longRunning.setId(10L);
        longRunning.setUserId(USER_ID);
        longRunning.setIdempotencyKey(VALID_KEY);
        longRunning.setStatus(IdempotencyStatus.IN_PROGRESS);
        longRunning.setUpdatedAt(LocalDateTime.now().minusMinutes(5)); // đã chạy 5 phút

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(longRunning));

        // Request B retry cùng key không bao giờ được tự ý re-execute datHang()
        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(CheckoutIdempotencyService.CODE_IN_PROGRESS);
                })
                .hasMessageContaining("đang được xử lý hoặc đang chờ đối soát, vui lòng không gửi lại");

        verify(donHangService, never()).datHang(anyString(), any(), any());
    }

    @Test
    @DisplayName("Test 15: Hai request cùng retry một record FAILED -> CAS bảo vệ chỉ cho phép 1 request thực thi, request kia nhận 409")
    void test15_failedRetry_CASRaceCondition_OneWinsOneReceives409() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);
        String fingerprint = idempotencyService.computeFingerprint(USER_ID, req);

        CheckoutIdempotency failed = new CheckoutIdempotency();
        failed.setId(20L);
        failed.setUserId(USER_ID);
        failed.setIdempotencyKey(VALID_KEY);
        failed.setStatus(IdempotencyStatus.FAILED);
        failed.setRequestHash(fingerprint);

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(failed));
        // Giả lập request 2 thất bại trong CAS transition do request 1 đã claim
        when(txHelper.transitionFromFailedToInProgress(20L, fingerprint)).thenReturn(false);

        assertThatThrownBy(() -> idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(CheckoutIdempotencyService.CODE_IN_PROGRESS);
                });

        verify(donHangService, never()).datHang(anyString(), any(), any());
    }

    @Test
    @DisplayName("Test 16: Reconciliation phát hiện order_id đã commit thực tế -> trả lại đơn cũ mà không gọi datHang")
    void test16_reconciliation_ReturnsCommittedOrderWithoutRecreating() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(PhuongThucTt.COD, "DC001", null, null, null, null);

        CheckoutIdempotency inProgressWithOrder = new CheckoutIdempotency();
        inProgressWithOrder.setId(10L);
        inProgressWithOrder.setUserId(USER_ID);
        inProgressWithOrder.setIdempotencyKey(VALID_KEY);
        inProgressWithOrder.setStatus(IdempotencyStatus.IN_PROGRESS);
        inProgressWithOrder.setOrderId("DH_COMMITTED_PREVIOUSLY");
        inProgressWithOrder.setUpdatedAt(LocalDateTime.now().minusMinutes(5));

        when(repository.findByUserIdAndIdempotencyKey(USER_ID, VALID_KEY)).thenReturn(Optional.of(inProgressWithOrder));
        when(donHangRepository.existsById("DH_COMMITTED_PREVIOUSLY")).thenReturn(true);
        when(donHangService.chiTiet(USER_ID, "DH_COMMITTED_PREVIOUSLY")).thenReturn(createSampleDto("DH_COMMITTED_PREVIOUSLY"));

        DonHangDto res = idempotencyService.executeIdempotentCheckout(USER_ID, VALID_KEY, req);

        assertThat(res.maDonHang()).isEqualTo("DH_COMMITTED_PREVIOUSLY");
        verify(donHangService, never()).datHang(anyString(), any(), any());
    }

    @Test
    @DisplayName("Test 17: Fingerprint canonicalization chuẩn hóa chữ hoa thường và khoảng trắng")
    void test17_fingerprint_Canonicalization_Consistency() {
        DonHangDto.Checkout req1 = new DonHangDto.Checkout(
                PhuongThucTt.COD, " DC001 ", "km01", "km_ship", " PB001 ", 1
        );
        DonHangDto.Checkout req2 = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "KM01", "KM_SHIP", "PB001", 1
        );

        String fp1 = idempotencyService.computeFingerprint(" KH001 ", req1);
        String fp2 = idempotencyService.computeFingerprint("KH001", req2);

        assertThat(fp1).isEqualTo(fp2);
    }
}
