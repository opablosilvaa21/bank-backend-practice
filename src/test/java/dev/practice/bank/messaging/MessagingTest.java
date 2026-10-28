package dev.practice.bank.messaging;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sobe a aplicação com a mensageria ligada e um Kafka embutido (dentro da JVM dos testes).
 * Todas as classes com esta anotação têm a mesma configuração e por isso dividem o mesmo contexto e o mesmo broker.
 * Roda num schema próprio ("messaging"), porque o relay e o consumidor fazem commits de verdade.
 * Uma partição só: a ordem das mensagens no tópico fica previsível nos testes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "bank.messaging.enabled=true",
        "bank.messaging.partitions=1",
        "bank.outbox.relay.interval-ms=100",
        "spring.flyway.schemas=messaging",
        "spring.datasource.hikari.schema=messaging"
})
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
public @interface MessagingTest {
}
