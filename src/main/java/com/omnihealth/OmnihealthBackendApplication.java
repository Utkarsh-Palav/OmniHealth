package com.omnihealth;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OmnihealthBackendApplication {

    public static void main(String[] args) {
		// Load .env file properties if it exists
		try {
			java.io.File envFile = new java.io.File(".env");
			if (envFile.exists()) {
				java.nio.file.Files.lines(envFile.toPath())
						.map(String::trim)
						.filter(line -> !line.isEmpty() && !line.startsWith("#"))
						.forEach(line -> {
							String[] parts = line.split("=", 2);
							if (parts.length == 2) {
								String key = parts[0].trim();
								String value = parts[1].trim();
								if (System.getenv(key) == null && System.getProperty(key) == null) {
									System.setProperty(key, value);
								}
							}
						});
			}
		} catch (Exception e) {
			System.err.println("Warning: Failed to load .env file: " + e.getMessage());
		}

		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		System.setProperty("user.timezone", "UTC");

        SpringApplication.run(OmnihealthBackendApplication.class, args);
    }

}
