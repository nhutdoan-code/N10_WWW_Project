package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "ChiTietDonHang", uniqueConstraints = {@UniqueConstraint(columnNames = {"maDonHang", "maPhienBan"})})
@Getter
@Setter
public class ChiTietDonHang {

    @Id
    private String maChiTiet;

    @ManyToOne
    @JoinColumn(name = "maDonHang", nullable = false)
    private DonHang donHang;

    @ManyToOne
    @JoinColumn(name = "maPhienBan", nullable = false)
    private PhienBanSanPham phienBan;

    // Giữ mã sản phẩm để bảo toàn liên kết đánh giá cũ
    @Column(name = "maSanPham", nullable = false, length = 50)
    private String maSanPham;

    private String tenSanPham;

    private String tenPhienBan;

    private String sku;

    private String hinhAnh;

    private int soLuong;

    private BigDecimal donGia;

    private int baoHanhThang = 12;
}