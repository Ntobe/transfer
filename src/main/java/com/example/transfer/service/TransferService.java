package com.example.transfer.service;

import com.example.transfer.dto.LedgerTransferRequest;
import com.example.transfer.dto.LedgerTransferResponse;
import com.example.transfer.dto.TransferRequestDto;
import com.example.transfer.dto.TransferResponseDto;
import com.example.transfer.entity.IdempotencyKey;
import com.example.transfer.entity.Transfer;
import com.example.transfer.exception.ConflictException;
import com.example.transfer.exception.NotFoundException;
import com.example.transfer.repository.IdempotencyKeyRepository;
import com.example.transfer.repository.TransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private final TransferRepository transferRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ResilientLedgerClient resilientLedgerClient;
    private final ObjectMapper objectMapper;

    private final int ttlHours;

    public TransferService(TransferRepository transferRepository,
                           IdempotencyKeyRepository idempotencyKeyRepository,
                           ResilientLedgerClient resilientLedgerClient,
                           ObjectMapper objectMapper,
                           @Value("${app.idempotency.ttl-hours:24}") int ttlHours) {
        this.transferRepository = transferRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.resilientLedgerClient = resilientLedgerClient;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    public TransferResponseDto getTransfer(String id) {
        return transferRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("transfer not found: " + id));
    }

    @Transactional
    public TransferResponseDto createTransfer(TransferRequestDto request, String idempotencyKey) {
        String requestHash = hashRequest(request);

        IdempotencyKey existing = idempotencyKeyRepository.findByKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ConflictException("Idempotency-Key reused with different request body");
            }
            try {
                return objectMapper.readValue(existing.getResponseJson(), TransferResponseDto.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // create placeholder idempotency row to prevent concurrent duplicates
        IdempotencyKey marker = new IdempotencyKey();
        marker.setKey(idempotencyKey);
        marker.setRequestHash(requestHash);
        marker = idempotencyKeyRepository.saveAndFlush(marker);

        // Create transfer and call ledger in same transaction boundary for our own state;
        // the ledger call itself is external and must be idempotent on its side based on transferId.
        Transfer transfer = new Transfer();
        transfer.setFromAccountId(request.fromAccountId());
        transfer.setToAccountId(request.toAccountId());
        transfer.setAmount(request.amount());
        transfer = transferRepository.saveAndFlush(transfer);

        LedgerTransferResponse ledgerResp = resilientLedgerClient.postTransfer(
                new LedgerTransferRequest(request.fromAccountId(), request.toAccountId(), request.amount(), transfer.getId())
        ).join();

        if ("FAILURE".equalsIgnoreCase(ledgerResp.status())) {
            transfer.setStatus(Transfer.Status.FAILED);
            transfer.setMessage(ledgerResp.message());
        } else {
            transfer.setStatus(Transfer.Status.COMPLETED);
            transfer.setMessage(ledgerResp.message());
        }
        transferRepository.save(transfer);

        log.info("{}", Map.of(
                "event", "transfer_result",
                "transferId", transfer.getId(),
                "status", transfer.getStatus(),
                "fromAccountId", transfer.getFromAccountId(),
                "toAccountId", transfer.getToAccountId(),
                "amount", transfer.getAmount(),
                "message", transfer.getMessage()
        ));

        TransferResponseDto resp = toDto(transfer);
        persistResponse(marker, transfer.getId(), resp);
        cleanupExpiredKeys();
        return resp;
    }

    private void persistResponse(IdempotencyKey marker, String transferId, TransferResponseDto resp) {
        try {
            marker.setTransferId(transferId);
            marker.setResponseJson(objectMapper.writeValueAsString(resp));
            idempotencyKeyRepository.save(marker);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void cleanupExpiredKeys() {
        Instant cutoff = Instant.now().minusSeconds(ttlHours * 3600L);
        idempotencyKeyRepository.deleteByCreatedAtBefore(cutoff);
    }

    private TransferResponseDto toDto(Transfer t) {
        return new TransferResponseDto(
                t.getId(),
                t.getStatus().name()
        );
    }

    private String hashRequest(TransferRequestDto request) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            String string = request.fromAccountId() + "|" + request.toAccountId() + "|" + request.amount().toPlainString();
            byte[] dig = messageDigest.digest(string.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(dig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
