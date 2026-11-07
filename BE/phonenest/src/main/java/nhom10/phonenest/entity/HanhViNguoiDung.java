package nhom10.phonenest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.LoaiHanhVi;

import java.time.LocalDateTime;

@Entity
@Table(name = "HanhViNguoiDung")
@Getter
@Setter
public class HanhViNguoiDung {

    @Id
    @Column(length = 36)
    private String maHanhVi;

    @Column(nullable = false, length = 36)
    private String visitorId;

    @Column(length = 50)
    private String maTk;

    @Enumerated(EnumType.STRING)
    @Column(name = "loaiHanhVi", nullable = false, length = 20)
    private LoaiHanhVi loaiHanhVi;

    @Column(length = 50)
    private String productId;

    @Column(length = 120)
    private String searchTerm;

    @Column(length = 30)
    private String pageType;

    @Column(nullable = false)
    private LocalDateTime thoiGian;
}
