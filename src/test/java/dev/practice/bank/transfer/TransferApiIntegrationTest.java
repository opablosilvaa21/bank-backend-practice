package dev.practice.bank.transfer;

import dev.practice.bank.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sobe a aplicação inteira com o H2 em memória e as contas de demonstração:
 * 1 = R$ 1.500,00, 2 = R$ 850,00, 3 = R$ 3.200,00.
 * O @Transactional desfaz (rollback) as alterações ao fim de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository repository;

    @Test
    void transferenciaValidaRetorna201EAtualizaSaldos() throws Exception {
        transfer("""
                {"fromAccountId": 1, "toAccountId": 2, "amount": 100.25}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccountId").value(1))
                .andExpect(jsonPath("$.toAccountId").value(2))
                .andExpect(jsonPath("$.amount").value(100.25))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.executedAt").exists());

        mvc.perform(get("/api/accounts/1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1399.75));
        mvc.perform(get("/api/accounts/2/balance"))
                .andExpect(jsonPath("$.balance").value(950.25));
    }

    @Test
    void saldoInsuficienteRetorna422ENaoAlteraSaldos() throws Exception {
        transfer("""
                {"fromAccountId": 2, "toAccountId": 1, "amount": 850.01}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Saldo insuficiente na conta: 2"));

        assertThat(repository.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1500.00");
        assertThat(repository.findById(2L).orElseThrow().getBalance()).isEqualByComparingTo("850.00");
    }

    @Test
    void contaInexistenteRetorna404() throws Exception {
        transfer("""
                {"fromAccountId": 1, "toAccountId": 999, "amount": 10}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conta não encontrada: 999"));

        assertThat(repository.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void mesmaContaRetorna400() throws Exception {
        transfer("""
                {"fromAccountId": 1, "toAccountId": 1, "amount": 10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("diferentes")));
    }

    @Test
    void valorZeroRetorna400ComErroNoCampo() throws Exception {
        transfer("""
                {"fromAccountId": 1, "toAccountId": 2, "amount": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").value("deve ser maior que zero"));
    }

    @Test
    void valorNegativoRetorna400() throws Exception {
        transfer("""
                {"fromAccountId": 1, "toAccountId": 2, "amount": -50}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
    }

    @Test
    void valorComMaisDeDuasCasasDecimaisRetorna400() throws Exception {
        transfer("""
                {"fromAccountId": 1, "toAccountId": 2, "amount": 10.001}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").value("deve ter no máximo 2 casas decimais"));
    }

    @Test
    void idDeContaNegativoRetorna400() throws Exception {
        transfer("""
                {"fromAccountId": -1, "toAccountId": 2, "amount": 10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.fromAccountId").value("deve ser positivo"));
    }

    @Test
    void camposObrigatoriosAusentesRetornam400() throws Exception {
        transfer("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.fromAccountId").value("é obrigatório"))
                .andExpect(jsonPath("$.fields.toAccountId").value("é obrigatório"))
                .andExpect(jsonPath("$.fields.amount").value("é obrigatório"));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        transfer("{ isto não é json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Corpo da requisição ausente ou malformado"));
    }

    @Test
    void metodoNaoSuportadoRetorna405() throws Exception {
        mvc.perform(get("/api/transfers"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").exists());
    }

    private org.springframework.test.web.servlet.ResultActions transfer(String json) throws Exception {
        return mvc.perform(post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
