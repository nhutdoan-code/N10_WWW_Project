package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.LyDoKhoaBan;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bien the (phien ban) san pham.
 *
 * <p>@DynamicUpdate: UPDATE chi ghi cac cot thuc su thay doi. Nho vay mot thao tac
 * chi doi ton kho (dat hang, nhap kho) khong ghi de cac cot phe duyet vua duoc
 * mot giao dich khac cap nhat.</p>
 */
@Entity
@DynamicUpdate
@Table(name = "PhienBanSanPham", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_PhienBan_CauHinh", columnNames = {"maSanPham", "ram", "boNho", "mauSac"})
})
@Getter
@Setter
public class PhienBanSanPham {

    @Id
    private String maPhienBan;

    @ManyToOne
    @JoinColumn(name = "maSanPham", nullable = false)
    private SanPham sanPham;

    @Column(nullable = false, unique = true)
    private String sku;

    private int ram;
    private int boNho;
    private String mauSac;
    private BigDecimal giaBan;
    private BigDecimal giaNiemYet;
    private int soLuongTon;

    @Enumerated(EnumType.STRING)
    private TrangThaiSanPham trangThai = TrangThaiSanPham.DANG_KINH_DOANH;

    /** Mac dinh an toan: bien the moi luon cho phe duyet. */
    @Enumerated(EnumType.STRING)
    @Column(name = "trangThaiPheDuyet", nullable = false)
    private TrangThaiPheDuyet trangThaiPheDuyet = TrangThaiPheDuyet.PENDING_REVIEW;

    @Column(name = "nguoiPheDuyet", length = 50)
    private String nguoiPheDuyet;

    @Column(name = "ngayPheDuyet")
    private LocalDateTime ngayPheDuyet;

    @Column(name = "lyDoPheDuyet", length = 255)
    private String lyDoPheDuyet;

    // ---- Migration 16: du lieu xac minh phe duyet ----

    @Column(name = "giaPheDuyet", precision = 15, scale = 2)
    private BigDecimal giaPheDuyet;

    @Column(name = "giaNiemYetPheDuyet", precision = 15, scale = 2)
    private BigDecimal giaNiemYetPheDuyet;

    @Column(name = "nguonXacMinh", length = 500)
    private String nguonXacMinh;

    /** SHA-256 cua du lieu ban tai thoi diem APPROVED (xem PheDuyetBanHangPolicy#dauVanTay). */
    @Column(name = "dauVanTayPheDuyet", length = 64)
    private String dauVanTayPheDuyet;

    @Enumerated(EnumType.STRING)
    @Column(name = "lyDoKhoa")
    private LyDoKhoaBan lyDoKhoa;

    @Column(name = "giaBanKhiKhoa", precision = 15, scale = 2)
    private BigDecimal giaBanKhiKhoa;
}
