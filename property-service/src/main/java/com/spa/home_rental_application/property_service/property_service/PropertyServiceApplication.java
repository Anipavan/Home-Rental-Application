package com.spa.home_rental_application.property_service.property_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({
		"com.spa.home_rental_application.property_service",
		"com.spa.home_rental_application.auth_service",
		"com.spa.home_rental_application"

})
public class PropertyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PropertyServiceApplication.class, args);
	}

}
