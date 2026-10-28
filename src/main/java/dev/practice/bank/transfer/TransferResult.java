package dev.practice.bank.transfer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Resposta da transferência e se ela é a repetição de uma operação já feita (mesma Idempotency-Key). */
public record TransferResult(TransferResponse response, boolean replayed) {

    /** Repetição também responde 201, com o mesmo corpo da primeira vez: o cliente não precisa tratar diferente. */
    public ResponseEntity<TransferResponse> toResponseEntity() {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (replayed) {
            builder.header(IdempotencyKey.REPLAYED_HEADER, "true");
        }
        return builder.body(response);
    }
}
