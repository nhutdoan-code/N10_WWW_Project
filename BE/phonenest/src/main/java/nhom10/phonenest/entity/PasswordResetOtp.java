
package nhom10.phonenest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;



import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_otp")
@Getter
@Setter
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "maTk", nullable = false, length = 50)
    private String maTk;

    @Column(name = "otpHash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "otpExpiresAt", nullable = false)
    private LocalDateTime otpExpiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "otpVerified", nullable = false)
    private boolean otpVerified = false;

    @Column(name = "resetTokenHash", length = 255)
    private String resetTokenHash;

    @Column(name = "resetExpiresAt")
    private LocalDateTime resetExpiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;
}
