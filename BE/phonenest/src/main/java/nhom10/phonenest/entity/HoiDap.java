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
@Table(name = "HoiDap")
@Getter
@Setter
public class HoiDap {

    @Id
    private String maCauHoi;
    private String maKh;
    private String maSanPham;
    private String noiDungCauHoi;
    private String noiDungTraLoi;
    private LocalDate ngayHoi;
    private LocalDate ngayTraLoi;
}
