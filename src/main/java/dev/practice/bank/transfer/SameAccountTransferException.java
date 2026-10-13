package dev.practice.bank.transfer;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException() {
        super("A conta de origem e a conta de destino devem ser diferentes");
    }
}
