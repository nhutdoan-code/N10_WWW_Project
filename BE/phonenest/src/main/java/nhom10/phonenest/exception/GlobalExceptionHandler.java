package nhom10.phonenest.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, String>> handleApp(AppException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleData(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Du lieu dang duoc su dung hoac bi trung, khong the thuc hien"));
    }

    /**
     * Resource static khong ton tai (vi du /uploads/khong-co.jpg) phai la 404.
     * Neu de roi vao handler Exception chung thi Spring se tra 500.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Khong tim thay tai nguyen"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Anh khong duoc vuot qua 5MB"));
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MultipartException.class
    })
    public ResponseEntity<Map<String, String>> handleBadUpload(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Du lieu upload khong hop le"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "He thong dang gap su co ky thuat, vui long thu lai sau!"));
    }
}
