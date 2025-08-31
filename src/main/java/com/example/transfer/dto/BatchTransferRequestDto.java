package com.example.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchTransferRequestDto(
        @NotEmpty @Size(max = 20) List<@Valid Item> items
) {
    public record Item(
            String idempotencyKey,
            @Valid TransferRequestDto transfer
    ) {}
}
