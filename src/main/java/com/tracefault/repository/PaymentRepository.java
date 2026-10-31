package com.tracefault.repository;

import com.tracefault.entity.Payment;
import com.tracefault.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findAllByIdempotencyKey(String idempotencyKey);

    List<Payment> findAllByOrderByCreatedAtDesc();

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paymentStatus = com.tracefault.entity.PaymentStatus.SUCCESS")
    BigDecimal sumSuccessfulAmounts();
}
