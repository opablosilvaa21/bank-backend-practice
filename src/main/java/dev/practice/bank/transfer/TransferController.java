package dev.practice.bank.transfer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfere um valor entre duas contas",
            description = "Debita a conta de origem e credita a de destino na mesma transação.")
    @ApiResponse(responseCode = "201", description = "Transferência realizada")
    @ApiResponse(responseCode = "400", description = "Dados inválidos ou origem igual ao destino")
    @ApiResponse(responseCode = "404", description = "Conta de origem ou destino não encontrada")
    @ApiResponse(responseCode = "422", description = "Saldo insuficiente")
    public TransferResponse create(@Valid @RequestBody TransferRequest request) {
        return service.transfer(request);
    }
}
