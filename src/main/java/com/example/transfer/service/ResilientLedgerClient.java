package com.example.transfer.service;

import com.example.transfer.dto.LedgerTransferRequest;
import com.example.transfer.dto.LedgerTransferResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ResilientLedgerClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientLedgerClient.class);

    private final LedgerClient delegate;

    @CircuitBreaker(name = "ledger", fallbackMethod = "fallback")
    @TimeLimiter(name = "ledger")
    public CompletableFuture<LedgerTransferResponse> postTransfer(LedgerTransferRequest req) {
        // Structured “attempt” log
        log.info("{}", Map.of(
                "event", "ledger_call_attempt",
                "fromAccountId", req.fromAccountId(),
                "toAccountId", req.toAccountId(),
                "amount", req.amount(),
                "transferId", req.transferId()
        ));

        // Wrap sync client in CompletableFuture for @TimeLimiter
        return CompletableFuture.supplyAsync(() -> delegate.postTransfer(req));
    }

    // Fallback must match return type and accept Throwable last
    private CompletableFuture<LedgerTransferResponse> fallback(LedgerTransferRequest req, Throwable ex) {
        log.error("{}", Map.of(
                "event", "ledger_call_failed",
                "fromAccountId", req.fromAccountId(),
                "toAccountId", req.toAccountId(),
                "amount", req.amount(),
                "transferId", req.transferId(),
                "errorType", ex.getClass().getSimpleName(),
                "message", ex.getMessage()
        ));

        // Degrade gracefully — let caller mark transfer FAILED
        return CompletableFuture.completedFuture(new LedgerTransferResponse("FAILURE",
                "Ledger unavailable: " + ex.getClass().getSimpleName()));
    }
}
