package nhom10.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "PhiVanChuyen")
@Getter
@Setter
public class PhiVanChuyen {

    @Id
    private String maPhi;
    private String maTinh;
    /** Vùng giao hàng tính từ kho TP.HCM: 0 nội tỉnh, 1 lân cận & miền Nam, 2 miền Trung & Tây Nguyên, 3 miền Bắc. */
    private Integer vung;
    /** Phí giao đến phường (nội thành / trung tâm). */
    private BigDecimal phiCoBan;
    /** Cộng thêm khi địa chỉ là xã (ngoại thành). */
    private BigDecimal phuPhiXa = BigDecimal.ZERO;
    /** Cộng thêm khi địa chỉ là đặc khu / huyện đảo. */
    private BigDecimal phuPhiDacKhu = BigDecimal.ZERO;
    private BigDecimal mienPhiTu;
    private Integer soNgayGiaoToiThieu;
    private Integer soNgayGiaoToiDa;
    /** Chuỗi hiển thị, sinh tự động từ số ngày giao. */
    private String thoiGianGiao;
}
