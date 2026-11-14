package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.TrangThaiHauMai;

import java.time.LocalDateTime;

@Entity
@Table(name = "LichSuHauMai")
@Getter @Setter
public class LichSuHauMai {
    @Id
    @Column(length = 50)
    private String maLichSu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maYeuCau", nullable = false)
    private YeuCauHauMai yeuCau;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrangThaiHauMai trangThai;

    @Column(length = 500)
    private String ghiChu;

    @Column(nullable = false)
    private LocalDateTime thoiGian;
}
