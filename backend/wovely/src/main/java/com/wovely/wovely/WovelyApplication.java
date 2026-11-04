package com.wovely.wovely;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class WovelyApplication {

	public static void main(String[] args) {
		SpringApplication.run(WovelyApplication.class, args);
	}
}
