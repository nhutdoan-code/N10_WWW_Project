package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.*;
import nhom10.phonenest.enums.LoaiHanhViNguoiDung;

import java.time.LocalDateTime;

@Entity
@Table(name = "HanhViNguoiDung")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanhViNguoiDung {

    @Id
    @Column(name = "maHanhVi", length = 50)
    private String maHanhVi;

    @Column(name = "maKh", length = 50)
    private String maKh;

    @Column(name = "sessionId", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "maSanPham", length = 50)
    private String maSanPham;

    @Column(name = "maPhienBan", length = 50)
    private String maPhienBan;

    @Enumerated(EnumType.STRING)
    @Column(name = "loaiHanhVi", nullable = false)
    private LoaiHanhViNguoiDung loaiHanhVi;

    @Column(name = "tuKhoa", length = 255)
    private String tuKhoa;

    @Column(name = "metadata", length = 1000)
    private String metadata;

    @Column(name = "nguonSuKien", length = 100)
    private String nguonSuKien;

    @Column(name = "thoiGian", nullable = false)
    private LocalDateTime thoiGian;
}
