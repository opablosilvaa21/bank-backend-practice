package dev.practice.bank.transaction;

/** Tipo de lançamento no extrato. Cada tipo sabe se é crédito (entrada) ou débito (saída). */
public enum TransactionType {
    DEPOSITO(true),
    SAQUE(false),
    PAGAMENTO(false),
    TRANSFERENCIA_ENVIADA(false),
    TRANSFERENCIA_RECEBIDA(true),
    PIX_ENVIADO(false),
    PIX_RECEBIDO(true),
    RENDIMENTO(true);

    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    public boolean isCredit() {
        return credit;
    }
}
