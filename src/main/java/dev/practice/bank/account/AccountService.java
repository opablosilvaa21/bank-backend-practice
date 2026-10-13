package dev.practice.bank.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Converte para DTO dentro da transação: o cliente é LAZY e,
 * com open-in-view desligado, não pode ser lido depois que a transação fecha.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public List<AccountResponse> findAll() {
        return repository.findAllByOrderByIdAsc().stream().map(AccountResponse::from).toList();
    }

    public AccountResponse findById(Long id) {
        return AccountResponse.from(load(id));
    }

    public BalanceResponse balance(Long id) {
        return BalanceResponse.from(load(id));
    }

    private Account load(Long id) {
        return repository.findWithCustomerById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
