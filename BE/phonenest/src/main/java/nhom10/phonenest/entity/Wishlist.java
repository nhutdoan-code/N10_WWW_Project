package nhom11.phonenest.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "Wishlist")
@Getter
@Setter
public class Wishlist {

    @Id
    private String maDanhSach;
    private String maKh;

    @ElementCollection
    @CollectionTable(name = "WishlistSanPham", joinColumns = @JoinColumn(name = "maDanhSach"))
    @Column(name = "maSanPham")
    private Set<String> sanPhams = new HashSet<>();
}
