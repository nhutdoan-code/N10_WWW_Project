package nhom10.phonenest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "PhanCongHauMai", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_PhanCongHauMai_YeuCau", columnNames = {"nguon", "maYeuCau"})
}, indexes = {
        @Index(name = "IDX_PhanCongHauMai_NguoiHan", columnList = "maNguoiPhuTrach,hanXuLy")
})
@Getter
@Setter
public class PhanCongHauMai {
    @Id
    @Column(length = 50)
    private String maPhanCong;

    @Column(nullable = false, length = 12)
    private String nguon;

    @Column(nullable = false, length = 50)
    private String maYeuCau;

    @Column(nullable = false, length = 50)
    private String maNguoiPhuTrach;

    @Column(nullable = false, length = 150)
    private String tenNguoiPhuTrach;

    @Column(nullable = false)
    private LocalDateTime hanXuLy;

    @Column(nullable = false, length = 50)
    private String capNhatBoi;

    @Column(nullable = false)
    private LocalDateTime capNhatLuc;
}
