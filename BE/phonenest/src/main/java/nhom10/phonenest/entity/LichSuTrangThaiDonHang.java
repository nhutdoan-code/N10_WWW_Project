package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.TrangThaiDonHang; import java.time.LocalDateTime;
@Entity @Table(name="LichSuTrangThaiDonHang") @Getter @Setter
public class LichSuTrangThaiDonHang { @Id private String maLichSu; @ManyToOne @JoinColumn(name="maDonHang",nullable=false) private DonHang donHang; @Enumerated(EnumType.STRING) private TrangThaiDonHang trangThai; private LocalDateTime thoiGian; private String ghiChu; @ManyToOne @JoinColumn(name="nguoiCapNhat") private TaiKhoan nguoiCapNhat; }
