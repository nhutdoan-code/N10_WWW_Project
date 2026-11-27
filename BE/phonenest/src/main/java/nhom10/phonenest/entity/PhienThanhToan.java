package nhom10.phonenest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiPhienThanhToan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "PhienThanhToan",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_PhienThanhToan_maKh_idempotencyKey",
                columnNames = {"maKh", "idempotencyKey"}
        )
)
@Getter
@Setter
public class PhienThanhToan {
    @Id
    @Column(length = 50)
    private String maPhienThanhToan;

    @ManyToOne
    @JoinColumn(name = "maKh", nullable = false)
    private KhachHang khachHang;

    @Column(nullable = false, length = 36)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PhuongThucTt phuongThuc;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal soTien;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String duLieuCheckout;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TrangThaiPhienThanhToan trangThai;

    @Column(length = 3000)
    private String payUrl;

    @Column(length = 50)
    private String maDonHang;

    @Column(length = 100)
    private String maGiaoDichNhaCungCap;

    @Column(length = 100)
    private String maGiaoDichHoanTien;

    @Column(length = 500)
    private String lyDoHoanTien;

    @Column(nullable = false)
    private LocalDateTime ngayTao;

    @Column(nullable = false)
    private LocalDateTime ngayCapNhat;
}
