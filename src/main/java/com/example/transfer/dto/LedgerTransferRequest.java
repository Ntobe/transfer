package com.example.transfer.dto;

import java.math.BigDecimal;

public record LedgerTransferRequest(
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String transferId
) {}
