package com.example.mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.mdm.system.AdminProperties;

@SpringBootApplication
@EnableConfigurationProperties(AdminProperties.class)
public class MdmApplication {

  public static void main(String[] args) {
    SpringApplication.run(MdmApplication.class, args);
  }
}
