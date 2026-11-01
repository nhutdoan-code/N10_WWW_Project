package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.ThongKeDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.LoaiSanPham;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.ChiTietDonHangRepository;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import nhom10.phonenest.util.SecurityUtil;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ThongKeService {

    private static final List<TrangThaiDonHang> PIPELINE_STATUSES = List.of(
            TrangThaiDonHang.CHO_XAC_NHAN,
            TrangThaiDonHang.DA_XAC_NHAN,
            TrangThaiDonHang.DANG_CHUAN_BI,
            TrangThaiDonHang.DANG_GIAO
    );

    private final DonHangRepository donHangRepository;
    private final ChiTietDonHangRepository chiTietDonHangRepository;
    private final PhienBanSanPhamRepository phienBanSanPhamRepository;
    private final YeuCauDoiTraRepository yeuCauDoiTraRepository;
    private final LoaiSanPhamRepository loaiSanPhamRepository;
    private final DonHangMapper donHangMapper;

    public ThongKeDto thongKe(LocalDate tu, LocalDate den) {
        if (tu != null && den != null && tu.isAfter(den)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }

        LocalDateTime start = tu != null ? tu.atStartOfDay() : null;
        LocalDateTime end = den != null ? den.plusDays(1).atStartOfDay() : null;
        boolean hasFilter = (tu != null || den != null);

        // 1. Doanh thu & số đơn giao thành công (Delivery scope: DA_GIAO theo ngayGiao)
        BigDecimal doanhThu;
        int soDonThanhCong;
        if (hasFilter) {
            doanhThu = donHangRepository.sumDeliveredRevenueBetweenDeliveryDates(start, end);
            soDonThanhCong = (int) donHangRepository.countDeliveredOrdersBetweenDeliveryDates(start, end);
        } else {
            doanhThu = donHangRepository.sumDeliveredRevenueAllTime();
            soDonThanhCong = (int) donHangRepository.countDeliveredOrdersAllTime();
        }

        // 2. Giá trị & số đơn đang xử lý (Pipeline GMV: CHO_XAC_NHAN..DANG_GIAO theo ngayMua)
        BigDecimal giaTriDangXuLy = donHangRepository.sumPipelineGmv(PIPELINE_STATUSES, start, end);
        int soDonDangXuLy = (int) donHangRepository.countPipelineOrders(PIPELINE_STATUSES, start, end);

        // 3. Phân bố trạng thái & tổng đơn tạo (Creation scope: theo ngayMua)
        Map<String, Integer> theoTrangThai = new LinkedHashMap<>();
        for (TrangThaiDonHang t : TrangThaiDonHang.values()) {
            theoTrangThai.put(t.name(), 0);
        }
        List<Object[]> statusRows = donHangRepository.countOrdersByStatus(start, end);
        int soDon = 0;
        for (Object[] r : statusRows) {
            if (r[0] != null) {
                String stName = ((TrangThaiDonHang) r[0]).name();
                int count = ((Number) r[1]).intValue();
                theoTrangThai.put(stName, count);
                soDon += count;
            }
        }
        int soDonHuy = theoTrangThai.getOrDefault(TrangThaiDonHang.DA_HUY.name(), 0);

        // 4. Chuỗi thời gian doanh thu & số đơn theo ngày giao hàng (ngayGiao IS NOT NULL)
        List<Object[]> dailyRows = donHangRepository.getDailyRevenueAndDeliveredCount(start, end);
        List<ThongKeDto.Ngay> doanhThuTheoNgay = new ArrayList<>();
        for (Object[] r : dailyRows) {
            String ngay = String.valueOf(r[0]);
            BigDecimal rev = r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO;
            int count = r[2] != null ? ((Number) r[2]).intValue() : 0;
            doanhThuTheoNgay.add(new ThongKeDto.Ngay(ngay, rev, count));
        }

        // 5. Sản phẩm bán chạy (DA_GIAO only, limit 5, tie-break by ID ASC)
        List<Object[]> topRows;
        if (hasFilter) {
            topRows = chiTietDonHangRepository.findTopSellingDeliveredBetweenDeliveryDates(start, end, PageRequest.of(0, 5));
        } else {
            topRows = chiTietDonHangRepository.findTopSellingDeliveredAllTime(PageRequest.of(0, 5));
        }
        List<ThongKeDto.BanChay> banChay = new ArrayList<>();
        for (Object[] r : topRows) {
            String maSp = (String) r[0];
            String tenMay = (String) r[1];
            int daBan = ((Number) r[2]).intValue();
            banChay.add(new ThongKeDto.BanChay(maSp, tenMay, daBan));
        }

        // 6. Doanh số theo hãng (Option A: Product Gross Sales from DA_GIAO, mapped to root category)
        List<Object[]> catRows;
        if (hasFilter) {
            catRows = chiTietDonHangRepository.findDeliveredSalesByCategoryBetweenDeliveryDates(start, end);
        } else {
            catRows = chiTietDonHangRepository.findDeliveredSalesByCategoryAllTime();
        }
        Map<String, LoaiSanPham> loaiMap = loaiSanPhamRepository.findAll().stream()
                .collect(Collectors.toMap(LoaiSanPham::getMaLoai, l -> l, (a, b) -> a));

        Map<String, BigDecimal> tienHang = new HashMap<>();
        Map<String, Integer> slHang = new HashMap<>();
        for (Object[] r : catRows) {
            String maLoai = (String) r[0];
            BigDecimal lineRev = r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO;
            int lineQty = r[2] != null ? ((Number) r[2]).intValue() : 0;
            String hang = tenHang(maLoai, loaiMap);
            tienHang.merge(hang, lineRev, BigDecimal::add);
            slHang.merge(hang, lineQty, Integer::sum);
        }
        List<ThongKeDto.Hang> theoHang = tienHang.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> new ThongKeDto.Hang(e.getKey(), e.getValue(), slHang.getOrDefault(e.getKey(), 0)))
                .toList();

        // 7. Yêu cầu đổi trả trong kỳ
        int soDoiTra = (int) yeuCauDoiTraRepository.countByNgayTaoRange(start, end);

        // 8. Tồn kho cấp biến thể (PhienBanSanPham)
        List<Object[]> invRows = phienBanSanPhamRepository.countVariantInventorySummary(KhoService.NGUONG_SAP_HET);
        int hetHang = 0;
        int sapHet = 0;
        int conHang = 0;
        if (!invRows.isEmpty() && invRows.get(0) != null) {
            Object[] r = invRows.get(0);
            hetHang = r[0] != null ? ((Number) r[0]).intValue() : 0;
            sapHet = r[1] != null ? ((Number) r[1]).intValue() : 0;
            conHang = r[2] != null ? ((Number) r[2]).intValue() : 0;
        }

        return new ThongKeDto(
                doanhThu,
                giaTriDangXuLy,
                soDon,
                soDonThanhCong,
                soDonHuy,
                soDonDangXuLy,
                soDoiTra,
                sapHet,
                hetHang,
                conHang,
                theoTrangThai,
                banChay,
                doanhThuTheoNgay,
                theoHang
        );
    }

    private String tenHang(String maLoai, Map<String, LoaiSanPham> map) {
        if (maLoai == null) return "Khác";
        LoaiSanPham cur = map.get(maLoai);
        while (cur != null && cur.getMaLoaiCha() != null) {
            LoaiSanPham parent = map.get(cur.getMaLoaiCha());
            if (parent == null) break;
            cur = parent;
        }
        return cur == null ? "Khác" : cur.getTenLoai();
    }

    public String xuatCsv(LocalDate tu, LocalDate den) {
        if (tu != null && den != null && tu.isAfter(den)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }
        LocalDateTime start = tu != null ? tu.atStartOfDay() : null;
        LocalDateTime end = den != null ? den.plusDays(1).atStartOfDay() : null;
        List<DonHang> dons = donHangRepository.findCreatedInRange(start, end);

        BigDecimal revenueDelivered = BigDecimal.ZERO;
        BigDecimal pipelineGmv = BigDecimal.ZERO;
        int deliveredCount = 0;
        int activeCount = 0;
        int cancelledCount = 0;

        for (DonHang d : dons) {
            BigDecimal total = d.getTongThanhToan() != null ? d.getTongThanhToan() : BigDecimal.ZERO;
            if (d.getTrangThai() == TrangThaiDonHang.DA_GIAO) {
                revenueDelivered = revenueDelivered.add(total);
                deliveredCount++;
            } else if (d.getTrangThai() == TrangThaiDonHang.DA_HUY) {
                cancelledCount++;
            } else {
                pipelineGmv = pipelineGmv.add(total);
                activeCount++;
            }
        }

        String exporter = SecurityUtil.currentUserId();
        String period = (tu != null || den != null)
                ? "Từ " + (tu != null ? tu.toString() : "bắt đầu") + " đến " + (den != null ? den.toString() : "hiện tại")
                : "Toàn bộ thời gian";
        String exportedAt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(LocalDateTime.now());

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("# BÁO CÁO DOANH THU & ĐƠN HÀNG PHONENEST\n");
        sb.append("# Thời gian xuất,").append(exportedAt).append('\n');
        sb.append("# Người xuất,").append(exporter != null ? exporter : "Quản trị viên").append('\n');
        sb.append("# Kỳ báo cáo,").append(period).append('\n');
        sb.append("#\n");
        sb.append("# TỔNG KẾT CHỈ SỐ KINH DOANH (KPI)\n");
        sb.append("Chỉ số,Giá trị (VND / Số lượng)\n");
        sb.append("Doanh thu thực nhận (Đã giao),").append(revenueDelivered.toBigInteger()).append('\n');
        sb.append("Doanh thu tiềm năng (Pipeline GMV),").append(pipelineGmv.toBigInteger()).append('\n');
        sb.append("Tổng số đơn hàng,").append(dons.size()).append('\n');
        sb.append("Đơn giao thành công,").append(deliveredCount).append('\n');
        sb.append("Đơn đang xử lý,").append(activeCount).append('\n');
        sb.append("Đơn đã hủy,").append(cancelledCount).append('\n');
        sb.append('\n');
        sb.append("# DANH SÁCH CHI TIẾT ĐƠN HÀNG\n");
        sb.append("Mã đơn,Ngày mua,Khách hàng,Số điện thoại,Trạng thái,Thanh toán,Tạm tính,Giảm giá,Phí vận chuyển,Tổng tiền\n");

        for (DonHang d : dons) {
            DonHangDto dto = donHangMapper.toDto(d);
            sb.append(dto.maDonHang()).append(',')
                    .append(dto.ngayMua() != null ? dto.ngayMua().toLocalDate() : "").append(',')
                    .append('"').append(dto.tenKhach() != null ? dto.tenKhach().replace("\"", "\"\"") : "").append('"').append(',')
                    .append('"').append(d.getSoDienThoaiNhan() != null ? d.getSoDienThoaiNhan() : "").append('"').append(',')
                    .append('"').append(tenTrangThai(d.getTrangThai())).append('"').append(',')
                    .append(dto.phuongThucTt() != null ? dto.phuongThucTt().name() : "").append(',')
                    .append(dto.tamTinh() != null ? dto.tamTinh().toBigInteger() : 0).append(',')
                    .append(dto.giamGia() != null ? dto.giamGia().toBigInteger() : 0).append(',')
                    .append(dto.phiVanChuyen() != null && dto.giamPhiVanChuyen() != null ? dto.phiVanChuyen().subtract(dto.giamPhiVanChuyen()).toBigInteger() : 0).append(',')
                    .append(dto.tongTien() != null ? dto.tongTien().toBigInteger() : 0).append('\n');
        }
        return sb.toString();
    }

    private String tenTrangThai(TrangThaiDonHang t) {
        if (t == null) return "—";
        return switch (t) {
            case CHO_XAC_NHAN -> "Chờ xác nhận";
            case DA_XAC_NHAN -> "Đã xác nhận";
            case DANG_CHUAN_BI -> "Đang chuẩn bị hàng";
            case DANG_GIAO -> "Đang giao hàng";
            case DA_GIAO -> "Đã giao thành công";
            case DA_HUY -> "Đã hủy";
        };
    }
}