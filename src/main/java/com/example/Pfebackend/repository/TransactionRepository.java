package com.example.Pfebackend.repository;

import com.example.Pfebackend.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    List<Transaction> findByUserIdOrderByTransactionDateDesc(String userId);
}
