package com.softropic.sendam;

import com.softropic.sendam.config.TestConfig;

import org.springframework.boot.SpringApplication;

public class TestAppTemplateApplication {

	public static void main(String[] args) {
		SpringApplication.from(AppTemplateApplication::main).with(TestConfig.class).run(args);
	}

}
