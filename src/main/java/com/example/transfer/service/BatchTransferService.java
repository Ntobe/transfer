package com.example.transfer.service;

import com.example.transfer.dto.BatchTransferRequestDto;
import com.example.transfer.dto.BatchTransferResponseDto;
import com.example.transfer.dto.TransferResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
public class BatchTransferService {
    private final TransferService transferService;

    public BatchTransferResponseDto processBatch(BatchTransferRequestDto batch) {
        try (ExecutorService executor = createExecutor()) {
            List<Callable<BatchTransferResponseDto.Result>> tasks = new ArrayList<>();
            for (BatchTransferRequestDto.Item item : batch.items()) {
                tasks.add(() -> {
                    TransferResponseDto resp = transferService.createTransfer(item.transfer(), item.idempotencyKey());
                    return new BatchTransferResponseDto.Result(item.idempotencyKey(), resp);
                });
            }

            List<Future<BatchTransferResponseDto.Result>> futures = executor.invokeAll(tasks);
            List<BatchTransferResponseDto.Result> results = new ArrayList<>();
            for (Future<BatchTransferResponseDto.Result> f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    TransferResponseDto failed = new TransferResponseDto(null, "FAILED");
                    results.add(new BatchTransferResponseDto.Result(null, failed));
                }
            }
            return new BatchTransferResponseDto(results);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Batch interrupted", ie);
        }
    }

    private ExecutorService createExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
