package nhom10.phonenest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void gui(String den, String tieuDe, String noiDung) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.error("Chưa cấu hình SMTP; không thể gửi email.");
            throw new IllegalStateException("Email service chưa được cấu hình.");
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "Chưa cấu hình Gmail gửi email PhoneNest."
            );
        }
        mail.setFrom(from);
        mail.setTo(den);
        mail.setSubject(tieuDe);
        mail.setText(noiDung);

        try {
            sender.send(mail);
        } catch (MailException e) {
            log.error("Gửi email thất bại. Vui lòng kiểm tra cấu hình SMTP.");
            throw new IllegalStateException("Không thể gửi email lúc này.", e);
        }
    }
}