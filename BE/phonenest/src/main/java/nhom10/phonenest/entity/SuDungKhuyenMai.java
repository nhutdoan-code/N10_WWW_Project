package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime;
@Entity @Table(name="SuDungKhuyenMai",uniqueConstraints=@UniqueConstraint(columnNames={"maKm","maDonHang"})) @Getter @Setter
public class SuDungKhuyenMai { @Id private String maSuDung; @ManyToOne @JoinColumn(name="maKm",nullable=false) private KhuyenMai khuyenMai; @ManyToOne @JoinColumn(name="maKh",nullable=false) private KhachHang khachHang; @ManyToOne @JoinColumn(name="maDonHang") private DonHang donHang; private LocalDateTime ngaySuDung; }
