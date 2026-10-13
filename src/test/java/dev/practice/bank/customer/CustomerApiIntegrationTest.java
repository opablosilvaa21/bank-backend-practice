package dev.practice.bank.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listaClientesPaginados() throws Exception {
        mvc.perform(get("/api/customers").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[0].fullName").value("Ana Costa"))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(100))
                .andExpect(jsonPath("$.page.totalPages").value(10));
    }

    @Test
    void cpfSempreMascarado() throws Exception {
        mvc.perform(get("/api/customers"))
                .andExpect(jsonPath("$.content[*].cpf", everyItem(matchesPattern("\\*\\*\\*\\.\\d{3}\\.\\d{3}-\\*\\*"))));
    }

    @Test
    void buscaPorNomeSemDiferenciarMaiusculas() throws Exception {
        mvc.perform(get("/api/customers").param("name", "bruno lima"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("Bruno Lima"));
    }

    @Test
    void detalheTrazAsContasDoCliente() throws Exception {
        mvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.fullName").value("Ana Costa"))
                .andExpect(jsonPath("$.customer.email").value("ana.costa@gmail.com"))
                .andExpect(jsonPath("$.accounts", hasSize(1)))
                .andExpect(jsonPath("$.accounts[0].id").value(1))
                .andExpect(jsonPath("$.accounts[0].balance").value(1500.00));
    }

    @Test
    void clienteInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/customers/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cliente não encontrado: 9999"));
    }

    @Test
    void tamanhoDePaginaEhLimitado() throws Exception {
        mvc.perform(get("/api/customers").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }
}
