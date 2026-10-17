package nhom10.phonenest.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "GioHang")
@Getter
@Setter
public class GioHang {

    @Id
    private String maGioHang;
    private String maKh;
    private LocalDate ngayTao;

    @ElementCollection
    @CollectionTable(name = "ChiTietGioHang", joinColumns = @JoinColumn(name = "maGioHang"))
    @MapKeyColumn(name = "maSanPham")
    @Column(name = "soLuong")
    private Map<String, Integer> items = new HashMap<>();
}
