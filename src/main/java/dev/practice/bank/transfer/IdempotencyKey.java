package dev.practice.bank.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Regras do cabeçalho Idempotency-Key.
 * O cliente gera uma chave única por operação (um UUID, por exemplo) e a reenvia se precisar repetir o pedido.
 * O banco executa a operação uma vez só e devolve a mesma resposta nas repetições.
 */
public final class IdempotencyKey {

    public static final String HEADER = "Idempotency-Key";
    /** Presente (com valor "true") quando a resposta é a repetição de uma operação já feita. */
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_.:\\-]{1,64}$");

    private IdempotencyKey() {}

    /** Sem cabeçalho, devolve null: a operação segue sem proteção contra repetição. */
    public static String validate(String key) {
        if (key == null) {
            return null;
        }
        if (!VALID.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter de 1 a 64 caracteres: letras, números, '-', '_', '.' ou ':'");
        }
        return key;
    }

    /** Impressão digital do pedido: a mesma chave só vale para exatamente os mesmos dados. */
    static String fingerprint(TransferChannel channel, Long fromId, Long toId, BigDecimal amount, String description) {
        String canonical = String.join("|", channel.name(), fromId.toString(), toId.toString(),
                amount.toPlainString(), description == null ? "" : description);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
