package com.example.transfer.service;

import com.example.transfer.dto.LedgerTransferResponse;
import com.example.transfer.dto.TransferRequestDto;
import com.example.transfer.dto.TransferResponseDto;
import com.example.transfer.repository.IdempotencyKeyRepository;
import com.example.transfer.repository.TransferRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class TransferServiceIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private LedgerClient ledgerClient; // mock external ledger

    private TransferRequestDto request1;
    private TransferRequestDto request2;

    @BeforeEach
    void setup() {
        request1 = new TransferRequestDto(1L, 2L, BigDecimal.valueOf(100));
        request2 = new TransferRequestDto(1L, 3L, BigDecimal.valueOf(50));
    }

    @Test
    void testHappyPathTransfer() {
        // Ledger responds successfully
        Mockito.when(ledgerClient.postTransfer(Mockito.any()))
                .thenReturn(new LedgerTransferResponse("SUCCESS", "Processed successfully"));

        TransferResponseDto resp = transferService.createTransfer(request1, "key1");

        assertEquals("COMPLETED", resp.status());
        assertEquals(request1.amount(), resp.amount());
        assertEquals(request1.fromAccountId(), resp.fromAccountId());
        assertEquals(request1.toAccountId(), resp.toAccountId());

        // Idempotency stored
        assertTrue(idempotencyKeyRepository.findByKey("key1").isPresent());
    }

    @Test
    void testConcurrentTransfersOnSameAccount() throws InterruptedException, ExecutionException {
        Mockito.when(ledgerClient.postTransfer(Mockito.any()))
                .thenAnswer(invocation -> new LedgerTransferResponse("SUCCESS", "Processed"));

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        Callable<TransferResponseDto> task1 = () ->
                transferService.createTransfer(request1, "key-concurrent-1");
        Callable<TransferResponseDto> task2 = () ->
                transferService.createTransfer(request2, "key-concurrent-2");

        Future<TransferResponseDto> f1 = executor.submit(task1);
        Future<TransferResponseDto> f2 = executor.submit(task2);

        TransferResponseDto resp1 = f1.get();
        TransferResponseDto resp2 = f2.get();

        assertEquals("COMPLETED", resp1.status());
        assertEquals("COMPLETED", resp2.status());

        // Both transfers are persisted
        assertEquals(2, transferRepository.count());
    }

    @Test
    void testCircuitBreakerOpensAfterFailures() {
        // Mock ledger client to always fail
        Mockito.when(ledgerClient.postTransfer(Mockito.any()))
                .thenThrow(new RuntimeException("Ledger unavailable"));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("ledger");

        // Cause enough failures to trigger OPEN state
        for (int i = 0; i < 3; i++) {
            try {
                transferService.createTransfer(request1, "cb-key-" + i);
            } catch (Exception ignored) {
            }
        }

        // Verify breaker is OPEN
        assertEquals(CircuitBreaker.State.OPEN, cb.getState(),
                "Circuit breaker should be OPEN after repeated failures");
    }
}
