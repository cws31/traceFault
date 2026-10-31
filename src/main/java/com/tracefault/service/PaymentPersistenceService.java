package com.tracefault.service;

import com.tracefault.dto.PaymentRequest;
import com.tracefault.entity.Payment;
import com.tracefault.entity.PaymentStatus;
import com.tracefault.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PaymentPersistenceService.class);

    private final PaymentRepository paymentRepository;

    public PaymentPersistenceService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createAndPersistPayment(PaymentRequest request, String idempotencyKey) {
        Payment payment = new Payment(
                request.getOrderId(),
                request.getAmount(),
                request.getCurrency(),
                idempotencyKey,
                PaymentStatus.SUCCESS
        );

        Payment saved = paymentRepository.saveAndFlush(payment);
        log.info("Committed payment [id={}, orderId='{}', amount={} {}, idempotencyKey='{}'] to database",
                saved.getId(), saved.getOrderId(), saved.getAmount(), saved.getCurrency(), saved.getIdempotencyKey());
        return saved;
    }
}
