package dev.practice.bank.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByFullNameContainingIgnoreCase(String name, Pageable pageable);
}
