package com.example.transfer.service;

import com.example.transfer.dto.BatchTransferRequestDto;
import com.example.transfer.dto.BatchTransferResponseDto;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final LedgerClient ledgerClient;
    private final ObjectMapper objectMapper;

    private final int ttlHours;

    public TransferService(TransferRepository transferRepository,
                           IdempotencyKeyRepository idempotencyKeyRepository,
                           LedgerClient ledgerClient,
                           ObjectMapper objectMapper,
                           @Value("${app.idempotency.ttl-hours:24}") int ttlHours) {
        this.transferRepository = transferRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.ledgerClient = ledgerClient;
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
        String reqHash = hashRequest(request);

        IdempotencyKey existing = idempotencyKeyRepository.findByKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(reqHash)) {
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
        marker.setRequestHash(reqHash);
        marker = idempotencyKeyRepository.saveAndFlush(marker);

        // Create transfer and call ledger in same transaction boundary for our own state;
        // the ledger call itself is external and must be idempotent on its side based on transferId.
        Transfer transfer = new Transfer();
        transfer.setFromAccountId(request.fromAccountId());
        transfer.setToAccountId(request.toAccountId());
        transfer.setAmount(request.amount());
        transfer = transferRepository.saveAndFlush(transfer);

        LedgerTransferResponse ledgerResp = ledgerClient.postTransfer(
                new LedgerTransferRequest(request.fromAccountId(), request.toAccountId(), request.amount(), transfer.getId())
        );

        if ("FAILURE".equalsIgnoreCase(ledgerResp.status())) {
            transfer.setStatus(Transfer.Status.FAILED);
            transfer.setMessage(ledgerResp.message());
        } else {
            transfer.setStatus(Transfer.Status.COMPLETED);
            transfer.setMessage(ledgerResp.message());
        }
        transferRepository.save(transfer);

        TransferResponseDto resp = toDto(transfer);
        persistResponse(marker, transfer.getId(), resp);
        return resp;
    }

    @Transactional
    public BatchTransferResponseDto processBatch(BatchTransferRequestDto batch) {
        try (ExecutorService executor = createExecutor()) {
            List<Callable<BatchTransferResponseDto.Result>> tasks = new ArrayList<>();
            for (BatchTransferRequestDto.Item item : batch.items()) {
                tasks.add(() -> {
                    TransferResponseDto resp = createTransfer(item.transfer(), item.idempotencyKey());
                    return new BatchTransferResponseDto.Result(item.idempotencyKey(), resp);
                });
            }

            List<Future<BatchTransferResponseDto.Result>> futures = executor.invokeAll(tasks);
            List<BatchTransferResponseDto.Result> results = new ArrayList<>();
            for (Future<BatchTransferResponseDto.Result> f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    TransferResponseDto failed = new TransferResponseDto(
                            null, "FAILED", null, null, null, Instant.now(),
                            cause.getMessage()
                    );
                    results.add(new BatchTransferResponseDto.Result(null, failed));
                }
            }
            return new BatchTransferResponseDto(results);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Batch interrupted", ie);
        }
    }

    private ExecutorService createExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
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

    private TransferResponseDto toDto(Transfer t) {
        return new TransferResponseDto(
                t.getId(),
                t.getStatus().name(),
                t.getFromAccountId(),
                t.getToAccountId(),
                t.getAmount(),
                t.getCreatedAt(),
                t.getMessage()
        );
    }

    private String hashRequest(TransferRequestDto request) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String s = request.fromAccountId() + "|" + request.toAccountId() + "|" + request.amount().toPlainString();
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(dig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
