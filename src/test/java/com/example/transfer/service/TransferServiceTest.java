package com.example.transfer.service;

import com.example.transfer.dto.LedgerTransferResponse;
import com.example.transfer.dto.TransferRequestDto;
import com.example.transfer.dto.TransferResponseDto;
import com.example.transfer.entity.IdempotencyKey;
import com.example.transfer.entity.Transfer;
import com.example.transfer.repository.IdempotencyKeyRepository;
import com.example.transfer.repository.TransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private LedgerClient ledgerClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransferService transferService;

    @Test
    void createTransfer_successful() {
        TransferRequestDto request = new TransferRequestDto(1L, 2L, BigDecimal.valueOf(100));

        IdempotencyKey marker = new IdempotencyKey();
        marker.setKey("idem-1");
        marker.setRequestHash("hash123");

        Transfer saved = new Transfer();
        saved.setId("t1");
        saved.setFromAccountId(1L);
        saved.setToAccountId(2L);
        saved.setAmount(BigDecimal.valueOf(100));

        when(idempotencyKeyRepository.findByKey("idem-1")).thenReturn(Optional.empty());
        when(idempotencyKeyRepository.saveAndFlush(any())).thenReturn(marker);
        when(transferRepository.saveAndFlush(any())).thenReturn(saved);
        when(ledgerClient.postTransfer(any()))
                .thenReturn(new LedgerTransferResponse("COMPLETED", "ok"));

        TransferResponseDto resp = transferService.createTransfer(request, "idem-1");

        assertEquals("COMPLETED", resp.status());
        verify(transferRepository).save(saved);
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    @Test
    void createTransfer_idempotentRetry_returnsStoredResponse() throws Exception {
        TransferResponseDto previousResp = new TransferResponseDto(
                "t1", "COMPLETED", 1L, 2L,
                BigDecimal.valueOf(100), Instant.now(), "ok"
        );
        objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(previousResp);

        IdempotencyKey existing = new IdempotencyKey();
        existing.setKey("idem-1");
        String requestHash = "Ud1ZsNIzKZKqlE6hGjQTHz5brWFlPsbshNc9WyoOXb8";
        existing.setRequestHash(requestHash);
        existing.setResponseJson(json);

        when(idempotencyKeyRepository.findByKey("idem-1"))
                .thenReturn(Optional.of(existing));

        TransferResponseDto resp = transferService.createTransfer(
                new TransferRequestDto(1L, 2L, BigDecimal.valueOf(100)),
                "idem-1"
        );

        assertEquals("COMPLETED", resp.status());
        verify(ledgerClient, never()).postTransfer(any());
    }
}