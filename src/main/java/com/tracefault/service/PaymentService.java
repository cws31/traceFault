package com.tracefault.service;

import com.tracefault.config.DatabaseIndexManager;
import com.tracefault.config.TraceFaultProperties;
import com.tracefault.dto.PaymentRequest;
import com.tracefault.dto.PaymentResponse;
import com.tracefault.dto.PaymentStatisticsResponse;
import com.tracefault.entity.Payment;
import com.tracefault.entity.PaymentStatus;
import com.tracefault.exception.MissingIdempotencyKeyException;
import com.tracefault.exception.PaymentConflictException;
import com.tracefault.exception.PaymentNotFoundException;
import com.tracefault.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentPersistenceService paymentPersistenceService;
    private final FailureSimulationService failureSimulationService;
    private final TraceFaultProperties properties;
    private final DatabaseIndexManager databaseIndexManager;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentPersistenceService paymentPersistenceService,
                          FailureSimulationService failureSimulationService,
                          TraceFaultProperties properties,
                          DatabaseIndexManager databaseIndexManager) {
        this.paymentRepository = paymentRepository;
        this.paymentPersistenceService = paymentPersistenceService;
        this.failureSimulationService = failureSimulationService;
        this.properties = properties;
        this.databaseIndexManager = databaseIndexManager;
    }

    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey, Long delayMs) {
        boolean idempotencyEnabled = properties.getIdempotency().isEnabled();

        if (idempotencyEnabled) {
            return processWithIdempotency(request, idempotencyKey, delayMs);
        } else {
            return processWithoutIdempotency(request, idempotencyKey, delayMs);
        }
    }

    private PaymentResponse processWithoutIdempotency(PaymentRequest request, String idempotencyKey, Long delayMs) {
        log.warn("Processing payment in VULNERABLE mode (idempotency disabled). OrderId: '{}', Key: '{}'",
                request.getOrderId(), idempotencyKey);

        Payment payment = paymentPersistenceService.createAndPersistPayment(request, idempotencyKey);
        failureSimulationService.simulateDelayIfNeeded(delayMs);
        return PaymentResponse.from(payment, false);
    }

    private PaymentResponse processWithIdempotency(PaymentRequest request, String idempotencyKey, Long delayMs) {
        log.info("Processing payment in PROTECTED mode (idempotency enabled). OrderId: '{}', Key: '{}'",
                request.getOrderId(), idempotencyKey);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header is required when idempotency protection is enabled.");
        }

        String cleanKey = idempotencyKey.trim();

        Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(cleanKey);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            validatePayloadMatch(existing, request, cleanKey);
            log.info("Idempotent request detected for key '{}'. Returning existing payment record [id={}].",
                    cleanKey, existing.getId());
            return PaymentResponse.from(existing, true);
        }

        Payment payment;
        try {
            payment = paymentPersistenceService.createAndPersistPayment(request, cleanKey);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Database unique constraint caught concurrent duplicate for key '{}'. Resolving existing record.", cleanKey);
            Payment existing = paymentRepository.findByIdempotencyKey(cleanKey)
                    .orElseThrow(() -> ex);
            validatePayloadMatch(existing, request, cleanKey);
            return PaymentResponse.from(existing, true);
        }

        failureSimulationService.simulateDelayIfNeeded(delayMs);

        return PaymentResponse.from(payment, false);
    }

    private void validatePayloadMatch(Payment existing, PaymentRequest request, String key) {
        boolean orderMatches = existing.getOrderId().equals(request.getOrderId());
        boolean amountMatches = existing.getAmount().compareTo(request.getAmount()) == 0;
        boolean currencyMatches = existing.getCurrency().equalsIgnoreCase(request.getCurrency());

        if (!orderMatches || !amountMatches || !currencyMatches) {
            String mismatchMsg = String.format(
                    "Idempotency key '%s' was already used for a different payment payload. " +
                            "Existing: [orderId=%s, amount=%s %s], Requested: [orderId=%s, amount=%s %s]",
                    key, existing.getOrderId(), existing.getAmount(), existing.getCurrency(),
                    request.getOrderId(), request.getAmount(), request.getCurrency()
            );
            log.error("Idempotency conflict: {}", mismatchMsg);
            throw new PaymentConflictException(mismatchMsg);
        }
    }

    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(p -> PaymentResponse.from(p, false))
                .toList();
    }

    public PaymentResponse getPaymentByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(p -> PaymentResponse.from(p, false))
                .orElseThrow(() -> new PaymentNotFoundException("Payment with idempotency key '" + idempotencyKey + "' was not found."));
    }

    public PaymentStatisticsResponse getStatistics() {
        long totalRecords = paymentRepository.count();
        long successfulPayments = paymentRepository.countByPaymentStatus(PaymentStatus.SUCCESS);
        BigDecimal totalAmount = paymentRepository.sumSuccessfulAmounts();
        return new PaymentStatisticsResponse(totalRecords, successfulPayments, totalAmount);
    }

    public synchronized void resetDemoData() {
        log.warn("=== RESETTING DEMONSTRATION DATA ===");
        paymentRepository.deleteAll();
        databaseIndexManager.syncIndexWithMode(properties.getIdempotency().isEnabled());
        log.info("Demonstration data cleared. Current mode: idempotency.enabled={}", properties.getIdempotency().isEnabled());
    }

    public synchronized void setIdempotencyEnabled(boolean enabled) {
        log.info("Setting idempotency protection to: {}", enabled);
        properties.getIdempotency().setEnabled(enabled);
        databaseIndexManager.syncIndexWithMode(enabled);
    }

    public boolean isIdempotencyEnabled() {
        return properties.getIdempotency().isEnabled();
    }
}
