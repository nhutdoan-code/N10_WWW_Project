package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import nhom10.phonenest.enums.TrangThaiDoiTra; import java.time.LocalDateTime; import java.util.*;
@Entity @Table(name="YeuCauDoiTra") @Getter @Setter
public class YeuCauDoiTra { @Id private String maYeuCau; @ManyToOne @JoinColumn(name="maDonHang",nullable=false) private DonHang donHang; @Column(columnDefinition="TEXT") private String lyDoChung; private LocalDateTime ngayTao; @Enumerated(EnumType.STRING) private TrangThaiDoiTra trangThai=TrangThaiDoiTra.CHO_DUYET; private String ghiChuXuLy; @OneToMany(mappedBy="yeuCau",cascade=CascadeType.ALL,orphanRemoval=true) private List<ChiTietDoiTra> chiTiet=new ArrayList<>(); }
