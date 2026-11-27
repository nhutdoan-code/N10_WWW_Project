package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.AdminAfterSalesRowDto;
import nhom10.phonenest.dto.AdminAfterSalesSummaryDto;
import nhom10.phonenest.entity.ChiTietDoiTra;
import nhom10.phonenest.entity.PhanCongHauMai;
import nhom10.phonenest.entity.YeuCauDoiTra;
import nhom10.phonenest.entity.YeuCauHauMai;
import nhom10.phonenest.enums.LoaiHauMai;
import nhom10.phonenest.enums.LoaiYeuCauDoiTra;
import nhom10.phonenest.enums.TrangThaiDoiTra;
import nhom10.phonenest.enums.TrangThaiHauMai;
import nhom10.phonenest.repository.PhanCongHauMaiRepository;
import nhom10.phonenest.repository.TaiKhoanRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import nhom10.phonenest.repository.YeuCauHauMaiRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AfterSalesAdminQueryService {
    private final YeuCauDoiTraRepository returns;
    private final YeuCauHauMaiRepository after;
    private final PhanCongHauMaiRepository assignments;
    private final TaiKhoanRepository accounts;

    private record Bucket(List<TrangThaiDoiTra> returnStatuses,
                          List<TrangThaiHauMai> afterStatuses,
                          boolean slaOnly) {}

    private static final List<TrangThaiDoiTra> RETURN_WAITING = List.of(TrangThaiDoiTra.CHO_DUYET);
    private static final List<TrangThaiHauMai> AFTER_WAITING = List.of(TrangThaiHauMai.CHO_TIEP_NHAN);
    private static final List<TrangThaiDoiTra> RETURN_PROCESSING = List.of(TrangThaiDoiTra.DA_DUYET, TrangThaiDoiTra.DANG_XU_LY);
    private static final List<TrangThaiHauMai> AFTER_PROCESSING = List.of(TrangThaiHauMai.DANG_KIEM_TRA, TrangThaiHauMai.DANG_XAC_MINH, TrangThaiHauMai.DANG_XU_LY);
    private static final List<TrangThaiDoiTra> RETURN_REJECTED = List.of(TrangThaiDoiTra.TU_CHOI);
    private static final List<TrangThaiHauMai> AFTER_REJECTED = List.of(TrangThaiHauMai.TU_CHOI);
    private static final List<TrangThaiDoiTra> RETURN_COMPLETED = List.of(TrangThaiDoiTra.HOAN_TAT);
    private static final List<TrangThaiHauMai> AFTER_COMPLETED = List.of(TrangThaiHauMai.HOAN_TAT);

    public Page<AdminAfterSalesRowDto> requests(String query, String status, String source, String type,
                                                 LocalDate from, LocalDate to, String sort,
                                                 int page, int size, boolean mine, boolean deadlineOverdue,
                                                 String currentAdminId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        long desiredLimit = ((long) safePage + 1) * safeSize;
        int fetchSize = (int) Math.min(Math.max(desiredLimit, safeSize), 100_000L);
        LocalDateTime now = LocalDateTime.now();
        String pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
        LocalDate today = LocalDate.now();
        LocalDateTime updatedAfter = "today".equals(status) ? today.atStartOfDay() : null;
        LocalDateTime updatedBefore = "today".equals(status) ? today.plusDays(1).atStartOfDay() : null;
        String assignedTo = mine ? currentAdminId : null;
        if (mine && (currentAdminId == null || currentAdminId.isBlank())) {
            return Page.empty(PageRequest.of(safePage, safeSize));
        }

        LoaiYeuCauDoiTra returnType = parseReturnType(type);
        LoaiHauMai afterType = parseAfterType(type);
        boolean returnTypeImpossible = type != null && !type.isBlank() && returnType == null;
        boolean afterTypeImpossible = type != null && !type.isBlank() && afterType == null;
        boolean includeReturn = source == null || source.isBlank() || "return".equals(source);
        boolean includeAfter = source == null || source.isBlank() || "after".equals(source);
        if (source != null && !source.isBlank() && !includeReturn && !includeAfter) {
            return Page.empty(PageRequest.of(safePage, safeSize));
        }

        if ((mine || deadlineOverdue) && status != null && Set.of("completed", "today", "rejected").contains(status)) {
            return Page.empty(PageRequest.of(safePage, safeSize));
        }
        List<Bucket> buckets = buckets(status);
        if ((mine || deadlineOverdue) && (status == null || status.isBlank())) {
            buckets = List.of(
                    new Bucket(RETURN_WAITING, AFTER_WAITING, false),
                    new Bucket(RETURN_PROCESSING, AFTER_PROCESSING, false));
        }
        List<AdminAfterSalesRowDto> candidates = new ArrayList<>();
        long total = 0;
        boolean ascending = "dateAsc".equals(sort);
        boolean dateSort = ascending || "dateDesc".equals(sort);
        List<Bucket> queryBuckets = dateSort ? List.of(mergeBuckets(buckets)) : buckets;

        for (Bucket bucket : queryBuckets) {
            if (includeReturn && !returnTypeImpossible && !bucket.returnStatuses().isEmpty()) {
                Page<YeuCauDoiTra> result = returns.searchAdmin(bucket.returnStatuses(), pattern, returnType,
                        start, end, assignedTo, deadlineOverdue, now, bucket.slaOnly(), now.minusHours(48),
                        updatedAfter, updatedBefore, ascending, PageRequest.of(0, fetchSize));
                total += result.getTotalElements();
                result.getContent().stream().map(this::toReturnRow).forEach(candidates::add);
            }
            if (includeAfter && !afterTypeImpossible && !bucket.afterStatuses().isEmpty()) {
                Page<YeuCauHauMai> result = after.searchAdmin(bucket.afterStatuses(), pattern, afterType,
                        start, end, assignedTo, deadlineOverdue, now, bucket.slaOnly(), now.minusHours(48),
                        updatedAfter, updatedBefore, ascending, PageRequest.of(0, fetchSize));
                total += result.getTotalElements();
                List<YeuCauHauMai> rows = result.getContent();
                Map<String, String> customerPhones = StreamSupport.stream(accounts.findAllById(rows.stream()
                                .map(this::ownerId).filter(id -> id != null).distinct().toList()).spliterator(), false)
                        .filter(account -> account.getSoDienThoai() != null && !account.getSoDienThoai().isBlank())
                        .collect(Collectors.toMap(account -> account.getMaTk(), account -> account.getSoDienThoai()));
                rows.stream().map(row -> toAfterRow(row, customerPhones)).forEach(candidates::add);
            }
        }

        Map<String, PhanCongHauMai> returnAssignments = assignmentMap(candidates, "return");
        Map<String, PhanCongHauMai> afterAssignments = assignmentMap(candidates, "after");

        Comparator<AdminAfterSalesRowDto> comparator = comparator(sort);
        List<AdminAfterSalesRowDto> sorted = candidates.stream()
                .map(row -> withAssignment(row, "return".equals(row.source()) ? returnAssignments.get(row.maYeuCau()) : afterAssignments.get(row.maYeuCau())))
                .sorted(comparator).toList();
        int fromIndex = (int) Math.min((long) safePage * safeSize, sorted.size());
        int toIndex = Math.min(fromIndex + safeSize, sorted.size());
        return new PageImpl<>(sorted.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), total);
    }

    public AdminAfterSalesSummaryDto summary() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        long waiting = returns.countByTrangThaiIn(RETURN_WAITING) + after.countByTrangThaiIn(AFTER_WAITING);
        long processing = returns.countByTrangThaiIn(RETURN_PROCESSING) + after.countByTrangThaiIn(AFTER_PROCESSING);
        long overdue = returns.countSlaOverdue(RETURN_WAITING, now.minusHours(48))
                + after.countSlaOverdue(AFTER_WAITING, now.minusHours(48));
        long completedToday = returns.countUpdatedByStatusAndRange(RETURN_COMPLETED, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                + after.countUpdatedByStatusAndRange(AFTER_COMPLETED, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        long total = returns.count() + after.count();
        return new AdminAfterSalesSummaryDto(waiting, processing, waiting + processing, overdue, completedToday, total);
    }

    private Bucket mergeBuckets(List<Bucket> buckets) {
        List<TrangThaiDoiTra> returnStatuses = buckets.stream().flatMap(b -> b.returnStatuses().stream()).distinct().toList();
        List<TrangThaiHauMai> afterStatuses = buckets.stream().flatMap(b -> b.afterStatuses().stream()).distinct().toList();
        return new Bucket(returnStatuses, afterStatuses, buckets.stream().anyMatch(Bucket::slaOnly));
    }

    private Map<String, PhanCongHauMai> assignmentMap(List<AdminAfterSalesRowDto> rows, String source) {
        List<String> ids = rows.stream().filter(row -> source.equals(row.source())).map(AdminAfterSalesRowDto::maYeuCau).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return assignments.findByNguonAndMaYeuCauIn(source, ids).stream()
                .collect(Collectors.toMap(PhanCongHauMai::getMaYeuCau, Function.identity()));
    }

    private List<Bucket> buckets(String status) {
        Bucket waiting = new Bucket(RETURN_WAITING, AFTER_WAITING, false);
        Bucket processing = new Bucket(RETURN_PROCESSING, AFTER_PROCESSING, false);
        Bucket rejected = new Bucket(RETURN_REJECTED, AFTER_REJECTED, false);
        Bucket completed = new Bucket(RETURN_COMPLETED, AFTER_COMPLETED, false);
        if (status == null || status.isBlank()) return List.of(waiting, processing, rejected, completed);
        return switch (status) {
            case "waiting" -> List.of(waiting);
            case "processing" -> List.of(processing);
            case "approved" -> List.of(new Bucket(List.of(TrangThaiDoiTra.DA_DUYET), List.of(), false));
            case "completed", "today" -> List.of(completed);
            case "rejected" -> List.of(rejected);
            case "overdue" -> List.of(new Bucket(RETURN_WAITING, AFTER_WAITING, true));
            case "open" -> List.of(waiting, processing);
            default -> List.of(waiting, processing, rejected, completed);
        };
    }

    private LoaiYeuCauDoiTra parseReturnType(String type) {
        try { return type == null || type.isBlank() ? null : LoaiYeuCauDoiTra.valueOf(type); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private LoaiHauMai parseAfterType(String type) {
        try { return type == null || type.isBlank() ? null : LoaiHauMai.valueOf(type); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private AdminAfterSalesRowDto toReturnRow(YeuCauDoiTra row) {
        ChiTietDoiTra first = row.getChiTiet().stream().findFirst().orElse(null);
        String product = first == null ? null : first.getChiTietDonHang().getTenSanPham() + " - " + first.getChiTietDonHang().getTenPhienBan();
        int quantity = row.getChiTiet().stream().mapToInt(ChiTietDoiTra::getSoLuong).sum();
        String phone = row.getDonHang().getSoDienThoaiNhan();
        return new AdminAfterSalesRowDto("return", row.getMaYeuCau(), row.getDonHang().getMaDonHang(),
                row.getDonHang().getKhachHang().getHoTen(), phone, first == null || first.getLoaiYeuCau() == null ? null : first.getLoaiYeuCau().name(),
                row.getLyDoChung(), product, quantity, row.getNgayTao(), row.getNgayCapNhat(), row.getTrangThai().name(),
                row.getGhiChuXuLy(), null, null, null);
    }

    private String ownerId(YeuCauHauMai row) {
        return row.getKhachHang() == null
                ? (row.getDonHang() == null || row.getDonHang().getKhachHang() == null ? null : row.getDonHang().getKhachHang().getMaKh())
                : row.getKhachHang().getMaKh();
    }

    private AdminAfterSalesRowDto toAfterRow(YeuCauHauMai row, Map<String, String> customerPhones) {
        var owner = row.getKhachHang() == null
                ? (row.getDonHang() == null ? null : row.getDonHang().getKhachHang())
                : row.getKhachHang();
        String phone = row.getDonHang() == null ? null : row.getDonHang().getSoDienThoaiNhan();
        if ((phone == null || phone.isBlank()) && owner != null) phone = customerPhones.get(owner.getMaKh());
        var line = row.getChiTietDonHang();
        String product = line == null ? null : line.getTenSanPham() + " - " + line.getTenPhienBan();
        return new AdminAfterSalesRowDto("after", row.getMaYeuCau(), row.getDonHang() == null ? null : row.getDonHang().getMaDonHang(),
                owner == null ? null : owner.getHoTen(), phone, row.getLoai().name(), row.getLyDo(), product, line == null ? 0 : line.getSoLuong(),
                row.getNgayTao(), row.getNgayCapNhat(), row.getTrangThai().name(), row.getGhiChuXuLy(), null, null, null);
    }

    private AdminAfterSalesRowDto withAssignment(AdminAfterSalesRowDto row, PhanCongHauMai assignment) {
        return assignment == null ? row : new AdminAfterSalesRowDto(row.source(), row.maYeuCau(), row.maDonHang(), row.tenKhach(),
                row.soDienThoai(), row.loai(), row.lyDo(), row.tenSanPham(), row.soLuong(), row.ngayTao(), row.ngayCapNhat(),
                row.trangThai(), row.ghiChuXuLy(), assignment.getMaNguoiPhuTrach(), assignment.getTenNguoiPhuTrach(), assignment.getHanXuLy());
    }

    private Comparator<AdminAfterSalesRowDto> comparator(String sort) {
        Comparator<AdminAfterSalesRowDto> newest = Comparator.comparing(AdminAfterSalesRowDto::ngayTao,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(AdminAfterSalesRowDto::maYeuCau);
        if ("dateAsc".equals(sort)) return Comparator.comparing(AdminAfterSalesRowDto::ngayTao,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(newest);
        if ("dateDesc".equals(sort)) return newest;
        Comparator<AdminAfterSalesRowDto> statusPriority = Comparator.comparingInt((AdminAfterSalesRowDto row) -> priority(row.trangThai()));
        if ("statusDesc".equals(sort)) statusPriority = statusPriority.reversed();
        return statusPriority.thenComparing(newest);
    }

    private int priority(String status) {
        if (Set.of("CHO_DUYET", "CHO_TIEP_NHAN").contains(status)) return 0;
        if (Set.of("DA_DUYET", "DANG_KIEM_TRA", "DANG_XAC_MINH", "DANG_XU_LY").contains(status)) return 1;
        if ("TU_CHOI".equals(status)) return 2;
        return 3;
    }
}
