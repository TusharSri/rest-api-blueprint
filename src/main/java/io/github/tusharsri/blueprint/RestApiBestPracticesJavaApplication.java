package io.github.tusharsri.blueprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RestApiBestPracticesJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestApiBestPracticesJavaApplication.class, args);
    }

}


