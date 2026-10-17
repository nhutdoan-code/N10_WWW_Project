package nhom11.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ThongBao")
@Getter
@Setter
public class ThongBao {

    @Id
    private String maThongBao;
    private String maTk;
    private String noiDung;
    private boolean daDoc;
    private LocalDateTime ngayTao;
}
