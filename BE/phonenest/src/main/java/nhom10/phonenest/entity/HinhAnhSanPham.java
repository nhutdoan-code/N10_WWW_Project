
package nhom10.phonenest.entity;

import jakarta.persistence.*;
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
    @JoinColumn(name = "maSanPham", nullable = false)
    private SanPham sanPham;

    private String duongDan;

    private String gocChup;

    private boolean anhChinh;
}
