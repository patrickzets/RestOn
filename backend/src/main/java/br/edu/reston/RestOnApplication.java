package br.edu.reston;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RestOnApplication {

	public static void main(String[] args) {
		SpringApplication.run(RestOnApplication.class, args);
	}

}
