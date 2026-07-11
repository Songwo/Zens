package com.campus.trend.campus_pulse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.annotation.Validated;

@SpringBootApplication
@MapperScan({"com.campus.trend.campus_pulse.mapper", "com.campus.trend.campus_pulse.seo"})
@Validated
@EnableScheduling
public class CampusPulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampusPulseApplication.class, args);
	}

}
