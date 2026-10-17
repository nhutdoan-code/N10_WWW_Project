package nhom11.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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
}
