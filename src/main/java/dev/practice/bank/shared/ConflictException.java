package dev.practice.bank.shared;

/** Base para conflito com o estado atual (ex.: chave Pix já cadastrada). O handler converte em HTTP 409. */
public abstract class ConflictException extends RuntimeException {
    protected ConflictException(String message) {
        super(message);
    }
}
