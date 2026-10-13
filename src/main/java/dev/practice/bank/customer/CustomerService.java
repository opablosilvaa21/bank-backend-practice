package dev.practice.bank.customer;

import dev.practice.bank.account.AccountRepository;
import dev.practice.bank.account.AccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final AccountRepository accounts;

    public CustomerService(CustomerRepository customers, AccountRepository accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    public Page<CustomerResponse> search(String name, Pageable pageable) {
        Page<Customer> page = StringUtils.hasText(name)
                ? customers.findByFullNameContainingIgnoreCase(name.trim(), pageable)
                : customers.findAll(pageable);
        return page.map(CustomerResponse::from);
    }

    public CustomerDetailResponse findById(Long id) {
        Customer customer = customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
        var customerAccounts = accounts.findByCustomerIdOrderByIdAsc(id).stream()
                .map(AccountResponse::from)
                .toList();
        return new CustomerDetailResponse(CustomerResponse.from(customer), customerAccounts);
    }
}
