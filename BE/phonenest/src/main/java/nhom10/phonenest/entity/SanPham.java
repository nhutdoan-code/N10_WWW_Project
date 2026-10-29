package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.TrangThaiSanPham; import java.math.BigDecimal; import java.util.*;
@Entity @Table(name="SanPham") @Getter @Setter
public class SanPham {
 @Id private String maSanPham;
 @ManyToOne @JoinColumn(name="maLoai", nullable=false) private LoaiSanPham loai;
 @ManyToOne @JoinColumn(name="maThuongHieu", nullable=false) private ThuongHieu thuongHieu;
 private String tenMay; @Column(columnDefinition="TEXT") private String moTa;
 private BigDecimal kichThuocManHinh; private String loaiManHinh; private Integer tanSoQuet; private String chip; private String cameraSau; private String cameraTruoc; private Integer pin; private Integer congSuatSac; private String heDieuHanh; private boolean nfc; @Column(name="ho5G") private boolean ho5G; private Integer trongLuong; private int thoiHanBaoHanhThang = 12;
 @Enumerated(EnumType.STRING) private TrangThaiSanPham trangThai = TrangThaiSanPham.DANG_KINH_DOANH;
 private BigDecimal diemDanhGiaTb = BigDecimal.ZERO;
 @OneToMany(mappedBy="sanPham") private List<PhienBanSanPham> phienBans = new ArrayList<>();
 @OneToMany(mappedBy="sanPham") private List<HinhAnhSanPham> hinhAnhs = new ArrayList<>();
 @Transient public int getSoLuongTon(){ return phienBans.stream().filter(p -> p.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH).mapToInt(PhienBanSanPham::getSoLuongTon).sum(); }
 @Transient public BigDecimal getGiaBan(){ return phienBans.stream().filter(p -> p.getTrangThai() == TrangThaiSanPham.DANG_KINH_DOANH).map(PhienBanSanPham::getGiaBan).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO); }
}
