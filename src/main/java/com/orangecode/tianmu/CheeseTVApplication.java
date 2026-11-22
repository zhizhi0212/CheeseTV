package com.orangecode.tianmu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.orangecode.tianmu.mapper")
public class CheeseTVApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheeseTVApplication.class, args);
    }

}
