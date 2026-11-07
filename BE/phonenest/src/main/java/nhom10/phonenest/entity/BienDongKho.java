package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.LoaiBienDongKho; import java.time.LocalDateTime;
@Entity @Table(name="BienDongKho") @Getter @Setter
public class BienDongKho {
    @Id private String maBienDong;
    @ManyToOne @JoinColumn(name="maPhienBan",nullable=false) private PhienBanSanPham phienBan;
    @Enumerated(EnumType.STRING) private LoaiBienDongKho loaiBienDong;
    private int soLuong;
    private int tonTruoc;
    private int tonSau;
    @ManyToOne @JoinColumn(name="maDonHang") private DonHang donHang;
    @ManyToOne @JoinColumn(name="maYeuCau") private YeuCauDoiTra yeuCau;
    @ManyToOne @JoinColumn(name="nguoiThucHien") private TaiKhoan nguoiThucHien;
    private LocalDateTime thoiGian;
    private String ghiChu;
}
