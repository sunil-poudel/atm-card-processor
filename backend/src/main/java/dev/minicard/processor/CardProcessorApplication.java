package dev.minicard.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CardProcessorApplication {

  public static void main(String[] args) {
    SpringApplication.run(CardProcessorApplication.class, args);
  }
}
