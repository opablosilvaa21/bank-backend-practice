package dev.practice.bank.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Com a mensageria ligada, a aplicação cria os tópicos e consegue publicar e ler no Kafka. */
@MessagingTest
class KafkaInfrastructureIntegrationTest {

    @Autowired KafkaAdmin admin;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void criaOsTopicosAoSubir() {
        assertThat(admin.describeTopics(Topics.TRANSFERS, Topics.TRANSFERS_DLT)).containsOnlyKeys(
                Topics.TRANSFERS, Topics.TRANSFERS_DLT);
        // Nos testes, uma partição (bank.messaging.partitions=1 em @MessagingTest); na aplicação, 3.
        assertThat(admin.describeTopics(Topics.TRANSFERS).get(Topics.TRANSFERS).partitions()).hasSize(1);
    }

    @Test
    void publicaELeUmaMensagem() throws Exception {
        String topic = "smoke-test";
        kafka.send(topic, "chave", "olá, kafka").get(10, TimeUnit.SECONDS);

        var props = KafkaTestUtils.consumerProps("smoke", "false", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo("chave");
            assertThat(record.value()).isEqualTo("olá, kafka");
        }
    }
}
