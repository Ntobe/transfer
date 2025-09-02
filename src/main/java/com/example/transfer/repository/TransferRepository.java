package com.example.transfer.repository;

import com.example.transfer.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, String> {
}
