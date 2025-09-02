package com.example.transfer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idem_key", columnNames = "idem_key")
})
@Getter
@Setter
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key", nullable = false, length = 200)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 200)
    private String requestHash;

    @Column(name = "transfer_id", length = 40)
    private String transferId;

    @Lob
    @Column(name = "response_json")
    private String responseJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
