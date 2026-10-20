package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "PhiVanChuyen")
@Getter
@Setter
public class PhiVanChuyen {

    @Id
    private String maPhi;
    private String khuVuc;
    private BigDecimal phiCoBan;
    private BigDecimal mienPhiTu;
    private String thoiGianGiao;
}
