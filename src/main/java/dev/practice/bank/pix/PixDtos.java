package dev.practice.bank.pix;

import dev.practice.bank.account.AccountType;
import dev.practice.bank.shared.Cpf;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Contratos de entrada e saída da API Pix. */
public final class PixDtos {

    private PixDtos() {}

    public record CreatePixKeyRequest(
            @NotNull(message = "é obrigatório") PixKeyType type,
            @Size(max = 77, message = "deve ter no máximo 77 caracteres")
            String value
    ) {}

    public record PixTransferRequest(
            @NotNull(message = "é obrigatório") @Positive(message = "deve ser positivo") Long fromAccountId,
            @NotBlank(message = "é obrigatório") @Size(max = 77, message = "deve ter no máximo 77 caracteres") String key,
            @NotNull(message = "é obrigatório")
            @DecimalMin(value = "0.00", inclusive = false, message = "deve ser maior que zero")
            @Digits(integer = 17, fraction = 2, message = "deve ter no máximo 2 casas decimais")
            BigDecimal amount,
            @Size(max = 140, message = "deve ter no máximo 140 caracteres") String description
    ) {}

    public record PixKeyResponse(Long id, PixKeyType type, String key, Long accountId, Instant createdAt) {
        static PixKeyResponse from(PixKey k) {
            return new PixKeyResponse(k.getId(), k.getKeyType(), display(k), k.getAccount().getId(), k.getCreatedAt());
        }
    }

    /** O que o pagador vê antes de confirmar o Pix: nome do recebedor e CPF mascarado. */
    public record PixKeyLookupResponse(
            PixKeyType type,
            String key,
            String ownerName,
            String ownerCpf,
            String agency,
            AccountType accountType
    ) {
        static PixKeyLookupResponse from(PixKey k) {
            var account = k.getAccount();
            return new PixKeyLookupResponse(k.getKeyType(), display(k), account.getOwnerName(),
                    Cpf.mask(account.getCustomer().getCpf()), account.getAgency(), account.getType());
        }
    }

    private static String display(PixKey k) {
        return k.getKeyType() == PixKeyType.CPF ? Cpf.mask(k.getKeyValue()) : k.getKeyValue();
    }
}
