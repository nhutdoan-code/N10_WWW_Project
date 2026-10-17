package nhom11.phonenest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void gui(String den, String tieuDe, String noiDung) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[EMAIL] Đến: {} | Tiêu đề: {} | Nội dung: {}", den, tieuDe, noiDung);
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(den);
            mail.setSubject(tieuDe);
            mail.setText(noiDung);
            sender.send(mail);
        } catch (Exception e) {
            log.warn("Không gửi được email tới {}: {}", den, e.getMessage());
        }
    }
}
