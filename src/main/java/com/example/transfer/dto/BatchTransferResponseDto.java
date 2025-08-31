package com.example.transfer.dto;

import java.util.List;

public record BatchTransferResponseDto(
        List<Result> results
) {
    public record Result(
            String idempotencyKey,
            TransferResponseDto response
    ) {}
}
