package nhom11.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "HinhAnhSanPham")
@Getter
@Setter
public class HinhAnhSanPham {

    @Id
    private String maHinhAnh;

    @ManyToOne
    @JoinColumn(name = "maSanPham")
    private SanPham sanPham;

    private String duongDan;
    private String gocChup;
    private boolean anhChinh;
}
