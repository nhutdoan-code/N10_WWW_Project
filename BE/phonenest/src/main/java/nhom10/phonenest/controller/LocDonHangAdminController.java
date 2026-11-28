
package nhom10.phonenest.controller;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.ThanhToan;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiThanhToan;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.DonHangRepository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/orders/filter")
@RequiredArgsConstructor
public class LocDonHangAdminController {

    private final DonHangRepository repository;
    private final DonHangMapper mapper;

    @GetMapping
    @Transactional(readOnly = true)
    public Page<DonHangDto> loc(
            @RequestParam(required = false) String q,
            @RequestParam(required = false)
            TrangThaiDonHang trangThai,

            @RequestParam(required = false)
            String nhomTrangThai,

            @RequestParam(defaultValue = "ngayMua")
            String ngayTheo,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false)
            BigDecimal minTotal,

            @RequestParam(required = false)
            BigDecimal maxTotal,

            @RequestParam(defaultValue = "ngay_moi")
            String sort,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        // Kiểm tra phân trang
        if (page < 0 || !List.of(10, 20, 50).contains(size)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phân trang không hợp lệ"
            );
        }

        if (nhomTrangThai != null && !"DANG_XU_LY".equals(nhomTrangThai)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhóm trạng thái không hợp lệ");
        }
        if (nhomTrangThai != null && trangThai != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chọn trạng thái đơn hoặc nhóm trạng thái");
        }
        if (!List.of("ngayMua", "ngayGiao").contains(ngayTheo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trường ngày lọc không hợp lệ");
        }

        // Kiểm tra khoảng ngày
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc"
            );
        }

        // Kiểm tra khoảng tiền
        if ((minTotal != null && minTotal.signum() < 0)
                || (maxTotal != null && maxTotal.signum() < 0)
                || (minTotal != null && maxTotal != null
                && minTotal.compareTo(maxTotal) > 0)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoảng tổng tiền không hợp lệ"
            );
        }

        // Sắp xếp
        Sort order = switch (sort) {
            case "ngay_moi" -> Sort.by(
                    Sort.Order.desc("ngayMua"),
                    Sort.Order.desc("maDonHang")
            );

            case "ngay_cu" -> Sort.by(
                    Sort.Order.asc("ngayMua"),
                    Sort.Order.asc("maDonHang")
            );

            case "ngay_giao_moi" -> Sort.by(
                    Sort.Order.desc("ngayGiao"),
                    Sort.Order.desc("maDonHang")
            );

            case "tien_cao" -> Sort.by(
                    Sort.Order.desc("tongThanhToan"),
                    Sort.Order.desc("maDonHang")
            );

            case "tien_thap" -> Sort.by(
                    Sort.Order.asc("tongThanhToan"),
                    Sort.Order.asc("maDonHang")
            );

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cách sắp xếp không hợp lệ"
            );
        };

        // Điều kiện tìm kiếm và lọc
        Specification<DonHang> spec = (root, query, cb) -> {

            List<Predicate> ps = new ArrayList<>();

            Subquery<Integer> paymentExists = query.subquery(Integer.class);
            var payment = paymentExists.from(ThanhToan.class);
            paymentExists.select(cb.literal(1)).where(
                    cb.equal(payment.get("donHang"), root),
                    cb.or(
                            cb.equal(payment.get("phuongThuc"), PhuongThucTt.COD),
                            cb.and(
                                    cb.equal(payment.get("phuongThuc"), PhuongThucTt.CHUYEN_KHOAN),
                                    payment.get("trangThai").in(
                                            TrangThaiThanhToan.DA_THANH_TOAN,
                                            TrangThaiThanhToan.CAN_HOAN_TIEN,
                                            TrangThaiThanhToan.DANG_HOAN_TIEN,
                                            TrangThaiThanhToan.DA_HOAN_TIEN)
                            ),
                            cb.and(
                                    cb.equal(payment.get("phuongThuc"), PhuongThucTt.VI_DIEN_TU),
                                            payment.get("trangThai").in(
                                                    TrangThaiThanhToan.DA_THANH_TOAN,
                                                    TrangThaiThanhToan.CAN_HOAN_TIEN,
                                                    TrangThaiThanhToan.DANG_HOAN_TIEN,
                                                    TrangThaiThanhToan.DA_HOAN_TIEN)
                            )
                    )
            );
            ps.add(cb.exists(paymentExists));

            // Lọc trạng thái
            if (trangThai != null) {
                ps.add(cb.equal(
                        root.get("trangThai"),
                        trangThai
                ));
            } else if ("DANG_XU_LY".equals(nhomTrangThai)) {
                ps.add(root.get("trangThai").in(List.of(
                        TrangThaiDonHang.CHO_XAC_NHAN,
                        TrangThaiDonHang.DA_XAC_NHAN,
                        TrangThaiDonHang.DANG_CHUAN_BI,
                        TrangThaiDonHang.DANG_GIAO
                )));
            }

            jakarta.persistence.criteria.Path<LocalDateTime> datePath = root.<LocalDateTime>get(ngayTheo);

            // Từ ngày
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(
                        datePath,
                        from.atStartOfDay()
                ));
            }

            // Đến ngày (bao gồm hết ngày)
            if (to != null) {
                ps.add(cb.lessThan(
                        datePath,
                        to.plusDays(1).atStartOfDay()
                ));
            }

            // Tổng tiền từ
            if (minTotal != null) {
                ps.add(cb.greaterThanOrEqualTo(
                        root.get("tongThanhToan"),
                        minTotal
                ));
            }

            // Tổng tiền đến
            if (maxTotal != null) {
                ps.add(cb.lessThanOrEqualTo(
                        root.get("tongThanhToan"),
                        maxTotal
                ));
            }

            // Tìm theo mã đơn, tên, SĐT
            if (q != null && !q.isBlank()) {

                String pattern = "%" + q.trim()
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_") + "%";

                ps.add(cb.or(
                        cb.like(
                                cb.lower(root.get("maDonHang")),
                                pattern, '\\'
                        ),
                        cb.like(
                                cb.lower(root.get("tenNguoiNhan")),
                                pattern, '\\'
                        ),
                        cb.like(
                                cb.lower(root.get("soDienThoaiNhan")),
                                pattern, '\\'
                        ),
                        cb.like(
                                cb.lower(root.get("diaChiGiaoHang")),
                                pattern, '\\'
                        ),
                        cb.like(
                                cb.lower(
                                        root.join("khachHang").get("hoTen")
                                ),
                                pattern, '\\'
                        )
                ));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };

        return repository
                .findAll(spec, PageRequest.of(page, size, order))
                .map(mapper::toDto);
    }
}
