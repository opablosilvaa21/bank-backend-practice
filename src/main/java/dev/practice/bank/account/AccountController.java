package dev.practice.bank.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Contas", description = "Consulta de contas e saldos")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista todas as contas")
    public List<AccountResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma conta pelo ID")
    @ApiResponse(responseCode = "200", description = "Conta encontrada")
    @ApiResponse(responseCode = "404", description = "Conta não encontrada")
    public AccountResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Consulta o saldo de uma conta")
    @ApiResponse(responseCode = "200", description = "Saldo encontrado")
    @ApiResponse(responseCode = "404", description = "Conta não encontrada")
    public BalanceResponse balance(@PathVariable Long id) {
        return service.balance(id);
    }
}
