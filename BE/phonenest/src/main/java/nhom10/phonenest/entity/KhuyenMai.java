package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.LoaiKhuyenMai;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "KhuyenMai")
@Getter
@Setter
public class KhuyenMai {

    @Id
    private String maKm;
    private String tenKm;
    private BigDecimal phanTramGiam;
    private BigDecimal soTienGiamToiDa;
    private LocalDate ngayBatDau;
    private LocalDate ngayKetThuc;
    private String dieuKienApDung;

    @Enumerated(EnumType.STRING)
    private LoaiKhuyenMai loai = LoaiKhuyenMai.DON_HANG;

    private BigDecimal donToiThieu = BigDecimal.ZERO;
    private Integer soLuong;
    private int daDung;
    private boolean chiKhachMoi;
}
