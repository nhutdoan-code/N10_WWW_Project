package nhom11.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nhom11.phonenest.enums.TrangThaiSanPham;

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
    @JoinColumn(name = "maLoai")
    private LoaiSanPham loai;

    private String tenMay;
    private BigDecimal giaBan;
    private String moTa;
    private String thongSoKyThuat;
    private int soLuongTon;

    @Enumerated(EnumType.STRING)
    private TrangThaiSanPham trangThai;

    private BigDecimal diemDanhGiaTb;

    @OneToMany(mappedBy = "sanPham")
    private List<HinhAnhSanPham> hinhAnhs = new ArrayList<>();
}
