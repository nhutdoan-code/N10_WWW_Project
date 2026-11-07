package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.TrangThaiSanPham; import java.math.BigDecimal;
@Entity @Table(name="PhienBanSanPham", uniqueConstraints={@UniqueConstraint(name="UQ_PhienBan_CauHinh", columnNames={"maSanPham","ram","boNho","mauSac"})}) @Getter @Setter
public class PhienBanSanPham { @Id private String maPhienBan; @ManyToOne @JoinColumn(name="maSanPham",nullable=false) private SanPham sanPham; @Column(nullable=false,unique=true) private String sku; private int ram; private int boNho; private String mauSac; private BigDecimal giaBan; private BigDecimal giaNiemYet; private int soLuongTon; @Enumerated(EnumType.STRING) private TrangThaiSanPham trangThai=TrangThaiSanPham.DANG_KINH_DOANH; }
