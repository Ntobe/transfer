package com.example.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequestDto(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @Min(1) BigDecimal amount
) {}
