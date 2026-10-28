package dev.practice.bank.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.awaitility.Awaitility.await;

/** Lê um tópico do Kafka embutido, desde o início, até achar a mensagem procurada. */
public final class KafkaTestReader {

    private KafkaTestReader() {}

    public static ConsumerRecord<String, String> awaitRecord(EmbeddedKafkaBroker broker, String topic,
                                                             Predicate<ConsumerRecord<String, String>> match) {
        // Grupo novo a cada leitura: começa do início e não interfere no consumidor da aplicação.
        var props = KafkaTestUtils.consumerProps("test-reader-" + UUID.randomUUID(), "false", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic));
            return await().atMost(Duration.ofSeconds(15)).until(() ->
                    StreamSupport.stream(consumer.poll(Duration.ofMillis(200)).spliterator(), false)
                            .filter(match)
                            .findFirst(), Optional::isPresent).orElseThrow();
        }
    }

    public static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
