package dev.practice.bank;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Todo contexto de teste começa com o banco zerado: o Flyway apaga o schema (clean) e roda as migrations de novo.
 * Depois disso o DemoData recria a base fictícia, com as contas 1, 2 e 3 nos saldos conhecidos.
 * Está em src/test, então a aplicação de verdade nunca faz clean.
 */
@Configuration
public class TestDatabaseConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
