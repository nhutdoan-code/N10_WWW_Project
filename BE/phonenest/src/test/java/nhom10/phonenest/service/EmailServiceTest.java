package nhom10.phonenest.service;

import nhom10.phonenest.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender javaMailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSenderProvider, "no-reply@phonenest.vn");
    }

    @Test
    @DisplayName("gui throws SERVICE_UNAVAILABLE when JavaMailSender is unavailable (no console mock fallback)")
    void gui_WhenSenderUnavailable_ThrowsServiceUnavailable() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        AppException ex = assertThrows(AppException.class, () ->
                emailService.gui("user@example.com", "Test Title", "Secret OTP 123456"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("không khả dụng"));
    }

    @Test
    @DisplayName("gui throws SERVICE_UNAVAILABLE when JavaMailSender throws exception (SMTP error handled safely)")
    void gui_WhenMailSenderFails_ThrowsServiceUnavailable() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        doThrow(new MailSendException("SMTP connection failed")).when(javaMailSender).send(any(SimpleMailMessage.class));

        AppException ex = assertThrows(AppException.class, () ->
                emailService.gui("user@example.com", "Test Title", "Secret OTP 123456"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("Không thể gửi email"));
    }

    @Test
    @DisplayName("gui succeeds when JavaMailSender successfully delivers mail")
    void gui_WhenMailSenderSucceeds_SendsCorrectMessage() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

        assertDoesNotThrow(() ->
                emailService.gui("user@example.com", "Test Title", "Your verification code is 123456"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertArrayEquals(new String[]{"user@example.com"}, sent.getTo());
        assertEquals("Test Title", sent.getSubject());
        assertEquals("Your verification code is 123456", sent.getText());
        assertEquals("no-reply@phonenest.vn", sent.getFrom());
    }
}
