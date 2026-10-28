package dev.practice.bank.transfer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transferências", description = "Transferência de valores entre contas")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Transfere um valor entre duas contas",
            description = "Debita a conta de origem e credita a de destino na mesma transação. "
                    + "Envie uma Idempotency-Key para poder repetir o pedido sem transferir duas vezes.")
    @ApiResponse(responseCode = "201", description = "Transferência realizada (ou repetida, com Idempotent-Replayed: true)")
    @ApiResponse(responseCode = "400", description = "Dados inválidos, Idempotency-Key inválida ou origem igual ao destino")
    @ApiResponse(responseCode = "404", description = "Conta de origem ou destino não encontrada")
    @ApiResponse(responseCode = "422", description = "Saldo insuficiente ou Idempotency-Key já usada com outros dados")
    public ResponseEntity<TransferResponse> create(
            @Parameter(description = "Chave única da operação, como um UUID. Opcional.")
            @RequestHeader(name = IdempotencyKey.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return service.transfer(request, idempotencyKey).toResponseEntity();
    }
}
