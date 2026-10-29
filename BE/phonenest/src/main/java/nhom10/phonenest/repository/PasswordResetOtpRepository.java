
package nhom10.phonenest.repository;

import nhom10.phonenest.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PasswordResetOtpRepository
        extends JpaRepository<PasswordResetOtp, Long> {

    // Lấy yêu cầu OTP mới nhất chưa được sử dụng
    Optional<PasswordResetOtp>
    findTopByMaTkAndUsedFalseOrderByCreatedAtDesc(String maTk);

    // Tìm yêu cầu đặt lại mật khẩu bằng hash của token
    Optional<PasswordResetOtp>
    findByResetTokenHashAndUsedFalse(String resetTokenHash);

    // Vô hiệu hóa các yêu cầu đặt lại mật khẩu cũ của tài khoản
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE PasswordResetOtp p
        SET p.used = true
        WHERE p.maTk = :maTk
          AND p.used = false
    """)
    int invalidateActiveByMaTk(@Param("maTk") String maTk);
}
