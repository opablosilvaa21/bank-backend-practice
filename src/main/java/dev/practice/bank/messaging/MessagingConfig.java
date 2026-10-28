package dev.practice.bank.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Infraestrutura de mensageria, ativa só com bank.messaging.enabled=true.
 * Desligada, nenhum bean aqui existe e a aplicação nem tenta falar com o Kafka.
 * O @EnableScheduling liga o relay do outbox (OutboxRelay).
 */
@Configuration
@EnableScheduling
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

    /**
     * O que fazer quando o consumidor falha (o Spring Boot liga este bean aos @KafkaListener):
     * - erro transitório (banco fora do ar, por exemplo): tenta de novo, com intervalo fixo;
     * - mensagem inválida (InvalidMessageException): repetir não adianta, vai direto para a DLT;
     * - esgotadas as tentativas: vai para a DLT, com o erro nos cabeçalhos, e o consumidor segue para a próxima.
     * Sem a DLT, uma mensagem com defeito travaria a partição para sempre (o "poison pill").
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka,
                                         @Value("${bank.messaging.retry.interval-ms:1000}") long intervalMs,
                                         @Value("${bank.messaging.retry.attempts:2}") long attempts) {
        // Partição -1: o Kafka escolhe a partição da DLT (ela tem uma só).
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(Topics.TRANSFERS_DLT, -1));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervalMs, attempts));
        handler.addNotRetryableExceptions(InvalidMessageException.class);
        return handler;
    }
}
