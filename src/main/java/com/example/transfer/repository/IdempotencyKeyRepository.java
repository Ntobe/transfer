package com.example.transfer.repository;

import com.example.transfer.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByKey(String key);
    void deleteByCreatedAtBefore(java.time.Instant cutoff);
}
