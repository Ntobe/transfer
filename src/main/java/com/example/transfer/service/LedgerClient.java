package com.example.transfer.service;

import com.example.transfer.dto.LedgerTransferRequest;
import com.example.transfer.dto.LedgerTransferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class LedgerClient {

    private final WebClient ledgerWebClient;

    public LedgerTransferResponse postTransfer(LedgerTransferRequest request) {
        return ledgerWebClient.post()
                .uri("v1/ledger/transfer")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(LedgerTransferResponse.class)
                .onErrorResume(e -> Mono.just(new LedgerTransferResponse("FAILURE", e.getMessage())))
                .block();
    }
}
