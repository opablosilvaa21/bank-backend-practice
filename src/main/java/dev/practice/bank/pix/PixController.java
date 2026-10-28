package dev.practice.bank.pix;

import dev.practice.bank.pix.PixDtos.CreatePixKeyRequest;
import dev.practice.bank.pix.PixDtos.PixKeyLookupResponse;
import dev.practice.bank.pix.PixDtos.PixKeyResponse;
import dev.practice.bank.pix.PixDtos.PixTransferRequest;
import dev.practice.bank.transfer.IdempotencyKey;
import dev.practice.bank.transfer.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Pix", description = "Chaves Pix e transferências por chave")
public class PixController {

    private final PixService service;

    public PixController(PixService service) {
        this.service = service;
    }

    @GetMapping("/api/pix/keys/{key}")
    @Operation(summary = "Consulta uma chave Pix antes de pagar",
            description = "Mostra o nome do recebedor e o CPF mascarado. Aceita CPF com ou sem pontuação.")
    @ApiResponse(responseCode = "200", description = "Chave encontrada")
    @ApiResponse(responseCode = "404", description = "Chave não encontrada")
    public PixKeyLookupResponse lookup(@PathVariable String key) {
        return service.lookup(key);
    }

    @PostMapping("/api/pix/transfers")
    @Operation(summary = "Envia um Pix para uma chave",
            description = "Envie uma Idempotency-Key para poder repetir o pedido sem pagar duas vezes.")
    @ApiResponse(responseCode = "201", description = "Pix realizado (ou repetido, com Idempotent-Replayed: true)")
    @ApiResponse(responseCode = "400", description = "Dados inválidos, Idempotency-Key inválida ou chave da própria conta")
    @ApiResponse(responseCode = "404", description = "Conta de origem ou chave não encontrada")
    @ApiResponse(responseCode = "422",
            description = "Saldo insuficiente, conta bloqueada/encerrada ou Idempotency-Key já usada com outros dados")
    public ResponseEntity<TransferResponse> transfer(
            @Parameter(description = "Chave única da operação, como um UUID. Opcional.")
            @RequestHeader(name = IdempotencyKey.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PixTransferRequest request) {
        return service.transfer(request, idempotencyKey).toResponseEntity();
    }

    @GetMapping("/api/accounts/{accountId}/pix-keys")
    @Operation(summary = "Lista as chaves Pix de uma conta")
    public List<PixKeyResponse> list(@PathVariable Long accountId) {
        return service.listByAccount(accountId);
    }

    @PostMapping("/api/accounts/{accountId}/pix-keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma chave Pix na conta",
            description = "CPF: usa o CPF do titular. EMAIL: envie o e-mail em value. ALEATORIA: o banco gera a chave.")
    @ApiResponse(responseCode = "201", description = "Chave cadastrada")
    @ApiResponse(responseCode = "400", description = "Valor inválido para o tipo de chave")
    @ApiResponse(responseCode = "409", description = "Chave já cadastrada")
    @ApiResponse(responseCode = "422", description = "Limite de chaves atingido ou conta inativa")
    public PixKeyResponse register(@PathVariable Long accountId, @Valid @RequestBody CreatePixKeyRequest request) {
        return service.register(accountId, request);
    }
}
