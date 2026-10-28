package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Entity @Table(name="ThuongHieu") @Getter @Setter
public class ThuongHieu { @Id private String maThuongHieu; private String tenThuongHieu; private String moTa; private String logo; private boolean trangThaiHoatDong = true; }
