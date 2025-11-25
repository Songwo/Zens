package com.campus.trend.campus_pulse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.campus.trend.campus_pulse.mapper")
public class CampusPulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampusPulseApplication.class, args);
	}

}
