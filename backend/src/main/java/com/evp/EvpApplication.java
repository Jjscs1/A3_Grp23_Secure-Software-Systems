package com.evp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. If the team repo already has its own @SpringBootApplication class in
 * package com.evp, delete this one and keep theirs.
 */
@SpringBootApplication
public class EvpApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvpApplication.class, args);
    }
}
