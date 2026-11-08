package br.edu.reston;

import org.springframework.boot.SpringApplication;

public class TestRestOnApplication {

	public static void main(String[] args) {
		SpringApplication.from(RestOnApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
