package nhom10.phonenest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PhoneNestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhoneNestApplication.class, args);
    }
}
