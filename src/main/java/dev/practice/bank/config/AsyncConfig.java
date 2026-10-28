package dev.practice.bank.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Liga o @Async. Usa o pool de threads que o Spring Boot já configura (spring.task.execution.*). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
