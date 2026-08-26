package com.omnihealth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.TimeZone;

@SpringBootTest
@Disabled("Integration test requiring live PostgreSQL container on localhost:5432")
class OmnihealthBackendApplicationTests {
    @Test
    void contextLoads() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("user.timezone", "UTC");
    }

}
