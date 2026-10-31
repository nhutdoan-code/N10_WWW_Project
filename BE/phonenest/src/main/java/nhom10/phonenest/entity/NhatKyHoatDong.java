package nhom10.phonenest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nhom10.phonenest.enums.HanhDongAudit;
import nhom10.phonenest.enums.LoaiDoiTuongAudit;

import java.time.LocalDateTime;

/**
 * Nhat ky hoat dong quan tri.
 *
 * <p>Chi ghi va doc, khong co nghiep vu sua/xoa. Nguoi thuc hien va doi tuong
 * duoc luu dang String snapshot (khong @ManyToOne) de lich su khong bi thay doi
 * khi tai khoan hoac doi tuong nghiep vu bi xoa.
 */
@Entity
@Table(name = "NhatKyHoatDong")
@Getter
@Setter
public class NhatKyHoatDong {

    @Id
    private String maNhatKy;

    private String maTk;

    private String tenNguoiThucHien;

    @Enumerated(EnumType.STRING)
    private HanhDongAudit hanhDong;

    @Enumerated(EnumType.STRING)
    private LoaiDoiTuongAudit loaiDoiTuong;

    private String maDoiTuong;

    private String moTa;

    @Column(columnDefinition = "LONGTEXT")
    private String duLieuTruoc;

    @Column(columnDefinition = "LONGTEXT")
    private String duLieuSau;

    private String ipAddress;

    private String requestMethod;

    private String requestPath;

    private LocalDateTime thoiGian;
}
