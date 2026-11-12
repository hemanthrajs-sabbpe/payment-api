package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByMerchantOrderRef(String merchantOrderRef);

    Optional<Payment> findByPblOrderRef(String pblOrderRef);

    // Find pending payments with a Pay By Link reference
    List<Payment> findByStatusIgnoreCaseAndPblOrderRefIsNotNull(
            String status
    );

    // Find by Gateway Master Transaction ID
    Optional<Payment> findByMasterTransactionId(String masterTransactionId);

    // Find pending gateway payments
    List<Payment> findByGatewayProviderAndStatusIgnoreCase(String gatewayProvider, String status);
}
