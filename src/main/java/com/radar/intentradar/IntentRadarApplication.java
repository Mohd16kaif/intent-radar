package com.radar.intentradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntentRadarApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntentRadarApplication.class, args);
	}

}
