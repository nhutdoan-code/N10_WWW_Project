package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.LoaiBienDongKho; import java.time.LocalDateTime;
@Entity @Table(name="BienDongKho") @Getter @Setter
public class BienDongKho { @Id private String maBienDong; @ManyToOne @JoinColumn(name="maPhienBan",nullable=false) private PhienBanSanPham phienBan; @Enumerated(EnumType.STRING) private LoaiBienDongKho loaiBienDong; private int soLuong; private int tonTruoc; private int tonSau; @ManyToOne @JoinColumn(name="maDonHang") private DonHang donHang; private LocalDateTime thoiGian; private String ghiChu; }
