package nhom10.phonenest.entity;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.LocalDateTime; import java.util.*;
@Entity @Table(name="GioHang") @Getter @Setter
public class GioHang { @Id private String maGioHang; @OneToOne @JoinColumn(name="maKh",nullable=false,unique=true) private KhachHang khachHang; private LocalDateTime ngayTao; private LocalDateTime ngayCapNhat; @OneToMany(mappedBy="gioHang",cascade=CascadeType.ALL,orphanRemoval=true) private List<ChiTietGioHang> chiTiet=new ArrayList<>(); }
