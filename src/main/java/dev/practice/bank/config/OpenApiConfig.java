package dev.practice.bank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cabeçalho do Swagger UI (/swagger-ui.html) e a ordem dos grupos de endpoints. */
@Configuration
public class OpenApiConfig {

    /** Ordem em que os grupos (@Tag dos controllers) aparecem: do cadastro até as notificações. */
    private static final List<String> TAG_ORDER =
            List.of("Clientes", "Contas", "Movimentações", "Transferências", "Pix", "Notificações");

    @Bean
    OpenAPI devBankOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DevBank API")
                .version("1.0")
                .description("""
                        API de um banco digital fictício: clientes, contas, depósitos, saques, \
                        transferências, extrato e Pix.

                        Transferências e Pix aceitam o cabeçalho `Idempotency-Key`: repetir o pedido \
                        com a mesma chave devolve a resposta original sem movimentar dinheiro de novo.

                        Contas fixas para testar: **1** (Ana Costa), **2** (Bruno Lima) e **3** (Carla Souza).""")
                .contact(new Contact()
                        .name("Pablo Araújo Silva")
                        .url("https://github.com/opablosilvaa21/bank-backend-practice"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }

    @Bean
    OpenApiCustomizer tagOrder() {
        return openApi -> {
            if (openApi.getTags() == null) {
                return;
            }
            List<Tag> sorted = openApi.getTags().stream()
                    .sorted(Comparator.comparingInt(OpenApiConfig::position))
                    .toList();
            openApi.setTags(new ArrayList<>(sorted));
        };
    }

    private static int position(Tag tag) {
        int index = TAG_ORDER.indexOf(tag.getName());
        return index < 0 ? TAG_ORDER.size() : index;
    }
}
