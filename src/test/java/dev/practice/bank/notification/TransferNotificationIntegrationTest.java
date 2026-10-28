package dev.practice.bank.notification;

import dev.practice.bank.transfer.TransferRequest;
import dev.practice.bank.transfer.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O listener de notificações só roda depois do commit. Por isso este teste NÃO é @Transactional:
 * as transferências fazem commit de verdade, num schema próprio ("notifications") para não afetar os outros testes.
 * O listener é @Async, então os testes esperam a notificação aparecer (Awaitility) em vez de conferir na hora.
 */
@SpringBootTest(properties = {
        "spring.flyway.schemas=notifications",
        "spring.datasource.hikari.schema=notifications"
})
@AutoConfigureMockMvc
class TransferNotificationIntegrationTest {

    @Autowired TransferService transfers;
    @Autowired NotificationRepository notifications;
    @Autowired TransactionTemplate tx;
    @Autowired MockMvc mvc;

    @Test
    void depoisDoCommitAvisaQuemEnviouEQuemRecebeu() throws Exception {
        UUID id = transfers.transfer(new TransferRequest(3L, 1L, new BigDecimal("1250.50"))).transferId();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notifications.findByTransferIdOrderByIdAsc(id))
                        .extracting(Notification::getAccountId, Notification::getMessage)
                        .containsExactly(
                                tuple(3L, "Você enviou R$ 1.250,50 para Ana Costa por transferência"),
                                tuple(1L, "Você recebeu R$ 1.250,50 de Carla Souza por transferência")));

        mvc.perform(get("/api/accounts/1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transferId").value(id.toString()))
                .andExpect(jsonPath("$.content[0].message").value("Você recebeu R$ 1.250,50 de Carla Souza por transferência"));
    }

    @Test
    void transferenciaDesfeitaNaoGeraNotificacao() {
        long before = notifications.count();

        // A transferência roda, mas a transação externa termina em rollback: o dinheiro não se move.
        UUID id = tx.execute(status -> {
            UUID transferId = transfers.transfer(new TransferRequest(1L, 2L, BigDecimal.TEN)).transferId();
            status.setRollbackOnly();
            return transferId;
        });

        // Espera um pouco para ter certeza de que nada chega depois.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .until(() -> notifications.findByTransferIdOrderByIdAsc(id).isEmpty());
        assertThat(notifications.count()).isEqualTo(before);
    }

    @Test
    void repeticaoComAMesmaChaveNaoDuplicaANotificacao() {
        var request = new TransferRequest(2L, 3L, new BigDecimal("5.00"));
        UUID id = transfers.transfer(request, "aviso-unico").response().transferId();
        transfers.transfer(request, "aviso-unico");

        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> notifications.findByTransferIdOrderByIdAsc(id).size() == 2);
    }

    @Test
    void contaInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/accounts/999999/notifications"))
                .andExpect(status().isNotFound());
    }
}
