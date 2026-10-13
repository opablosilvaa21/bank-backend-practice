package dev.practice.bank.shared;

/** Base para "não encontrado". O handler converte em HTTP 404. */
public abstract class ResourceNotFoundException extends RuntimeException {
    protected ResourceNotFoundException(String message) {
        super(message);
    }
}
