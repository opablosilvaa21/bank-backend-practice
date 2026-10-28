package dev.practice.bank.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts/{accountId}/notifications")
@Tag(name = "Notificações", description = "Avisos de transferências e Pix enviados e recebidos")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as notificações da conta, da mais recente para a mais antiga")
    @ApiResponse(responseCode = "200", description = "Notificações encontradas")
    @ApiResponse(responseCode = "404", description = "Conta não encontrada")
    public Page<NotificationResponse> list(@PathVariable Long accountId,
                                           @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return service.list(accountId, pageable);
    }
}
