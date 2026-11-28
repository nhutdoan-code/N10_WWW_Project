package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.TrangThaiDoiTra;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "YeuCauDoiTra")
@Getter
@Setter
public class YeuCauDoiTra {
    @Id
    private String maYeuCau;

    @ManyToOne
    @JoinColumn(name = "maDonHang", nullable = false)
    private DonHang donHang;

    @Column(columnDefinition = "TEXT")
    private String lyDoChung;

    private LocalDateTime ngayTao;
    private LocalDateTime ngayCapNhat;

    @Enumerated(EnumType.STRING)
    private TrangThaiDoiTra trangThai = TrangThaiDoiTra.CHO_DUYET;

    private String ghiChuXuLy;
    private String ghiChuNoiBo;
    private String hinhThucHoanTien;
    private String maGiaoDichHoanTien;
    private LocalDateTime ngayHoanTien;

    @Column(length = 20)
    private String trangThaiHoanVnPay;

    @Column(length = 32)
    private String maYeuCauHoanVnPay;

    private LocalDateTime ngayYeuCauHoanVnPay;

    @Column(length = 245)
    private String nguoiYeuCauHoanVnPay;

    @Column(length = 500)
    private String thongBaoHoanVnPay;

    @OneToMany(mappedBy = "yeuCau", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChiTietDoiTra> chiTiet = new ArrayList<>();
}
