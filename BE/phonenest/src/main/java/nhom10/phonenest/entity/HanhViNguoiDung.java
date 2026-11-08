package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.*;
import nhom10.phonenest.enums.LoaiHanhVi;
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

    // --- Bridge Compatibility Methods for PR #17 ---
    public String getVisitorId() {
        return sessionId;
    }

    public void setVisitorId(String visitorId) {
        this.sessionId = visitorId;
    }

    public String getMaTk() {
        return maKh;
    }

    public void setMaTk(String maTk) {
        this.maKh = maTk;
    }

    public String getProductId() {
        return maSanPham;
    }

    public void setProductId(String productId) {
        this.maSanPham = productId;
    }

    public String getSearchTerm() {
        return tuKhoa;
    }

    public void setSearchTerm(String searchTerm) {
        this.tuKhoa = searchTerm;
    }

    public String getPageType() {
        return nguonSuKien;
    }

    public void setPageType(String pageType) {
        this.nguonSuKien = pageType;
    }

    public void setLoaiHanhVi(LoaiHanhVi loai) {
        if (loai != null) {
            this.loaiHanhVi = switch (loai) {
                case PAGE_VIEW -> LoaiHanhViNguoiDung.PAGE_VIEW;
                case PRODUCT_VIEW -> LoaiHanhViNguoiDung.PRODUCT_VIEW;
                case SEARCH -> LoaiHanhViNguoiDung.SEARCH;
                case ADD_TO_CART -> LoaiHanhViNguoiDung.ADD_TO_CART;
            };
        }
    }
}
