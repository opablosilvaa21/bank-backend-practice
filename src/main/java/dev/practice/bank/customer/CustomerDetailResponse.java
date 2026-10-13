package dev.practice.bank.customer;

import dev.practice.bank.account.AccountResponse;

import java.util.List;

public record CustomerDetailResponse(CustomerResponse customer, List<AccountResponse> accounts) {}
