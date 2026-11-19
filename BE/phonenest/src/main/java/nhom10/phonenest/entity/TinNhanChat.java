package nhom10.phonenest.entity;

/*
 * @description
 * @author: LuuVanQuoc
 * @date: 11/15/2026
 * @version: 1.0
 */

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TinNhanChat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TinNhanChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cuocHoiThoaiId", nullable = false, length = 36)
    private String cuocHoiThoaiId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "noiDung", nullable = false, columnDefinition = "TEXT")
    private String noiDung;

    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime ngayTao;
}
