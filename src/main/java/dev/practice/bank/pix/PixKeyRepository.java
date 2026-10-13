package dev.practice.bank.pix;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PixKeyRepository extends JpaRepository<PixKey, Long> {

    @EntityGraph(attributePaths = {"account", "account.customer"})
    Optional<PixKey> findByKeyValue(String keyValue);

    List<PixKey> findByAccountIdOrderByIdAsc(Long accountId);

    long countByAccountId(Long accountId);

    boolean existsByKeyValue(String keyValue);
}
