package nhom11.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "QuanTriVien")
@Getter
@Setter
public class QuanTriVien {

    @Id
    private String maQtv;
    private String hoTen;
    private String vaiTro;
    private String chucVu;
}
