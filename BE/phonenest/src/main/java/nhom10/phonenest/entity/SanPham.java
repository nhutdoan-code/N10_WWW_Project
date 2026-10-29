
package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.TrangThaiSanPham;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "SanPham")
@Getter
@Setter
public class SanPham {

 @Id
 private String maSanPham;

 @ManyToOne
 @JoinColumn(name = "maLoai", nullable = false)
 private LoaiSanPham loai;

 @Column(name = "tenMay", nullable = false, length = 150)
 private String tenMay;

 @Column(columnDefinition = "TEXT")
 private String moTa;
 @Column(name = "thoiHanBaoHanhThang", nullable = false)
 private Integer thoiHanBaoHanhThang = 12;

 private BigDecimal giaBan;

 private BigDecimal kichThuocManHinh;

 private String loaiManHinh;

 private Integer tanSoQuet;

 private String chip;

 private Integer ram;

 private Integer boNho;

 private String cameraSau;

 private Integer cameraTruoc;

 private Integer pin;

 private Integer congSuatSac;

 private String heDieuHanh;

 private boolean nfc;

 @Column(name = "ho5G")
 private boolean ho5G;

 private Integer trongLuong;

 private Integer soLuongTon;

 @Enumerated(EnumType.STRING)
 private TrangThaiSanPham trangThai =
         TrangThaiSanPham.CON_HANG;

 private BigDecimal diemDanhGiaTb = BigDecimal.ZERO;

 @OneToMany(
         mappedBy = "sanPham",
         fetch = FetchType.LAZY
 )
 private List<HinhAnhSanPham> hinhAnhs =
         new ArrayList<>();

}
