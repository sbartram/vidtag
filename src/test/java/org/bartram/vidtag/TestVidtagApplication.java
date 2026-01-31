package org.bartram.vidtag;

import org.springframework.boot.SpringApplication;

public class TestVidtagApplication {

	public static void main(String[] args) {
		SpringApplication.from(VidtagApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
