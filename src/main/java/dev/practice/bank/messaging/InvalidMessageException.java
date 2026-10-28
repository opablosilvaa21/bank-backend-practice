package dev.practice.bank.messaging;

/**
 * Mensagem que nunca vai poder ser processada (JSON inválido, campo obrigatório faltando, versão desconhecida).
 * Tentar de novo não adianta: o tratador de erros manda direto para o dead letter topic.
 */
public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
