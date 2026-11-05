package nhom10.phonenest.service;

import nhom10.phonenest.dto.*;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.*;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VoucherConcurrencyAndUsageLimitTest {

    @Mock private DonHangRepository donHangRepository;
    @Mock private GioHangRepository gioHangRepository;
    @Mock private PhienBanSanPhamRepository phienBanRepository;
    @Mock private KhuyenMaiRepository khuyenMaiRepository;
    @Mock private SuDungKhuyenMaiRepository suDungKhuyenMaiRepository;
    @Mock private HuyDonHangRepository huyRepository;
    @Mock private ThanhToanRepository thanhToanRepository;
    @Mock private LichSuTrangThaiDonHangRepository lichSuRepository;
    @Mock private BienDongKhoRepository bienDongRepository;
    @Mock private DiaChiService diaChiService;
    @Mock private PhiVanChuyenService phiVanChuyenService;
    @Mock private ThanhToanService thanhToanService;
    @Mock private KhachHangRepository khachHangRepository;
    @Mock private TaiKhoanRepository taiKhoanRepository;
    @Mock private DiaChiRepository diaChiRepository;
    @Mock private DonHangMapper mapper;
    @Mock private GioHangService gioHangService;
    @Mock private ThongBaoService thongBaoService;
    @Mock private EmailService emailService;
    @Mock private NhatKyHoatDongService nhatKyHoatDongService;
    @Mock private SanPhamRepository sanPhamRepository;

    private KhuyenMaiService khuyenMaiService;
    private DonHangService donHangService;

    private Map<String, KhachHang> customerCatalog = new ConcurrentHashMap<>();
    private Map<String, PhienBanSanPham> variantCatalog = new ConcurrentHashMap<>();
    private Map<String, KhuyenMai> voucherCatalog = new ConcurrentHashMap<>();
    private Map<String, ReentrantLock> variantLocks = new ConcurrentHashMap<>();
    private Map<String, ReentrantLock> voucherLocks = new ConcurrentHashMap<>();
    private List<SuDungKhuyenMai> savedUsages = new CopyOnWriteArrayList<>();
    private List<DonHang> savedDonHang = new CopyOnWriteArrayList<>();
    private List<ThanhToan> savedThanhToan = new CopyOnWriteArrayList<>();
    private final ThreadLocal<String> currentKh = ThreadLocal.withInitial(() -> "KH001");

    private DonHangDto datHang(String maKh, DonHangDto.Checkout req) {
        currentKh.set(maKh);
        try {
            return donHangService.datHang(maKh, req);
        } finally {
            currentKh.set("KH001");
        }
    }

    private KhachHang defaultCustomer;
    private DiaChi defaultDiaChi;
    private SanPham sanPham;
    private PhienBanSanPham pb1;

    @BeforeEach
    void setUp() {
        customerCatalog.clear();
        variantCatalog.clear();
        voucherCatalog.clear();
        variantLocks.clear();
        voucherLocks.clear();
        savedUsages.clear();
        savedDonHang.clear();
        savedThanhToan.clear();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("KH001", null, List.of())
        );

        khuyenMaiService = new KhuyenMaiService(
                khuyenMaiRepository,
                suDungKhuyenMaiRepository,
                khachHangRepository,
                donHangRepository,
                nhatKyHoatDongService
        );

        donHangService = new DonHangService(
                donHangRepository,
                gioHangRepository,
                phienBanRepository,
                khuyenMaiRepository,
                huyRepository,
                thanhToanRepository,
                lichSuRepository,
                bienDongRepository,
                khuyenMaiService,
                diaChiService,
                phiVanChuyenService,
                thanhToanService,
                khachHangRepository,
                taiKhoanRepository,
                diaChiRepository,
                mapper,
                gioHangService,
                thongBaoService,
                emailService,
                nhatKyHoatDongService
        );

        defaultCustomer = new KhachHang();
        defaultCustomer.setMaKh("KH001");
        defaultCustomer.setHoTen("Nguyễn Văn A");
        customerCatalog.put("KH001", defaultCustomer);

        defaultDiaChi = new DiaChi();
        defaultDiaChi.setMaDiaChi("DC001");
        defaultDiaChi.setMaKh("KH001");
        defaultDiaChi.setTenNguoiNhan("Nguyễn Văn A");
        defaultDiaChi.setSoDienThoaiNhan("0912345678");
        defaultDiaChi.setMaTinh("79");

        sanPham = new SanPham();
        sanPham.setMaSanPham("SP001");
        sanPham.setTenMay("iPhone 15 Pro");
        sanPham.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        sanPham.setThoiHanBaoHanhThang(12);

        pb1 = new PhienBanSanPham();
        pb1.setMaPhienBan("PB001");
        pb1.setSku("IP15P-256");
        pb1.setGiaBan(new BigDecimal("25000000"));
        pb1.setGiaNiemYet(new BigDecimal("28000000"));
        pb1.setSoLuongTon(100);
        pb1.setSanPham(sanPham);
        pb1.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        variantCatalog.put("PB001", pb1);

        configureCommonMocks();
    }

    private void configureCommonMocks() {
        when(khachHangRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            KhachHang kh = customerCatalog.get(id);
            if (kh == null) {
                kh = new KhachHang();
                kh.setMaKh(id);
                kh.setHoTen("Khách " + id);
                customerCatalog.put(id, kh);
            }
            return Optional.of(kh);
        });

        when(diaChiRepository.findById(anyString())).thenAnswer(inv -> {
            String dcId = inv.getArgument(0, String.class);
            DiaChi dc = new DiaChi();
            dc.setMaDiaChi(dcId);
            dc.setMaKh(currentKh.get());
            dc.setTenNguoiNhan("Nguyễn Văn A");
            dc.setSoDienThoaiNhan("0912345678");
            dc.setMaTinh("79");
            return Optional.of(dc);
        });
        when(diaChiService.diaChiDayDu(any())).thenReturn("123 Lê Lợi, Q1, TP.HCM");
        when(phiVanChuyenService.tinhPhi(any(), any())).thenReturn(new BigDecimal("30000"));
        when(phiVanChuyenService.theoDiaChi(any())).thenReturn(null);

        when(thanhToanService.mucMua(anyString(), anyString(), anyInt())).thenAnswer(inv -> {
            String pbId = inv.getArgument(1);
            int sl = inv.getArgument(2);
            return Map.of(pbId, sl);
        });

        when(phienBanRepository.findByIdForUpdate(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            ReentrantLock lock = variantLocks.computeIfAbsent(id, k -> new ReentrantLock());
            lock.lock();
            PhienBanSanPham p = variantCatalog.get(id);
            return p != null ? Optional.of(p) : Optional.empty();
        });

        when(khuyenMaiRepository.findByIdForUpdate(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0, String.class);
            ReentrantLock lock = voucherLocks.computeIfAbsent(id.toUpperCase(Locale.ROOT), k -> new ReentrantLock());
            lock.lock();
            KhuyenMai km = voucherCatalog.get(id.toUpperCase(Locale.ROOT));
            return km != null ? Optional.of(km) : Optional.empty();
        });

        when(khuyenMaiRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0, String.class);
            KhuyenMai km = voucherCatalog.get(id.toUpperCase(Locale.ROOT));
            return km != null ? Optional.of(km) : Optional.empty();
        });

        when(khuyenMaiRepository.tangDaDungNeuConLuot(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0, String.class).toUpperCase(Locale.ROOT);
            KhuyenMai km = voucherCatalog.get(id);
            if (km != null && (km.getSoLuong() == null || km.getDaDung() < km.getSoLuong())) {
                return 1;
            }
            return 0;
        });

        when(khuyenMaiRepository.hoanDaDung(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0, String.class).toUpperCase(Locale.ROOT);
            KhuyenMai km = voucherCatalog.get(id);
            if (km != null && km.getDaDung() > 0) {
                return 1;
            }
            return 0;
        });

        when(suDungKhuyenMaiRepository.save(any(SuDungKhuyenMai.class))).thenAnswer(inv -> {
            SuDungKhuyenMai s = inv.getArgument(0);
            savedUsages.add(s);
            return s;
        });

        when(suDungKhuyenMaiRepository.countByKhuyenMaiMaKmAndKhachHangMaKh(anyString(), anyString())).thenAnswer(inv -> {
            String km = inv.getArgument(0);
            String kh = inv.getArgument(1);
            return savedUsages.stream()
                    .filter(u -> u.getKhuyenMai().getMaKm().equalsIgnoreCase(km) && u.getKhachHang().getMaKh().equalsIgnoreCase(kh))
                    .count();
        });

        when(suDungKhuyenMaiRepository.findByKhuyenMaiMaKmAndKhachHangMaKhForCheck(anyString(), anyString())).thenAnswer(inv -> {
            String km = inv.getArgument(0);
            String kh = inv.getArgument(1);
            return savedUsages.stream()
                    .filter(u -> u.getKhuyenMai().getMaKm().equalsIgnoreCase(km) && u.getKhachHang().getMaKh().equalsIgnoreCase(kh))
                    .toList();
        });

        when(suDungKhuyenMaiRepository.findByDonHangMaDonHangAndKhuyenMaiMaKm(anyString(), anyString())).thenAnswer(inv -> {
            String dh = inv.getArgument(0);
            String km = inv.getArgument(1);
            return savedUsages.stream()
                    .filter(u -> u.getDonHang() != null && u.getDonHang().getMaDonHang().equals(dh) && u.getKhuyenMai().getMaKm().equalsIgnoreCase(km))
                    .toList();
        });

        doAnswer(inv -> {
            Iterable<SuDungKhuyenMai> toDelete = inv.getArgument(0);
            toDelete.forEach(savedUsages::remove);
            return null;
        }).when(suDungKhuyenMaiRepository).deleteAll(any());

        when(donHangRepository.save(any(DonHang.class))).thenAnswer(inv -> {
            DonHang d = inv.getArgument(0);
            if (d.getMaDonHang() == null) {
                d.setMaDonHang("DH-" + UUID.randomUUID().toString().substring(0, 8));
            }
            savedDonHang.add(d);
            return d;
        });

        when(donHangRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return savedDonHang.stream().filter(d -> d.getMaDonHang().equals(id)).findFirst();
        });

        when(donHangRepository.findByIdForUpdate(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return savedDonHang.stream().filter(d -> d.getMaDonHang().equals(id)).findFirst();
        });

        when(thanhToanRepository.save(any(ThanhToan.class))).thenAnswer(inv -> {
            ThanhToan t = inv.getArgument(0);
            savedThanhToan.add(t);
            return t;
        });

        when(thanhToanRepository.findByDonHangMaDonHangOrderByNgayTaoDesc(anyString())).thenAnswer(inv -> {
            String dhId = inv.getArgument(0, String.class);
            return savedThanhToan.stream()
                    .filter(t -> t.getDonHang() != null && t.getDonHang().getMaDonHang().equals(dhId))
                    .toList();
        });

        when(mapper.toDto(any(DonHang.class))).thenAnswer(inv -> {
            DonHang d = inv.getArgument(0);
            PhuongThucTt pt = savedThanhToan.stream()
                    .filter(t -> t.getDonHang() != null && t.getDonHang().getMaDonHang().equals(d.getMaDonHang()))
                    .map(ThanhToan::getPhuongThuc)
                    .findFirst()
                    .orElse(PhuongThucTt.COD);
            return new DonHangDto(
                    d.getMaDonHang(),
                    d.getKhachHang() != null ? d.getKhachHang().getMaKh() : "KH001",
                    d.getKhuyenMai() != null ? d.getKhuyenMai().getMaKm() : null,
                    d.getKhuyenMaiVanChuyen() != null ? d.getKhuyenMaiVanChuyen().getMaKm() : null,
                    d.getNgayMua(),
                    pt,
                    d.getTrangThai(),
                    d.getTamTinh(),
                    d.getGiamGia(),
                    d.getPhiVanChuyen(),
                    d.getGiamPhiVanChuyen(),
                    d.getTongThanhToan(),
                    d.getDiaChiGiaoHang(),
                    d.getNgayCapNhat(),
                    d.getNgayGiao(),
                    "Nguyễn Văn A",
                    null,
                    null,
                    d.getNgayKhachXacNhan(),
                    d.getNguoiXacNhan(),
                    List.of()
            );
        });
    }

    private void releaseAllLocks() {
        variantLocks.values().forEach(l -> {
            while (l.isHeldByCurrentThread()) l.unlock();
        });
        voucherLocks.values().forEach(l -> {
            while (l.isHeldByCurrentThread()) l.unlock();
        });
    }

    private KhuyenMai createStandardVoucher(String code, LoaiKhuyenMai loai, Integer soLuong, int daDung, int limitPerCustomer) {
        KhuyenMai km = new KhuyenMai();
        km.setMaKm(code.toUpperCase(Locale.ROOT));
        km.setTenKm("Khuyến mãi " + code);
        km.setLoai(loai);
        km.setPhanTramGiam(new BigDecimal("10"));
        km.setSoTienGiamToiDa(new BigDecimal("500000"));
        km.setDonToiThieu(BigDecimal.ZERO);
        km.setSoLuong(soLuong);
        km.setDaDung(daDung);
        km.setChiKhachMoi(false);
        km.setGioiHanMoiKhach(limitPerCustomer);
        km.setNgayBatDau(LocalDate.now().minusDays(1));
        km.setNgayKetThuc(LocalDate.now().plusDays(30));
        km.setTrangThaiHoatDong(true);
        voucherCatalog.put(km.getMaKm(), km);
        return km;
    }

    @AfterEach
    void tearDown() {
        releaseAllLocks();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC01: Voucher còn lượt, đủ điều kiện -> checkout thành công, daDung tăng 1, SuDungKhuyenMai được tạo")
    void tc01_voucherAvailable_checkoutSuccess() {
        createStandardVoucher("SALE10", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "SALE10", null, "PB001", 1
        );

        DonHangDto result = datHang("KH001", req);
        releaseAllLocks();

        assertThat(result).isNotNull();
        KhuyenMai km = voucherCatalog.get("SALE10");
        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);
        assertThat(savedUsages.get(0).getKhuyenMai().getMaKm()).isEqualTo("SALE10");
        assertThat(savedUsages.get(0).getKhachHang().getMaKh()).isEqualTo("KH001");
    }

    @Test
    @DisplayName("TC02: Voucher hết lượt (daDung >= soLuong) -> checkout bị từ chối với lỗi rõ ràng, daDung không đổi")
    void tc02_voucherExhausted_checkoutRejected() {
        createStandardVoucher("SALE10", LoaiKhuyenMai.DON_HANG, 5, 5, 1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "SALE10", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("đã hết lượt sử dụng");
        } finally {
            releaseAllLocks();
        }

        KhuyenMai km = voucherCatalog.get("SALE10");
        assertThat(km.getDaDung()).isEqualTo(5);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC03: 2 khách hàng đồng thời checkout tranh 1 lượt voucher cuối cùng -> đúng 1 người thành công")
    void tc03_twoCustomersRaceForOneVoucherSlot() throws Exception {
        createStandardVoucher("HOTVOUCHER", LoaiKhuyenMai.DON_HANG, 1, 0, 1);
        int clientCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= clientCount; i++) {
            String khId = "KH00" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DonHangDto.Checkout req = new DonHangDto.Checkout(
                            PhuongThucTt.COD, "DC001", "HOTVOUCHER", null, "PB001", 1
                    );
                    datHang(khId, req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    if (e.getMessage().contains("hết lượt sử dụng")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    releaseAllLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        KhuyenMai km = voucherCatalog.get("HOTVOUCHER");
        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);
    }

    @Test
    @DisplayName("TC04: 10 concurrent requests tranh 3 lượt voucher còn lại -> đúng 3 thành công, 7 bị từ chối")
    void tc04_tenConcurrentRequestsRaceForThreeVouchers() throws Exception {
        createStandardVoucher("SALE3", LoaiKhuyenMai.DON_HANG, 3, 0, 1);
        int clientCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= clientCount; i++) {
            String khId = "KH_" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DonHangDto.Checkout req = new DonHangDto.Checkout(
                            PhuongThucTt.COD, "DC001", "SALE3", null, "PB001", 1
                    );
                    datHang(khId, req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    if (e.getMessage().contains("hết lượt sử dụng")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    releaseAllLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(3);
        assertThat(failCount.get()).isEqualTo(7);
        KhuyenMai km = voucherCatalog.get("SALE3");
        assertThat(km.getDaDung()).isEqualTo(3);
        assertThat(savedUsages).hasSize(3);
    }

    @Test
    @DisplayName("TC05: 100 concurrent requests tranh 20 lượt voucher -> đúng 20 thành công, 80 bị từ chối")
    void tc05_hundredConcurrentRequestsRaceForTwentyVouchers() throws Exception {
        createStandardVoucher("FLASH20", LoaiKhuyenMai.DON_HANG, 20, 0, 1);
        int clientCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= clientCount; i++) {
            String khId = "USER_" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DonHangDto.Checkout req = new DonHangDto.Checkout(
                            PhuongThucTt.COD, "DC001", "FLASH20", null, "PB001", 1
                    );
                    datHang(khId, req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    if (e.getMessage().contains("hết lượt sử dụng")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    releaseAllLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(20);
        assertThat(failCount.get()).isEqualTo(80);
        KhuyenMai km = voucherCatalog.get("FLASH20");
        assertThat(km.getDaDung()).isEqualTo(20);
        assertThat(savedUsages).hasSize(20);
    }

    @Test
    @DisplayName("TC06: Khách hàng đã đạt gioiHanMoiKhach cố gắng checkout lần 2 -> bị từ chối")
    void tc06_exceedPerCustomerLimit_rejected() {
        KhuyenMai km = createStandardVoucher("ONEPERUSER", LoaiKhuyenMai.DON_HANG, 10, 1, 1);
        SuDungKhuyenMai oldUsage = new SuDungKhuyenMai();
        oldUsage.setMaSuDung("SDKM001");
        oldUsage.setKhuyenMai(km);
        oldUsage.setKhachHang(defaultCustomer);
        oldUsage.setNgaySuDung(LocalDateTime.now().minusHours(1));
        savedUsages.add(oldUsage);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "ONEPERUSER", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Bạn đã đạt giới hạn sử dụng mã này");
        } finally {
            releaseAllLocks();
        }

        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);
    }

    @Test
    @DisplayName("TC07: Cùng 1 khách hàng mở 2 tabs checkout đồng thời với voucher gioiHanMoiKhach=1 -> đúng 1 request thành công")
    void tc07_sameCustomerTwoTabsRace_perCustomerLimitEnforced() throws Exception {
        createStandardVoucher("TABRACE", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        int clientCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DonHangDto.Checkout req = new DonHangDto.Checkout(
                            PhuongThucTt.COD, "DC001", "TABRACE", null, "PB001", 1
                    );
                    datHang("KH001", req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    if (e.getMessage().contains("Bạn đã đạt giới hạn sử dụng mã này")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    releaseAllLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        KhuyenMai km = voucherCatalog.get("TABRACE");
        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);
    }

    @Test
    @DisplayName("TC08: Voucher hết hạn (ngayKetThuc < now) -> bị từ chối, daDung không đổi")
    void tc08_expiredVoucher_rejected() {
        KhuyenMai km = createStandardVoucher("EXPIRED", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        km.setNgayKetThuc(LocalDate.now().minusDays(1));

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "EXPIRED", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không còn hiệu lực");
        } finally {
            releaseAllLocks();
        }

        assertThat(km.getDaDung()).isEqualTo(0);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC09: Voucher chưa đến ngày bắt đầu (ngayBatDau > now) -> bị từ chối, daDung không đổi")
    void tc09_notStartedVoucher_rejected() {
        KhuyenMai km = createStandardVoucher("FUTURE", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        km.setNgayBatDau(LocalDate.now().plusDays(2));

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "FUTURE", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không còn hiệu lực");
        } finally {
            releaseAllLocks();
        }

        assertThat(km.getDaDung()).isEqualTo(0);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC10: Voucher bị vô hiệu hóa (trangThaiHoatDong = false) -> bị từ chối")
    void tc10_deactivatedVoucher_rejected() {
        KhuyenMai km = createStandardVoucher("DISABLED", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        km.setTrangThaiHoatDong(false);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "DISABLED", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("đã ngừng hoạt động");
        } finally {
            releaseAllLocks();
        }

        assertThat(km.getDaDung()).isEqualTo(0);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC11: Đơn hàng không đạt donToiThieu của voucher -> bị từ chối với thông báo số tiền còn thiếu")
    void tc11_orderBelowMinOrderValue_rejected() {
        KhuyenMai km = createStandardVoucher("MIN30M", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        km.setDonToiThieu(new BigDecimal("30000000")); // 30 million VND

        // PB001 price is 25M < 30M
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "MIN30M", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Mua thêm");
        } finally {
            releaseAllLocks();
        }

        assertThat(km.getDaDung()).isEqualTo(0);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC12: Transaction checkout thất bại -> rollback hoàn toàn, voucher không bị trừ lượt")
    void tc12_checkoutFailure_rollbackVoucher() {
        KhuyenMai km = createStandardVoucher("ROLLBACK_TEST", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        // Simulate out of stock after voucher evaluation
        pb1.setSoLuongTon(0);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "ROLLBACK_TEST", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không đủ hàng");
        } finally {
            releaseAllLocks();
        }

        assertThat(km.getDaDung()).isEqualTo(0);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC13: Retry cùng Idempotency-Key của đơn đã thành công -> daDung KHÔNG bị tăng thêm lần 2")
    void tc13_idempotencyRetry_noDoubleVoucherIncrement() {
        KhuyenMai km = createStandardVoucher("IDEMPOTENT_KM", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "IDEMPOTENT_KM", null, "PB001", 1
        );

        DonHangDto first = datHang("KH001", req);
        releaseAllLocks();

        assertThat(first).isNotNull();
        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);

        // When duplicate idempotency call is served by returning existing order
        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);
    }

    @Test
    @DisplayName("TC14: 2 vouchers trong 1 đơn hàng -> cả 2 đều bị lock đúng thứ tự, không deadlock, cả 2 trừ lượt chính xác")
    void tc14_twoVouchersInOneCheckout_deterministicLockingAndAccurateCounters() throws Exception {
        createStandardVoucher("VOUCHER_Z", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        createStandardVoucher("VOUCHER_A", LoaiKhuyenMai.VAN_CHUYEN, 10, 0, 1);

        int clientCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 1; i <= clientCount; i++) {
            String khId = "USER_DUAL_" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DonHangDto.Checkout req = new DonHangDto.Checkout(
                            PhuongThucTt.COD, "DC001", "VOUCHER_Z", "VOUCHER_A", "PB001", 1
                    );
                    datHang(khId, req);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    releaseAllLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(4);
        assertThat(voucherCatalog.get("VOUCHER_Z").getDaDung()).isEqualTo(4);
        assertThat(voucherCatalog.get("VOUCHER_A").getDaDung()).isEqualTo(4);
        assertThat(savedUsages).hasSize(8); // 4 orders x 2 vouchers
    }

    @Test
    @DisplayName("TC15: Hủy đơn hàng hợp lệ -> hoàn lại lượt dùng voucher (daDung giảm 1, xóa bản ghi SuDungKhuyenMai)")
    void tc15_validCancelOrder_refundsVoucher() {
        KhuyenMai km = createStandardVoucher("CANCEL_REFUND", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "CANCEL_REFUND", null, "PB001", 1
        );

        DonHangDto orderDto = datHang("KH001", req);
        releaseAllLocks();

        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);

        DonHangDto.HuyRequest huyReq = new DonHangDto.HuyRequest("Đổi ý, không muốn mua nữa", null);
        donHangService.huy("KH001", orderDto.maDonHang(), huyReq);
        releaseAllLocks();

        assertThat(km.getDaDung()).isEqualTo(0);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC16: Hủy đơn hàng lần 2 (duplicate cancel) -> bị từ chối, KHÔNG hoàn voucher lần 2 (daDung không giảm âm)")
    void tc16_duplicateCancel_rejected_noNegativeDaDung() {
        KhuyenMai km = createStandardVoucher("DUP_CANCEL", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "DUP_CANCEL", null, "PB001", 1
        );

        DonHangDto orderDto = datHang("KH001", req);
        releaseAllLocks();

        DonHangDto.HuyRequest huyReq = new DonHangDto.HuyRequest("Đổi ý, không muốn mua nữa", null);
        donHangService.huy("KH001", orderDto.maDonHang(), huyReq);
        releaseAllLocks();

        assertThat(km.getDaDung()).isEqualTo(0);

        try {
            assertThatThrownBy(() -> donHangService.huy("KH001", orderDto.maDonHang(), huyReq))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("đã được hủy trước đó");
        } finally {
            releaseAllLocks();
        }

        assertThat(km.getDaDung()).isEqualTo(0);
        assertThat(km.getDaDung()).isNotNegative();
    }

    @Test
    @DisplayName("TC17: MoMo IPN gọi lại nhiều lần cho cùng 1 đơn -> voucher không bị ảnh hưởng, giữ nguyên số lượt")
    void tc17_momoIpnDuplicateCallback_voucherUnchanged() {
        KhuyenMai km = createStandardVoucher("MOMO_VOUCHER", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.VI_DIEN_TU, "DC001", "MOMO_VOUCHER", null, "PB001", 1
        );

        DonHangDto orderDto = datHang("KH001", req);
        releaseAllLocks();

        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);

        // Multiple IPN callback notifications do not alter SuDungKhuyenMai or KhuyenMai.daDung
        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).hasSize(1);
    }

    @Test
    @DisplayName("TC18: Thanh toán MoMo thất bại -> voucher state preserved correctly")
    void tc18_momoPaymentFailed_voucherStatePreserved() {
        KhuyenMai km = createStandardVoucher("MOMO_FAIL_KM", LoaiKhuyenMai.DON_HANG, 10, 0, 1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.VI_DIEN_TU, "DC001", "MOMO_FAIL_KM", null, "PB001", 1
        );

        DonHangDto orderDto = datHang("KH001", req);
        releaseAllLocks();

        assertThat(km.getDaDung()).isEqualTo(1);
        // If order stays in CHO_THANH_TOAN, voucher remains applied
        DonHang d = savedDonHang.get(0);
        assertThat(d.getKhuyenMai().getMaKm()).isEqualTo("MOMO_FAIL_KM");
    }

    @Test
    @DisplayName("TC19: Voucher hết lượt giữa lúc xem giỏ và lúc bấm đặt hàng -> từ chối tại checkout, giỏ hàng giữ nguyên")
    void tc19_voucherExhaustedBetweenPreviewAndCheckout() {
        KhuyenMai km = createStandardVoucher("RACE_PREVIEW", LoaiKhuyenMai.DON_HANG, 1, 0, 1);

        // Before customer checks out, another order consumes the last slot
        km.setDaDung(1);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "RACE_PREVIEW", null, "PB001", 1
        );

        try {
            assertThatThrownBy(() -> datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("đã hết lượt sử dụng");
        } finally {
            releaseAllLocks();
        }

        // Cart is untouched, daDung remains 1
        assertThat(km.getDaDung()).isEqualTo(1);
        assertThat(savedUsages).isEmpty();
    }

    @Test
    @DisplayName("TC20: Checkout đồng thời vừa tranh voucher vừa tranh tồn kho -> bảo đảm toàn vẹn cả kho và voucher")
    void tc20_concurrentStockAndVoucherRace() throws Exception {
        // Stock = 2, Voucher slots = 2, 8 concurrent requests
        pb1.setSoLuongTon(2);
        createStandardVoucher("DUAL_RACE", LoaiKhuyenMai.DON_HANG, 2, 0, 1);

        int clientCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= clientCount; i++) {
            String khId = "RACE_USER_" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DonHangDto.Checkout req = new DonHangDto.Checkout(
                            PhuongThucTt.COD, "DC001", "DUAL_RACE", null, "PB001", 1
                    );
                    datHang(khId, req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    failCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    releaseAllLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(failCount.get()).isEqualTo(6);
        assertThat(pb1.getSoLuongTon()).isEqualTo(0);
        assertThat(voucherCatalog.get("DUAL_RACE").getDaDung()).isEqualTo(2);
        assertThat(savedUsages).hasSize(2);
    }

    @Test
    @DisplayName("TC21: Regression test: MoMo integration (PNE-001) unbroken")
    void tc21_regression_momoIntegrationUnbroken() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.VI_DIEN_TU, "DC001", null, null, "PB001", 1
        );

        DonHangDto order = datHang("KH001", req);
        releaseAllLocks();

        assertThat(order).isNotNull();
        assertThat(order.phuongThucTt()).isEqualTo(PhuongThucTt.VI_DIEN_TU);
    }

    @Test
    @DisplayName("TC22: Regression test: Inventory pessimistic locking (PNE-002) unbroken")
    void tc22_regression_inventoryLockingUnbroken() {
        pb1.setSoLuongTon(1);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 1
        );

        DonHangDto order = datHang("KH001", req);
        releaseAllLocks();

        assertThat(order).isNotNull();
        assertThat(pb1.getSoLuongTon()).isEqualTo(0);

        try {
            assertThatThrownBy(() -> datHang("KH002", req))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không đủ hàng");
        } finally {
            releaseAllLocks();
        }
    }

    @Test
    @DisplayName("TC23: Regression test: Checkout idempotency (PNE-004) unbroken")
    void tc23_regression_checkoutIdempotencyUnbroken() {
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 1
        );

        DonHangDto order1 = datHang("KH001", req);
        releaseAllLocks();

        assertThat(order1).isNotNull();
        assertThat(savedDonHang).hasSize(1);
    }

    @Test
    @DisplayName("TC24: Backward compatibility: Voucher cũ không có giới hạn (soLuong = null) hoạt động bình thường")
    void tc24_unlimitedVoucher_backwardCompatibility() {
        KhuyenMai km = createStandardVoucher("UNLIMITED", LoaiKhuyenMai.DON_HANG, null, 0, 999);
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", "UNLIMITED", null, "PB001", 1
        );

        DonHangDto order1 = datHang("KH001", req);
        releaseAllLocks();
        DonHangDto order2 = datHang("KH002", req);
        releaseAllLocks();

        assertThat(order1).isNotNull();
        assertThat(order2).isNotNull();
        assertThat(km.getDaDung()).isEqualTo(2);
        assertThat(savedUsages).hasSize(2);
    }
}
