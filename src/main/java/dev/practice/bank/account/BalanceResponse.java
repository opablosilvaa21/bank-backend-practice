package dev.practice.bank.account;

import dev.practice.bank.shared.Money;

import java.math.BigDecimal;

public record BalanceResponse(Long accountId, String ownerName, BigDecimal balance, String currency) {

    public static BalanceResponse from(Account account) {
        return new BalanceResponse(account.getId(), account.getOwnerName(), account.getBalance(), Money.CURRENCY);
    }
}
