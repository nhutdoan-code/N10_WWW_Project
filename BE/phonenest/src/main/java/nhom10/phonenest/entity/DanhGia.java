package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="DanhGia") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DanhGia { @Id private String maDanhGia; @ManyToOne @JoinColumn(name="maKh",nullable=false) private KhachHang khachHang; @OneToOne @JoinColumn(name="maChiTietDonHang",nullable=false,unique=true) private ChiTietDonHang chiTietDonHang; @ManyToOne @JoinColumn(name="maSanPham",nullable=false) private SanPham sanPham; private int diemSao; @Column(columnDefinition="TEXT") private String noiDungBinhLuan; private String hinhAnhThucTe; private LocalDateTime ngayDanhGia; }
