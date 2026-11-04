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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
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
public class DonHangKhoConcurrencyTest {

    @Mock private DonHangRepository donHangRepository;
    @Mock private GioHangRepository gioHangRepository;
    @Mock private PhienBanSanPhamRepository phienBanRepository;
    @Mock private KhuyenMaiRepository khuyenMaiRepository;
    @Mock private HuyDonHangRepository huyRepository;
    @Mock private ThanhToanRepository thanhToanRepository;
    @Mock private LichSuTrangThaiDonHangRepository lichSuRepository;
    @Mock private BienDongKhoRepository bienDongRepository;
    @Mock private KhuyenMaiService khuyenMaiService;
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

    @InjectMocks
    private DonHangService donHangService;

    private KhoService khoService;

    private KhachHang khachHang;
    private DiaChi diaChi;
    private SanPham sanPham;
    private PhienBanSanPham pb1;
    private PhienBanSanPham pb2;

    private final ConcurrentHashMap<String, ReentrantLock> rowLocks = new ConcurrentHashMap<>();
    private final List<BienDongKho> savedBienDong = Collections.synchronizedList(new ArrayList<>());
    private final List<DonHang> savedDonHang = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        rowLocks.clear();
        savedBienDong.clear();
        savedDonHang.clear();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("KH001", null, List.of())
        );

        khoService = new KhoService(
                sanPhamRepository,
                phienBanRepository,
                bienDongRepository,
                taiKhoanRepository,
                nhatKyHoatDongService
        );

        khachHang = new KhachHang();
        khachHang.setMaKh("KH001");
        khachHang.setHoTen("Nguyễn Văn A");

        diaChi = new DiaChi();
        diaChi.setMaDiaChi("DC001");
        diaChi.setMaKh("KH001");
        diaChi.setTenNguoiNhan("Nguyễn Văn A");
        diaChi.setSoDienThoaiNhan("0912345678");
        diaChi.setMaTinh("79");

        LoaiSanPham loaiSp = new LoaiSanPham();
        loaiSp.setMaLoai("L01");
        loaiSp.setTenLoai("Điện thoại");

        ThuongHieu thuongHieu = new ThuongHieu();
        thuongHieu.setMaThuongHieu("TH01");
        thuongHieu.setTenThuongHieu("Apple");

        sanPham = new SanPham();
        sanPham.setMaSanPham("SP001");
        sanPham.setTenMay("iPhone 15 Pro");
        sanPham.setLoai(loaiSp);
        sanPham.setThuongHieu(thuongHieu);
        sanPham.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);
        sanPham.setThoiHanBaoHanhThang(12);

        pb1 = new PhienBanSanPham();
        pb1.setMaPhienBan("PB001");
        pb1.setSku("IP15P-256");
        pb1.setGiaBan(new BigDecimal("25000000"));
        pb1.setGiaNiemYet(new BigDecimal("28000000"));
        pb1.setSoLuongTon(1);
        pb1.setSanPham(sanPham);
        pb1.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);

        pb2 = new PhienBanSanPham();
        pb2.setMaPhienBan("PB002");
        pb2.setSku("IP15P-512");
        pb2.setGiaBan(new BigDecimal("29000000"));
        pb2.setGiaNiemYet(new BigDecimal("32000000"));
        pb2.setSoLuongTon(5);
        pb2.setSanPham(sanPham);
        pb2.setTrangThai(TrangThaiSanPham.DANG_KINH_DOANH);

        when(khachHangRepository.findById("KH001")).thenReturn(Optional.of(khachHang));
        when(diaChiRepository.findById("DC001")).thenReturn(Optional.of(diaChi));
        when(diaChiService.diaChiDayDu(any())).thenReturn("123 Lê Lợi, Q1, TP.HCM");
        when(phiVanChuyenService.tinhPhi(any(), any())).thenReturn(BigDecimal.ZERO);
        when(phiVanChuyenService.theoDiaChi(any())).thenReturn(null);

        when(donHangRepository.save(any(DonHang.class))).thenAnswer(inv -> {
            DonHang d = inv.getArgument(0);
            savedDonHang.add(d);
            return d;
        });

        when(bienDongRepository.save(any(BienDongKho.class))).thenAnswer(inv -> {
            BienDongKho b = inv.getArgument(0);
            savedBienDong.add(b);
            return b;
        });

        when(mapper.toDto(any(DonHang.class))).thenAnswer(inv -> {
            DonHang d = inv.getArgument(0);
            return new DonHangDto(
                    d.getMaDonHang(),
                    "KH001",
                    null,
                    null,
                    d.getNgayMua(),
                    PhuongThucTt.COD,
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void configurePessimisticRowLocking() {
        Map<String, PhienBanSanPham> catalog = Map.of("PB001", pb1, "PB002", pb2);
        when(phienBanRepository.findByIdForUpdate(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            ReentrantLock lock = rowLocks.computeIfAbsent(id, k -> new ReentrantLock());
            lock.lock();
            PhienBanSanPham p = catalog.get(id);
            return p != null ? Optional.of(p) : Optional.empty();
        });
        when(phienBanRepository.findBySku("IP15P-256")).thenReturn(Optional.of(pb1));
        when(phienBanRepository.findBySku("IP15P-512")).thenReturn(Optional.of(pb2));
    }

    private void releaseAllRowLocks() {
        rowLocks.values().forEach(lock -> {
            while (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        });
    }

    @Test
    @DisplayName("Test 1: Kho còn 1 sản phẩm, 10 khách đặt đồng thời: chỉ đúng 1 đơn thành công")
    void test01_singleStock_10ConcurrentOrders_onlyOneSucceeds() throws Exception {
        pb1.setSoLuongTon(1);
        configurePessimisticRowLocking();

        when(thanhToanService.mucMua(eq("KH001"), eq("PB001"), eq(1)))
                .thenReturn(Map.of("PB001", 1));

        int clientCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 1
        );

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    donHangService.datHang("KH001", req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    if (e.getMessage().contains("không đủ hàng")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    releaseAllRowLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(pb1.getSoLuongTon()).isEqualTo(0);
        assertThat(savedDonHang).hasSize(1);
        assertThat(savedBienDong).hasSize(1);
        assertThat(savedBienDong.get(0).getLoaiBienDong()).isEqualTo(LoaiBienDongKho.BAN_HANG);
        assertThat(savedBienDong.get(0).getTonTruoc()).isEqualTo(1);
        assertThat(savedBienDong.get(0).getTonSau()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test 2: Kho còn 5 sản phẩm, 10 khách đặt đồng thời: tối đa đúng 5 đơn thành công")
    void test02_fiveStock_10ConcurrentOrders_exactlyFiveSucceed() throws Exception {
        pb1.setSoLuongTon(5);
        configurePessimisticRowLocking();

        when(thanhToanService.mucMua(eq("KH001"), eq("PB001"), eq(1)))
                .thenReturn(Map.of("PB001", 1));

        int clientCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 1
        );

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    donHangService.datHang("KH001", req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    if (e.getMessage().contains("không đủ hàng")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    releaseAllRowLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);
        assertThat(pb1.getSoLuongTon()).isEqualTo(0);
        assertThat(savedDonHang).hasSize(5);
        assertThat(savedBienDong).hasSize(5);
    }

    @Test
    @DisplayName("Test 3: Đơn mua nhiều SKU, một SKU hết hàng: rollback toàn bộ, không trừ SKU nào")
    void test03_multiSkuOrder_oneSkuOutOfStock_entireOrderRollsBack() {
        pb1.setSoLuongTon(5);
        pb2.setSoLuongTon(0); // PB002 hết hàng
        configurePessimisticRowLocking();

        when(thanhToanService.mucMua(eq("KH001"), isNull(), isNull()))
                .thenReturn(new LinkedHashMap<>() {{
                    put("PB001", 2);
                    put("PB002", 1);
                }});

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, null, null
        );

        try {
            assertThatThrownBy(() -> donHangService.datHang("KH001", req))
                    .isInstanceOf(AppException.class)
                    .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                    .hasMessageContaining("không đủ hàng");
        } finally {
            releaseAllRowLocks();
        }

        // Tồn kho không đổi
        assertThat(pb1.getSoLuongTon()).isEqualTo(5);
        assertThat(pb2.getSoLuongTon()).isEqualTo(0);
        // Không có đơn hàng nào được lưu
        assertThat(savedDonHang).isEmpty();
        // Không có biến động kho nào được ghi nhận
        assertThat(savedBienDong).isEmpty();
    }

    @Test
    @DisplayName("Test 4: Đặt hàng thất bại do lỗi hợp lệ không làm thay đổi tồn kho")
    void test04_orderPlacementFails_stockRemainsUnchanged() {
        pb1.setSoLuongTon(10);
        configurePessimisticRowLocking();

        when(thanhToanService.mucMua(eq("KH001"), eq("PB001"), eq(1)))
                .thenReturn(Map.of("PB001", 1));
        // Địa chỉ không hợp lệ
        when(diaChiRepository.findById("DC_INVALID")).thenReturn(Optional.empty());

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC_INVALID", null, null, "PB001", 1
        );

        assertThatThrownBy(() -> donHangService.datHang("KH001", req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Địa chỉ giao hàng không hợp lệ");

        assertThat(pb1.getSoLuongTon()).isEqualTo(10);
        assertThat(savedDonHang).isEmpty();
        assertThat(savedBienDong).isEmpty();
    }

    @Test
    @DisplayName("Test 5: Hủy đơn hợp lệ hoàn kho đúng một lần")
    void test05_validCancellation_restocksInventoryExactlyOnce() {
        pb1.setSoLuongTon(10);
        configurePessimisticRowLocking();

        DonHang dh = new DonHang();
        dh.setMaDonHang("DH001");
        dh.setKhachHang(khachHang);
        dh.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);
        dh.setNgayMua(LocalDateTime.now());

        ChiTietDonHang ct = new ChiTietDonHang();
        ct.setMaChiTiet("CT001");
        ct.setDonHang(dh);
        ct.setPhienBan(pb1);
        ct.setSoLuong(2);
        dh.setChiTiet(List.of(ct));

        when(donHangRepository.findByIdForUpdate("DH001")).thenReturn(Optional.of(dh));

        DonHangDto.HuyRequest req = new DonHangDto.HuyRequest(
                "Đổi ý, không muốn mua nữa", ""
        );

        try {
            DonHangDto result = donHangService.huy("KH001", "DH001", req);
            assertThat(result.trangThai()).isEqualTo(TrangThaiDonHang.DA_HUY);
        } finally {
            releaseAllRowLocks();
        }

        // Tồn kho được hoàn lại đúng 2 chiếc: 10 + 2 = 12
        assertThat(pb1.getSoLuongTon()).isEqualTo(12);
        assertThat(dh.getTrangThai()).isEqualTo(TrangThaiDonHang.DA_HUY);
        assertThat(savedBienDong).hasSize(1);
        assertThat(savedBienDong.get(0).getLoaiBienDong()).isEqualTo(LoaiBienDongKho.HOAN_HUY_DON);
        assertThat(savedBienDong.get(0).getSoLuong()).isEqualTo(2);
        assertThat(savedBienDong.get(0).getTonTruoc()).isEqualTo(10);
        assertThat(savedBienDong.get(0).getTonSau()).isEqualTo(12);
        verify(huyRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Test 6: Hai request hủy cùng đơn không hoàn kho hai lần")
    void test06_concurrentCancellation_sameOrder_restocksOnlyOnce() throws Exception {
        pb1.setSoLuongTon(10);
        configurePessimisticRowLocking();

        DonHang dh = new DonHang();
        dh.setMaDonHang("DH001");
        dh.setKhachHang(khachHang);
        dh.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);
        dh.setNgayMua(LocalDateTime.now());

        ChiTietDonHang ct = new ChiTietDonHang();
        ct.setMaChiTiet("CT001");
        ct.setDonHang(dh);
        ct.setPhienBan(pb1);
        ct.setSoLuong(2);
        dh.setChiTiet(List.of(ct));

        ReentrantLock donHangLock = new ReentrantLock();
        when(donHangRepository.findByIdForUpdate("DH001")).thenAnswer(inv -> {
            donHangLock.lock();
            return Optional.of(dh);
        });

        int clientCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        DonHangDto.HuyRequest req = new DonHangDto.HuyRequest(
                "Đổi ý, không muốn mua nữa", ""
        );

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    donHangService.huy("KH001", "DH001", req);
                    successCount.incrementAndGet();
                } catch (AppException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    while (donHangLock.isHeldByCurrentThread()) {
                        donHangLock.unlock();
                    }
                    releaseAllRowLocks();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        // Tồn kho chỉ được hoàn 1 lần: 10 + 2 = 12 (KHÔNG PHẢI 14!)
        assertThat(pb1.getSoLuongTon()).isEqualTo(12);
        assertThat(savedBienDong).hasSize(1);
        assertThat(savedBienDong.get(0).getLoaiBienDong()).isEqualTo(LoaiBienDongKho.HOAN_HUY_DON);
    }

    @Test
    @DisplayName("Test 7: Nhập kho đồng thời với đặt hàng: tuần tự hóa đúng qua khóa hàng")
    void test07_concurrentStockImportAndOrderPlacement() throws Exception {
        pb1.setSoLuongTon(0); // Ban đầu hết hàng
        configurePessimisticRowLocking();

        when(thanhToanService.mucMua(eq("KH001"), eq("PB001"), eq(3)))
                .thenReturn(Map.of("PB001", 3));

        TaiKhoan tkAdmin = new TaiKhoan();
        tkAdmin.setMaTk("TK_ADMIN");
        when(taiKhoanRepository.findById("TK_ADMIN")).thenReturn(Optional.of(tkAdmin));

        // B1: Nhập kho 10 sản phẩm
        khoService.nhapKho("PB001", 10, "Nhập hàng từ kho tổng", "TK_ADMIN");
        releaseAllRowLocks();

        assertThat(pb1.getSoLuongTon()).isEqualTo(10);

        // B2: Đặt hàng 3 sản phẩm
        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, "PB001", 3
        );
        donHangService.datHang("KH001", req);
        releaseAllRowLocks();

        // Tồn kho cuối cùng là 7 (= 0 + 10 - 3)
        assertThat(pb1.getSoLuongTon()).isEqualTo(7);
        assertThat(savedBienDong).hasSize(2);
        assertThat(savedBienDong.get(0).getLoaiBienDong()).isEqualTo(LoaiBienDongKho.NHAP_KHO);
        assertThat(savedBienDong.get(0).getSoLuong()).isEqualTo(10);
        assertThat(savedBienDong.get(1).getLoaiBienDong()).isEqualTo(LoaiBienDongKho.BAN_HANG);
        assertThat(savedBienDong.get(1).getSoLuong()).isEqualTo(3);
    }

    @Test
    @DisplayName("Test 8: Nhiều đơn mua SKU theo thứ tự khác nhau: luôn khóa theo thứ tự maPhienBan tăng dần, không deadlock")
    void test08_multipleOrdersDifferentSkuOrder_noDeadlock() {
        pb1.setSoLuongTon(10);
        pb2.setSoLuongTon(10);
        configurePessimisticRowLocking();

        // Đơn 1 truyền map theo thứ tự PB002 trước, PB001 sau
        Map<String, Integer> mapReversed = new LinkedHashMap<>();
        mapReversed.put("PB002", 1);
        mapReversed.put("PB001", 1);
        when(thanhToanService.mucMua(eq("KH001"), isNull(), isNull()))
                .thenReturn(mapReversed);

        DonHangDto.Checkout req = new DonHangDto.Checkout(
                PhuongThucTt.COD, "DC001", null, null, null, null
        );

        try {
            donHangService.datHang("KH001", req);
        } finally {
            releaseAllRowLocks();
        }

        // Kiểm tra InOrder xác minh findByIdForUpdate luôn được gọi với PB001 TRƯỚC PB002
        InOrder inOrder = inOrder(phienBanRepository);
        inOrder.verify(phienBanRepository).findByIdForUpdate("PB001");
        inOrder.verify(phienBanRepository).findByIdForUpdate("PB002");

        assertThat(pb1.getSoLuongTon()).isEqualTo(9);
        assertThat(pb2.getSoLuongTon()).isEqualTo(9);
    }

    @Test
    @DisplayName("Test 9: Loại bỏ Math.max(0, truoc + delta): Ném CONFLICT khi tồn kho không đủ trừ")
    void test09_silentClampingRemoved_throwsConflictOnNegativeStock() {
        pb1.setSoLuongTon(1);
        configurePessimisticRowLocking();

        DonHang dh = new DonHang();
        dh.setMaDonHang("DH001");
        dh.setKhachHang(khachHang);
        dh.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);

        dh.setPhiVanChuyen(BigDecimal.ZERO);
        ChiTietDonHang ct = new ChiTietDonHang();
        ct.setMaChiTiet("CT001");
        ct.setDonHang(dh);
        ct.setPhienBan(pb1);
        ct.setSoLuong(5); // Muốn trừ 5 khi tồn chỉ có 1
        ct.setDonGia(new BigDecimal("25000000"));
        dh.setChiTiet(List.of(ct));

        when(donHangRepository.findByIdForUpdate("DH001")).thenReturn(Optional.of(dh));

        // 1. Thử sửa dòng tăng vượt tồn kho: ném BAD_REQUEST ("Không đủ tồn kho")
        assertThatThrownBy(() -> donHangService.suaDong("DH001", "PB001", 7))
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getStatus() == HttpStatus.BAD_REQUEST)
                .hasMessageContaining("Không đủ tồn kho");

        // 2. Thử trừ kho trực tiếp quá số lượng tồn: ném CONFLICT ("Tồn kho không đủ"), KHÔNG ép âm về 0
        assertThatThrownBy(() -> {
            try {
                java.lang.reflect.Method m = DonHangService.class.getDeclaredMethod(
                        "capTon", PhienBanSanPham.class, int.class, DonHang.class, LoaiBienDongKho.class
                );
                m.setAccessible(true);
                m.invoke(donHangService, pb1, -5, dh, LoaiBienDongKho.BAN_HANG);
            } catch (java.lang.reflect.InvocationTargetException ite) {
                throw ite.getCause();
            }
        })
                .isInstanceOf(AppException.class)
                .matches(e -> ((AppException) e).getStatus() == HttpStatus.CONFLICT)
                .hasMessageContaining("Tồn kho không đủ");

        releaseAllRowLocks();
        assertThat(pb1.getSoLuongTon()).isEqualTo(1); // Không bị ép về 0
    }

    @Test
    @DisplayName("Test 10: Sửa số lượng dòng đơn (suaDong): Khóa và cập nhật tồn kho an toàn")
    void test10_suaDong_adjustQuantityWithLockingAndValidation() {
        pb1.setSoLuongTon(5);
        configurePessimisticRowLocking();

        DonHang dh = new DonHang();
        dh.setMaDonHang("DH001");
        dh.setKhachHang(khachHang);
        dh.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);
        dh.setTamTinh(new BigDecimal("25000000"));
        dh.setTongThanhToan(new BigDecimal("25000000"));
        dh.setPhiVanChuyen(BigDecimal.ZERO);

        ChiTietDonHang ct = new ChiTietDonHang();
        ct.setMaChiTiet("CT001");
        ct.setDonHang(dh);
        ct.setPhienBan(pb1);
        ct.setSoLuong(1);
        ct.setDonGia(new BigDecimal("25000000"));
        dh.setChiTiet(new ArrayList<>(List.of(ct)));

        when(donHangRepository.findByIdForUpdate("DH001")).thenReturn(Optional.of(dh));

        try {
            // Tăng từ 1 lên 3 chiếc: cần thêm 2 chiếc, tồn kho 5 -> 3
            DonHangDto result = donHangService.suaDong("DH001", "PB001", 3);
            assertThat(result).isNotNull();
        } finally {
            releaseAllRowLocks();
        }

        assertThat(pb1.getSoLuongTon()).isEqualTo(3);
        assertThat(savedBienDong).hasSize(1);
        assertThat(savedBienDong.get(0).getLoaiBienDong()).isEqualTo(LoaiBienDongKho.DIEU_CHINH);
        assertThat(savedBienDong.get(0).getTonTruoc()).isEqualTo(5);
        assertThat(savedBienDong.get(0).getTonSau()).isEqualTo(3);
    }
}
