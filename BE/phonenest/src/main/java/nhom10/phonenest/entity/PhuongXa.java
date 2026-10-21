package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "PhuongXa")
@Getter
@Setter
public class PhuongXa {

    @Id
    private String maPhuong;
    private String tenPhuong;
    private String maTinh;
}
