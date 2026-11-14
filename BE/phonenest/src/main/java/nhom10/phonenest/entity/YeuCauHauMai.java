package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.LoaiHauMai;
import nhom10.phonenest.enums.TrangThaiHauMai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "YeuCauHauMai")
@Getter @Setter
public class YeuCauHauMai {
    @Id
    @Column(length = 50)
    private String maYeuCau;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maDonHang", nullable = false)
    private DonHang donHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maChiTietDonHang")
    private ChiTietDonHang chiTietDonHang;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoaiHauMai loai;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrangThaiHauMai trangThai = TrangThaiHauMai.CHO_TIEP_NHAN;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String lyDo;

    @Column(length = 500)
    private String hinhAnhMinhChung;

    @Column(length = 500)
    private String ghiChuXuLy;

    @Column(length = 1000)
    private String ghiChuNoiBo;

    @Column(nullable = false)
    private LocalDateTime ngayTao;

    @Column(nullable = false)
    private LocalDateTime ngayCapNhat;

    @OneToMany(mappedBy = "yeuCau", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("thoiGian ASC")
    private List<LichSuHauMai> lichSu = new ArrayList<>();
}
