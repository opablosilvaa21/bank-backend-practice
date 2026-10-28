package dev.practice.bank.messaging;

/** Nomes dos tópicos Kafka do banco. */
public final class Topics {

    /** Um evento por transferência concluída (TED interna ou Pix). Chave: conta de origem. */
    public static final String TRANSFERS = "bank.transfers";

    /** Dead letter topic: mensagens que o consumidor não conseguiu processar, guardadas para análise. */
    public static final String TRANSFERS_DLT = "bank.transfers.dlt";

    private Topics() {}
}
