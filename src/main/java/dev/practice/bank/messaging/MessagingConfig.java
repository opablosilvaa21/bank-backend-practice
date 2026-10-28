package dev.practice.bank.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Infraestrutura de mensageria, ativa só com bank.messaging.enabled=true.
 * Desligada, nenhum bean aqui existe e a aplicação nem tenta falar com o Kafka.
 */
@Configuration
@ConditionalOnProperty(name = "bank.messaging.enabled", havingValue = "true")
public class MessagingConfig {

    /** O KafkaAdmin do Spring cria os tópicos ao subir, se ainda não existirem. */
    @Bean
    NewTopic transfersTopic(@Value("${bank.messaging.partitions:3}") int partitions) {
        return TopicBuilder.name(Topics.TRANSFERS).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic transfersDeadLetterTopic() {
        return TopicBuilder.name(Topics.TRANSFERS_DLT).partitions(1).replicas(1).build();
    }
}
