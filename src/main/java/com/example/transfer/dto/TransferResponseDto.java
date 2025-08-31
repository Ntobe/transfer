package com.example.transfer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponseDto(
        String transferId,
        String status,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        Instant createdAt,
        String message
) {}
