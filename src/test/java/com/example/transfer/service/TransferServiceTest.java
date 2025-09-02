package com.example.transfer.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private ResilientLedgerClient resilientLedgerClient;

    @Mock
    private ObjectMapper objectMapper;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                transferRepository,
                idempotencyKeyRepository,
                resilientLedgerClient,
                objectMapper,
                24 // ttlHours
        );
    }

    @Test
    void getTransfer_shouldReturnDto_whenTransferExists() {
        Transfer transfer = new Transfer();
        transfer.setId("tx-1");
        transfer.setStatus(Transfer.Status.COMPLETED);

        when(transferRepository.findById("tx-1")).thenReturn(Optional.of(transfer));

        TransferResponseDto response = transferService.getTransfer("tx-1");

        assertEquals("tx-1", response.transferId());
        assertEquals("COMPLETED", response.status());


        verify(idempotencyKeyRepository, never()).save(any());
        verify(idempotencyKeyRepository, never()).saveAndFlush(any());
        verify(transferRepository, never()).save(any());
        verify(transferRepository, never()).saveAndFlush(any());
        verify(resilientLedgerClient, never()).postTransfer(any());
    }

    @Test
    void getTransfer_shouldThrowNotFound_whenTransferMissing() {
        when(transferRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> transferService.getTransfer("missing"));
    }

    @Test
    void createTransfer_shouldReturnExistingResponse_whenIdempotencyKeyExistsWithSameHash() throws Exception {
        TransferRequestDto request = new TransferRequestDto(1L, 2L, BigDecimal.valueOf(100));
        String idempotencyKey = "idem-123";
        String requestHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest("1|2|100".getBytes(StandardCharsets.UTF_8))
        );

        TransferResponseDto expectedResponse = new TransferResponseDto("tx-1", "COMPLETED");
        IdempotencyKey existingKey = new IdempotencyKey();
        existingKey.setKey(idempotencyKey);
        existingKey.setRequestHash(requestHash);
        existingKey.setResponseJson(new ObjectMapper().writeValueAsString(expectedResponse));

        when(idempotencyKeyRepository.findByKey(idempotencyKey)).thenReturn(Optional.of(existingKey));
        when(objectMapper.readValue(existingKey.getResponseJson(), TransferResponseDto.class)).thenReturn(expectedResponse);

        TransferResponseDto response = transferService.createTransfer(request, idempotencyKey);

        assertEquals("tx-1", response.transferId());
        assertEquals("COMPLETED", response.status());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_shouldThrowConflict_whenIdempotencyKeyExistsWithDifferentHash() {
        TransferRequestDto request = new TransferRequestDto(1L, 2L, BigDecimal.valueOf(100));
        IdempotencyKey existingKey = new IdempotencyKey();
        existingKey.setKey("idem-123");
        existingKey.setRequestHash("DIFFERENT_HASH");

        when(idempotencyKeyRepository.findByKey("idem-123")).thenReturn(Optional.of(existingKey));

        assertThrows(ConflictException.class, () -> transferService.createTransfer(request, "idem-123"));
    }

    @Test
    void createTransfer_shouldCreateAndPersistTransfer_whenNoIdempotencyKeyExists() throws Exception {
        TransferRequestDto request = new TransferRequestDto(1L, 2L, BigDecimal.valueOf(100));
        String idempotencyKey = "idem-456";

        when(idempotencyKeyRepository.findByKey(idempotencyKey)).thenReturn(Optional.empty());
        when(idempotencyKeyRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Transfer savedTransfer = new Transfer();
        savedTransfer.setId("tx-999");
        savedTransfer.setFromAccountId(1L);
        savedTransfer.setToAccountId(2L);
        savedTransfer.setAmount(BigDecimal.valueOf(100));
        when(transferRepository.saveAndFlush(any())).thenReturn(savedTransfer);

        LedgerTransferResponse ledgerResponse = new LedgerTransferResponse("SUCCESS", "OK");
        when(resilientLedgerClient.postTransfer(any())).thenReturn(CompletableFuture.completedFuture(ledgerResponse));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":\"tx-999\",\"status\":\"COMPLETED\"}");

        TransferResponseDto response = transferService.createTransfer(request, idempotencyKey);

        assertEquals("tx-999", response.transferId());
        assertEquals("COMPLETED", response.status());
        verify(idempotencyKeyRepository).save(any());
        verify(transferRepository).save(any());
    }
}
