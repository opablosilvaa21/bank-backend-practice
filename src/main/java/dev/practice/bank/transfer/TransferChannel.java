package dev.practice.bank.transfer;

import dev.practice.bank.transaction.TransactionType;

/** Por onde a transferência foi feita. Define os tipos e os textos dos lançamentos no extrato. */
public enum TransferChannel {
    TRANSFERENCIA(TransactionType.TRANSFERENCIA_ENVIADA, TransactionType.TRANSFERENCIA_RECEBIDA,
            "Transferência enviada para ", "Transferência recebida de "),
    PIX(TransactionType.PIX_ENVIADO, TransactionType.PIX_RECEBIDO,
            "Pix enviado para ", "Pix recebido de ");

    private final TransactionType sentType;
    private final TransactionType receivedType;
    private final String sentPrefix;
    private final String receivedPrefix;

    TransferChannel(TransactionType sentType, TransactionType receivedType, String sentPrefix, String receivedPrefix) {
        this.sentType = sentType;
        this.receivedType = receivedType;
        this.sentPrefix = sentPrefix;
        this.receivedPrefix = receivedPrefix;
    }

    public TransactionType sentType() { return sentType; }
    public TransactionType receivedType() { return receivedType; }
    public String sentPrefix() { return sentPrefix; }
    public String receivedPrefix() { return receivedPrefix; }
}
