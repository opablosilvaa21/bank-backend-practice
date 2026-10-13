package dev.practice.bank.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listaAsContasDeDemonstracao() throws Exception {
        mvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(3)))
                .andExpect(jsonPath("$[0].ownerName").value("Ana Costa"));
    }

    @Test
    void buscaContaPorId() throws Exception {
        mvc.perform(get("/api/accounts/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.ownerName").value("Bruno Lima"))
                .andExpect(jsonPath("$.agency").value("0001"))
                .andExpect(jsonPath("$.accountNumber", matchesPattern("\\d{5}-\\d")))
                .andExpect(jsonPath("$.type").value("CORRENTE"))
                .andExpect(jsonPath("$.status").value("ATIVA"));
    }

    @Test
    void consultaSaldo() throws Exception {
        mvc.perform(get("/api/accounts/3/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(3))
                .andExpect(jsonPath("$.balance").value(3200.00))
                .andExpect(jsonPath("$.currency").value("BRL"));
    }

    @Test
    void documentacaoOpenApiEstaDisponivel() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transfers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{id}/balance'].get").exists());
    }

    @Test
    void raizMostraOPainelDeClientes() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void rotaInexistenteRetorna404EmPortugues() throws Exception {
        mvc.perform(get("/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso não encontrado: /nao-existe"));
    }

    @Test
    void contaInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/accounts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conta não encontrada: 999"));
    }

    @Test
    void idNaoNumericoRetorna400() throws Exception {
        mvc.perform(get("/api/accounts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Parâmetro inválido: id"));
    }
}
