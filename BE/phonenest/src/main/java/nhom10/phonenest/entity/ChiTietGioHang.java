package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "chitietgiohang")
@Getter
@Setter
@NoArgsConstructor
public class ChiTietGioHang {

 @EmbeddedId
 private Id id = new Id();

 @ManyToOne
 @MapsId("maGioHang")
 @JoinColumn(name = "maGioHang")
 private GioHang gioHang;

 @ManyToOne
 @MapsId("maSanPham")
 @JoinColumn(name = "maSanPham")
 private SanPham sanPham;

 @Column(name = "soLuong", nullable = false)
 private int soLuong = 1;

 @Embeddable
 @Getter
 @Setter
 @NoArgsConstructor
 @AllArgsConstructor
 @EqualsAndHashCode
 public static class Id implements Serializable {

  private String maGioHang;
  private String maSanPham;
 }
}