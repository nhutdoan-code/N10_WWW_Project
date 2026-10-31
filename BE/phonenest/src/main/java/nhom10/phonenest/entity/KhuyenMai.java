package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.LoaiKhuyenMai; import java.math.BigDecimal; import java.time.LocalDate;
@Entity @Table(name="KhuyenMai") @Getter @Setter
public class KhuyenMai { @Id private String maKm; private String tenKm; @Enumerated(EnumType.STRING) private LoaiKhuyenMai loai=LoaiKhuyenMai.DON_HANG; private BigDecimal phanTramGiam; private BigDecimal soTienGiamToiDa; private BigDecimal donToiThieu=BigDecimal.ZERO; private Integer soLuong; private int daDung; private boolean chiKhachMoi; private int gioiHanMoiKhach=1; private LocalDate ngayBatDau; private LocalDate ngayKetThuc; @Column(columnDefinition="TEXT") private String dieuKienApDung; private boolean trangThaiHoatDong=true; }
