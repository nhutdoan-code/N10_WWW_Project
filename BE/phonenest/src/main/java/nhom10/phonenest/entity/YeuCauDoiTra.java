package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.TrangThaiDoiTra;

@Entity
@Table(name = "YeuCauDoiTra")
@Getter
@Setter
public class YeuCauDoiTra {

    @Id
    private String maYeuCau;
    private String maDonHang;
    private String lyDo;
    private String hinhAnhMinhChung;
    private LocalDate ngayTao;

    @Enumerated(EnumType.STRING)
    private TrangThaiDoiTra trangThai;
}
