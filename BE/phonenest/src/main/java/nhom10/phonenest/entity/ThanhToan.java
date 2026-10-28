package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@Entity @Table(name="ThanhToan") @Getter @Setter
public class ThanhToan { @Id private String maThanhToan; @ManyToOne @JoinColumn(name="maDonHang",nullable=false) private DonHang donHang; @Enumerated(EnumType.STRING) private PhuongThucTt phuongThuc; private BigDecimal soTien; @Enumerated(EnumType.STRING) private TrangThaiThanhToan trangThai=TrangThaiThanhToan.CHO_THANH_TOAN; @Column(unique=true) private String maGiaoDich; private LocalDateTime ngayTao; private LocalDateTime ngayThanhToan; }
