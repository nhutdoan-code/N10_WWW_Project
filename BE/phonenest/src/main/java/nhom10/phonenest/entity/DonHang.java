package nhom10.phonenest.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.PhuongThucTt;
import nhom10.phonenest.enums.TrangThaiDonHang;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "DonHang")
@Getter
@Setter
public class DonHang {

    @Id
    private String maDonHang;
    private String maKh;

    @ManyToOne
    @JoinColumn(name = "maKm")
    private KhuyenMai khuyenMai;

    private LocalDateTime ngayMua;
    private LocalDateTime ngayCapNhat;
    private String diaChiGiaoHang;
    private LocalDateTime ngayGiao;
    private String maKmVanChuyen;
    private BigDecimal giamGia = BigDecimal.ZERO;
    private BigDecimal phiVanChuyen = BigDecimal.ZERO;
    private BigDecimal giamPhiVanChuyen = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private PhuongThucTt phuongThucTt;

    @Enumerated(EnumType.STRING)
    private TrangThaiDonHang trangThai;

    @ElementCollection
    @CollectionTable(name = "ChiTietDonHang", joinColumns = @JoinColumn(name = "maDonHang"))
    private List<ChiTietDonHang> chiTiet = new ArrayList<>();
}
