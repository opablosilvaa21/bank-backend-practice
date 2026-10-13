package dev.practice.bank.pix;

import dev.practice.bank.shared.ResourceNotFoundException;

public class PixKeyNotFoundException extends ResourceNotFoundException {
    public PixKeyNotFoundException() {
        // Não repete a chave na mensagem: ela pode ser um CPF ou e-mail.
        super("Chave Pix não encontrada");
    }
}
