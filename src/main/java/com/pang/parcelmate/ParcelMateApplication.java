package com.pang.parcelmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ParcelMate 启动类。
 *
 * <p>Spring Boot 的约定：从 main 方法进入，启动内嵌 Tomcat，
 * 扫描本包及子包里的 @RestController / @Service / @Repository 自动装配。
 */
@SpringBootApplication
public class ParcelMateApplication {

    public static void main(String[] args) throws IOException {
        // SQLite 的数据库文件放在 ./data 下；JDBC 不会自动建目录，这里先建好
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(ParcelMateApplication.class, args);
    }
}
