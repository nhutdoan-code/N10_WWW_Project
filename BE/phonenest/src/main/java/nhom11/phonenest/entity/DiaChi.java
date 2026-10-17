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
@Table(name = "DiaChi")
@Getter
@Setter
public class DiaChi {

    @Id
    private String maDiaChi;
    private String maKh;
    private String tenNguoiNhan;
    private String soDienThoaiNhan;
    private String diaChiNhanHang;
    private String tinhThanhPho;
    private boolean macDinh;
}
