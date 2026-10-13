package dev.practice.bank.pix;

import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PixApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired AccountRepository accounts;

    /** CPF do Bruno (cliente 2), formatado como o usuário digitaria. */
    private String brunoCpfFormatted() {
        String cpf = customers.findById(2L).orElseThrow().getCpf();
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    @Test
    void consultaChaveCpfMostraNomeECpfMascarado() throws Exception {
        mvc.perform(get("/api/pix/keys/{key}", brunoCpfFormatted()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CPF"))
                .andExpect(jsonPath("$.ownerName").value("Bruno Lima"))
                .andExpect(jsonPath("$.ownerCpf", matchesPattern("\\*\\*\\*\\.\\d{3}\\.\\d{3}-\\*\\*")))
                .andExpect(jsonPath("$.key", matchesPattern("\\*\\*\\*\\..*")));
    }

    @Test
    void enviaPixPorChaveCpf() throws Exception {
        pix("""
                {"fromAccountId": 1, "key": "%s", "amount": 120, "description": "Presente"}
                """.formatted(brunoCpfFormatted()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channel").value("PIX"))
                .andExpect(jsonPath("$.toAccountId").value(2))
                .andExpect(jsonPath("$.amount").value(120.00));

        assertThat(accounts.findById(1L).orElseThrow().getBalance()).isEqualByComparingTo("1380.00");
        assertThat(accounts.findById(2L).orElseThrow().getBalance()).isEqualByComparingTo("970.00");
        mvc.perform(get("/api/accounts/2/statement"))
                .andExpect(jsonPath("$.content[0].type").value("PIX_RECEBIDO"))
                .andExpect(jsonPath("$.content[0].description").value("Pix recebido de Ana Costa - Presente"));
    }

    @Test
    void pixParaChaveDaPropriaContaRetorna400() throws Exception {
        String anaCpf = customers.findById(1L).orElseThrow().getCpf();
        pix("""
                {"fromAccountId": 1, "key": "%s", "amount": 10}
                """.formatted(anaCpf))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chaveInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/pix/keys/{key}", "ninguem@gmail.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Chave Pix não encontrada"));
    }

    @Test
    void pixSemSaldoRetorna422() throws Exception {
        pix("""
                {"fromAccountId": 2, "key": "%s", "amount": 99999}
                """.formatted(customers.findById(1L).orElseThrow().getCpf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cadastraChaveEmailEUsaParaReceberPix() throws Exception {
        register(3L, """
                {"type": "EMAIL", "value": "Carla.Pix@Gmail.com"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("carla.pix@gmail.com"));

        pix("""
                {"fromAccountId": 1, "key": "CARLA.PIX@gmail.com", "amount": 10}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toAccountId").value(3));
    }

    @Test
    void chaveDuplicadaRetorna409() throws Exception {
        register(1L, """
                {"type": "EMAIL", "value": "duplicada@gmail.com"}
                """).andExpect(status().isCreated());
        register(2L, """
                {"type": "EMAIL", "value": "duplicada@gmail.com"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Esta chave Pix já está cadastrada"));
    }

    @Test
    void chaveCpfDeOutraPessoaRetorna400() throws Exception {
        register(1L, """
                {"type": "CPF", "value": "%s"}
                """.formatted(brunoCpfFormatted()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A chave CPF deve ser o CPF do titular da conta"));
    }

    @Test
    void chaveAleatoriaEhGeradaPeloBanco() throws Exception {
        register(1L, """
                {"type": "ALEATORIA"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void limiteDeCincoChavesPorConta() throws Exception {
        // A conta 1 já tem a chave CPF; cabem mais 4.
        for (int i = 0; i < 4; i++) {
            register(1L, """
                    {"type": "ALEATORIA"}
                    """).andExpect(status().isCreated());
        }
        register(1L, """
                {"type": "ALEATORIA"}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("A conta já atingiu o limite de 5 chaves Pix"));

        mvc.perform(get("/api/accounts/1/pix-keys"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void tipoDeChaveInvalidoRetorna400() throws Exception {
        register(1L, """
                {"type": "TELEFONE_FIXO"}
                """)
                .andExpect(status().isBadRequest());
    }

    private ResultActions pix(String json) throws Exception {
        return mvc.perform(post("/api/pix/transfers").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(Long accountId, String json) throws Exception {
        return mvc.perform(post("/api/accounts/{id}/pix-keys", accountId)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
