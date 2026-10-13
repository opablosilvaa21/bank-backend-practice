package dev.practice.bank.transaction;

import dev.practice.bank.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository accounts;

    @Test
    void depositoAumentaSaldoEApareceNoExtrato() throws Exception {
        post("/api/accounts/2/deposits", """
                {"amount": 150.50, "description": "Depósito no caixa"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSITO"))
                .andExpect(jsonPath("$.direction").value("CREDITO"))
                .andExpect(jsonPath("$.balanceAfter").value(1000.50));

        mvc.perform(get("/api/accounts/2/statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Depósito no caixa"))
                .andExpect(jsonPath("$.content[1].description").value("Depósito inicial"));
    }

    @Test
    void saqueDiminuiSaldo() throws Exception {
        post("/api/accounts/1/withdrawals", """
                {"amount": 500}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SAQUE"))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.description").value("Saque"))
                .andExpect(jsonPath("$.balanceAfter").value(1000.00));
    }

    @Test
    void saqueMaiorQueOSaldoRetorna422() throws Exception {
        post("/api/accounts/2/withdrawals", """
                {"amount": 850.01}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Saldo insuficiente na conta: 2"));
    }

    @Test
    void contaBloqueadaNaoRecebeDeposito() throws Exception {
        accounts.findById(3L).orElseThrow().block();

        post("/api/accounts/3/deposits", """
                {"amount": 10}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("A conta 3 não pode movimentar: status BLOQUEADA"));
    }

    @Test
    void transferenciaApareceNoExtratoDasDuasContas() throws Exception {
        post("/api/transfers", """
                {"fromAccountId": 1, "toAccountId": 2, "amount": 75, "description": "Rateio do almoço"}
                """)
                .andExpect(status().isCreated());

        mvc.perform(get("/api/accounts/1/statement"))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFERENCIA_ENVIADA"))
                .andExpect(jsonPath("$.content[0].description").value("Transferência enviada para Bruno Lima - Rateio do almoço"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(1425.00));
        mvc.perform(get("/api/accounts/2/statement"))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFERENCIA_RECEBIDA"))
                .andExpect(jsonPath("$.content[0].description").value("Transferência recebida de Ana Costa - Rateio do almoço"));
    }

    @Test
    void extratoFiltraPorPeriodo() throws Exception {
        mvc.perform(get("/api/accounts/1/statement").param("from", "2000-01-01").param("to", "2000-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void periodoInvertidoRetorna400() throws Exception {
        mvc.perform(get("/api/accounts/1/statement").param("from", "2025-02-01").param("to", "2025-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataEmFormatoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/accounts/1/statement").param("from", "01/02/2025"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void extratoDeContaInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/accounts/9999/statement"))
                .andExpect(status().isNotFound());
    }

    @Test
    void depositoComValorInvalidoRetorna400() throws Exception {
        post("/api/accounts/1/deposits", """
                {"amount": -10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").value("deve ser maior que zero"));
    }

    private ResultActions post(String url, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
