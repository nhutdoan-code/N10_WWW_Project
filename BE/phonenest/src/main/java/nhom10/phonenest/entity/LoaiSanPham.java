package nhom11.phonenest.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "LoaiSanPham")
@Getter
@Setter
public class LoaiSanPham {

    @Id
    private String maLoai;
    private String tenLoai;
    private String moTa;
    private String maLoaiCha;
}
