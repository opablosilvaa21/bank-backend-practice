package dev.practice.bank.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Busca a conta com lock pessimista (SELECT ... FOR UPDATE).
     * Outra transação que tentar travar a mesma linha espera o commit,
     * o que evita "lost update" em movimentações concorrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /** O EntityGraph carrega o cliente junto, evitando uma consulta extra por conta (N+1). */
    @EntityGraph(attributePaths = "customer")
    List<Account> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "customer")
    Optional<Account> findWithCustomerById(Long id);

    @EntityGraph(attributePaths = "customer")
    List<Account> findByCustomerIdOrderByIdAsc(Long customerId);
}
