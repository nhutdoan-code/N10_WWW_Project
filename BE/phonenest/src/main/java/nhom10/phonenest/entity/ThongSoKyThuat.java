package nhom10.phonenest.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
public class ThongSoKyThuat {

    private BigDecimal kichThuocManHinh;
    private String loaiManHinh;
    private Integer tanSoQuet;
    private String chip;
    private Integer ram;
    private Integer boNho;
    private String cameraSau;
    private Integer cameraTruoc;
    private Integer pin;
    private Integer congSuatSac;
    private String heDieuHanh;
    private boolean nfc;
    private boolean ho5G;
    private Integer trongLuong;
}
