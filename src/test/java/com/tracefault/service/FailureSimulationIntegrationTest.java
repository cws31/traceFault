package com.tracefault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracefault.dto.PaymentRequest;
import com.tracefault.entity.Payment;
import com.tracefault.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FailureSimulationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService.resetDemoData();
        paymentService.setIdempotencyEnabled(false);
    }

    @Test
    @DisplayName("Verify payment is committed to H2 database DURING the simulated response delay")
    void testPaymentCommittedBeforeResponseDelayFinishes() throws Exception {
        String key = "KEY-DELAY-TEST-001";
        PaymentRequest request = new PaymentRequest("ORDER-DELAY-1", new BigDecimal("999.00"), "INR");

        long delayMs = 1200;

        long startTime = System.currentTimeMillis();

        CompletableFuture<Void> requestFuture = CompletableFuture.runAsync(() -> {
            try {
                mockMvc.perform(post("/api/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", key)
                                .header("X-Demo-Delay-Response-Ms", String.valueOf(delayMs))
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(300);

        assertThat(requestFuture.isDone()).isFalse();
        assertThat(paymentRepository.findByIdempotencyKey(key)).isPresent();
        Payment persisted = paymentRepository.findByIdempotencyKey(key).get();
        assertThat(persisted.getOrderId()).isEqualTo("ORDER-DELAY-1");
        assertThat(persisted.getAmount()).isEqualByComparingTo(new BigDecimal("999.00"));

        requestFuture.get(3, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;
        assertThat(elapsed).isGreaterThanOrEqualTo(1000);
    }
}
