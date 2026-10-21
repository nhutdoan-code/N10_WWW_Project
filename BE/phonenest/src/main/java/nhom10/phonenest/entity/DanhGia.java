package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "DanhGia")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DanhGia {
    @Id
    private String maDanhGia;
    private String maKh;
    private String maDonHang;
    private String maSanPham;
    private int diemSao;
    private String noiDungBinhLuan;
    private String hinhAnhThucTe;
    private LocalDate ngayDanhGia;
}
