package dev.practice.bank.pix;

import dev.practice.bank.shared.ConflictException;

public class PixKeyAlreadyExistsException extends ConflictException {
    public PixKeyAlreadyExistsException() {
        super("Esta chave Pix já está cadastrada");
    }
}
