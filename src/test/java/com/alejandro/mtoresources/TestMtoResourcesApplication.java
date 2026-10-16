package com.alejandro.mtoresources;

import org.springframework.boot.SpringApplication;

public class TestMtoResourcesApplication {

    public static void main(String[] args) {
        SpringApplication.from(MtoResourcesApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
