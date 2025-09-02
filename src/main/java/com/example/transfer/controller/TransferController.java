package com.example.transfer.controller;

import com.example.transfer.dto.BatchTransferRequestDto;
import com.example.transfer.dto.BatchTransferResponseDto;
import com.example.transfer.dto.TransferRequestDto;
import com.example.transfer.dto.TransferResponseDto;
import com.example.transfer.service.BatchTransferService;
import com.example.transfer.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TransferController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TransferService transferService;
    private final BatchTransferService batchTransferService;

    @PostMapping(path = "/v1/transfers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TransferResponseDto create(@RequestHeader(IDEMPOTENCY_KEY) String idemKey,
                                      @Valid @RequestBody TransferRequestDto body) {
        return transferService.createTransfer(body, idemKey);
    }

    @GetMapping(path = "/v1/transfers/{id}")
    public TransferResponseDto get(@PathVariable String id) {
        return transferService.getTransfer(id);
    }

    @PostMapping(path = "/v1/transfers/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BatchTransferResponseDto batch(@Valid @RequestBody BatchTransferRequestDto body) {
        return batchTransferService.processBatch(body);
    }
}
