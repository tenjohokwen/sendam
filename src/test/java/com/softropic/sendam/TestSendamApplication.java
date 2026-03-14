package com.softropic.sendam;

import com.softropic.sendam.config.TestConfig;

import org.springframework.boot.SpringApplication;

public class TestSendamApplication {

	public static void main(String[] args) {
		SpringApplication.from(SendamApplication::main).with(TestConfig.class).run(args);
	}

}
