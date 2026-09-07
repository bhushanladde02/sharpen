package io.sharpen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SharpenApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharpenApplication.class, args);
    }
}
