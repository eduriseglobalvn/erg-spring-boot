/**
 * @author : name
 * */
package com.erg.ergspringboot;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ErgSpringApplication {

    /** Must match spring.jpa hibernate.jdbc.time_zone: Hibernate shifts LocalTime columns (shift times) by the gap to the JVM zone. */
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ErgSpringApplication.class, args);
    }

}
