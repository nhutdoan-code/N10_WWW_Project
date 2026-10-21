package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "TinhThanh")
@Getter
@Setter
public class TinhThanh {

    @Id
    private String maTinh;
    private String tenTinh;
}
