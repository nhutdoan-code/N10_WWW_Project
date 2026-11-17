
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
@Table(name = "cuochoithoaichat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuocHoiThoaiChat {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "maTk", nullable = false, length = 50)
    private String maTk;

    @Column(name = "tieuDe", length = 255)
    private String tieuDe;

    @Column(name = "ngayTao", nullable = false, updatable = false)
    private LocalDateTime ngayTao;

    @Column(name = "ngayCapNhat", nullable = false)
    private LocalDateTime ngayCapNhat;
}
