package com.softropic.sendam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class AppTemplateApplication {

	public static void main(String[] args) {
		SpringApplication.run(AppTemplateApplication.class, args);
	}

}
