package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.LoaiYeuCauDoiTra; import java.math.BigDecimal;
@Entity @Table(name="ChiTietDoiTra",uniqueConstraints=@UniqueConstraint(columnNames={"maYeuCau","maChiTietDonHang"})) @Getter @Setter
public class ChiTietDoiTra { @Id private String maChiTietDoiTra; @ManyToOne @JoinColumn(name="maYeuCau",nullable=false) private YeuCauDoiTra yeuCau; @ManyToOne @JoinColumn(name="maChiTietDonHang",nullable=false) private ChiTietDonHang chiTietDonHang; private int soLuong; @Enumerated(EnumType.STRING) private LoaiYeuCauDoiTra loaiYeuCau; @Column(columnDefinition="TEXT") private String lyDo; private String hinhAnhMinhChung; private BigDecimal soTienHoan; }
