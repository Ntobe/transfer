package com.example.transfer.service;

import com.example.transfer.dto.LedgerTransferRequest;
import com.example.transfer.dto.LedgerTransferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class LedgerClient {

    private final WebClient webClient;

    public LedgerTransferResponse postTransfer(LedgerTransferRequest request) {
        return webClient.post()
                .uri("v1/ledger/transfer")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(LedgerTransferResponse.class)
                .block();
    }
}
