package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "TaiKhoan")
@Getter
@Setter
public class TaiKhoan {

    @Id
    private String maTk;
    private String email;
    private String matKhau;
    private LocalDate ngayTao;
    private boolean trangThaiHoatDong;
    private String loaiDangNhap;
    private String soDienThoai;
}
