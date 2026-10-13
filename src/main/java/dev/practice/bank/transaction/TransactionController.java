package dev.practice.bank.transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounts/{accountId}")
@Tag(name = "Movimentações", description = "Depósito, saque e extrato")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposita um valor na conta")
    @ApiResponse(responseCode = "201", description = "Depósito realizado")
    @ApiResponse(responseCode = "404", description = "Conta não encontrada")
    @ApiResponse(responseCode = "422", description = "Conta bloqueada ou encerrada")
    public TransactionResponse deposit(@PathVariable Long accountId, @Valid @RequestBody MovementRequest request) {
        return service.deposit(accountId, request);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Saca um valor da conta")
    @ApiResponse(responseCode = "201", description = "Saque realizado")
    @ApiResponse(responseCode = "404", description = "Conta não encontrada")
    @ApiResponse(responseCode = "422", description = "Saldo insuficiente ou conta bloqueada/encerrada")
    public TransactionResponse withdraw(@PathVariable Long accountId, @Valid @RequestBody MovementRequest request) {
        return service.withdraw(accountId, request);
    }

    @GetMapping("/statement")
    @Operation(summary = "Extrato da conta, do mais recente para o mais antigo",
            description = "Sem datas, mostra os últimos 90 dias. Datas no formato AAAA-MM-DD, inclusivas.")
    public Page<TransactionResponse> statement(
            @PathVariable Long accountId,
            @Parameter(description = "Data inicial (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Data final (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return service.statement(accountId, from, to, pageable);
    }
}
