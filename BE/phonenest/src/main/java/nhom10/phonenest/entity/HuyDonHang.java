package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "HuyDonHang")
@Getter
@Setter
public class HuyDonHang {

    @Id
    private String maDonHang;
    private String lyDo;
    private String ghiChu;
    private LocalDateTime ngayHuy;
}
