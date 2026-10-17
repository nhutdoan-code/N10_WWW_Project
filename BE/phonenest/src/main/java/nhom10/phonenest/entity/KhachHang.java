package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "KhachHang")
@Getter
@Setter
public class KhachHang {

    @Id
    private String maKh;
    private String hoTen;
    private LocalDate ngaySinh;
    private String anhDaiDien;
    private int diemTichLuy;
    private String hangThanhVien;
    private String maGioiThieu;
}
