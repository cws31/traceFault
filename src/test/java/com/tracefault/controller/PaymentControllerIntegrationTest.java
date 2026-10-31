package com.tracefault.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracefault.config.TraceFaultProperties;
import com.tracefault.dto.PaymentRequest;
import com.tracefault.entity.Payment;
import com.tracefault.entity.PaymentStatus;
import com.tracefault.repository.PaymentRepository;
import com.tracefault.service.PaymentService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TraceFaultProperties properties;

    @BeforeEach
    void setUp() {
        paymentService.resetDemoData();
        
        paymentService.setIdempotencyEnabled(false);
    }

    @Test
    @DisplayName("Test 1: Normal payment creates one successful payment record")
    void test1_normalPayment_createsSingleRecord() throws Exception {
        PaymentRequest request = new PaymentRequest("ORDER-101", new BigDecimal("1000.00"), "INR");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "KEY-TEST-001")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is("ORDER-101")))
                .andExpect(jsonPath("$.amount", is(1000.00)))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andExpect(jsonPath("$.paymentStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.duplicate", is(false)));

        assertThat(paymentRepository.count()).isEqualTo(1);
        Payment saved = paymentRepository.findAll().get(0);
        assertThat(saved.getOrderId()).isEqualTo("ORDER-101");
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(saved.getCurrency()).isEqualTo("INR");
        assertThat(saved.getIdempotencyKey()).isEqualTo("KEY-TEST-001");
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Test 2: Duplicate payment before correction creates two records with the same idempotency key")
    void test2_duplicatePayment_beforeCorrection_createsDuplicateRecords() throws Exception {
        
        paymentService.setIdempotencyEnabled(false);

        PaymentRequest request = new PaymentRequest("ORDER-101", new BigDecimal("1000.00"), "INR");
        String idempotencyKey = "KEY-TEST-DUPLICATE-002";

      
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

    
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

      
        List<Payment> duplicates = paymentRepository.findAllByIdempotencyKey(idempotencyKey);
        assertThat(duplicates).hasSize(2);
        assertThat(paymentRepository.count()).isEqualTo(2);

    
        mockMvc.perform(get("/api/payments/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords", is(2)))
                .andExpect(jsonPath("$.successfulPayments", is(2)))
                .andExpect(jsonPath("$.totalProcessedAmount", is(2000.00)));
    }

    @Test
    @DisplayName("Test 3: Duplicate payment after correction returns existing record and creates only one payment record")
    void test3_duplicatePayment_afterCorrection_returnsExistingRecordWithoutDuplication() throws Exception {
      
        paymentService.setIdempotencyEnabled(true);

        PaymentRequest request = new PaymentRequest("ORDER-102", new BigDecimal("1500.00"), "INR");
        String idempotencyKey = "KEY-TEST-CORRECTED-003";

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate", is(false)))
                .andExpect(jsonPath("$.amount", is(1500.00)));

        assertThat(paymentRepository.count()).isEqualTo(1);
        Long firstRecordId = paymentRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate", is(true)))
                .andExpect(jsonPath("$.id", is(firstRecordId.intValue())))
                .andExpect(jsonPath("$.orderId", is("ORDER-102")))
                .andExpect(jsonPath("$.amount", is(1500.00)));

       
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.findAllByIdempotencyKey(idempotencyKey)).hasSize(1);

        mockMvc.perform(get("/api/payments/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords", is(1)))
                .andExpect(jsonPath("$.successfulPayments", is(1)))
                .andExpect(jsonPath("$.totalProcessedAmount", is(1500.00)));
    }

    @Test
    @DisplayName("Test 4: Two legitimate payments with different idempotency keys create two distinct records")
    void test4_differentPaymentIdentifiers_createsIndependentRecords() throws Exception {
        paymentService.setIdempotencyEnabled(true);

        PaymentRequest payment1 = new PaymentRequest("ORDER-201", new BigDecimal("500.00"), "INR");
        PaymentRequest payment2 = new PaymentRequest("ORDER-202", new BigDecimal("750.00"), "INR");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "KEY-201")
                        .content(objectMapper.writeValueAsString(payment1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is("ORDER-201")));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "KEY-202")
                        .content(objectMapper.writeValueAsString(payment2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is("ORDER-202")));

        assertThat(paymentRepository.count()).isEqualTo(2);
        assertThat(paymentRepository.findByIdempotencyKey("KEY-201")).isPresent();
        assertThat(paymentRepository.findByIdempotencyKey("KEY-202")).isPresent();
    }

    @Test
    @DisplayName("Test 5: Reusing an existing idempotency key with a different payment payload returns HTTP 409 Conflict")
    void test5_payloadMismatch_returns409Conflict() throws Exception {
        paymentService.setIdempotencyEnabled(true);

        String sharedKey = "KEY-CONFLICT-005";
        PaymentRequest originalRequest = new PaymentRequest("ORDER-301", new BigDecimal("1000.00"), "INR");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", sharedKey)
                        .content(objectMapper.writeValueAsString(originalRequest)))
                .andExpect(status().isOk());

      
        PaymentRequest differentAmount = new PaymentRequest("ORDER-301", new BigDecimal("2000.00"), "INR");
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", sharedKey)
                        .content(objectMapper.writeValueAsString(differentAmount)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("different payment payload")));

     
        PaymentRequest differentOrder = new PaymentRequest("ORDER-999", new BigDecimal("1000.00"), "INR");
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", sharedKey)
                        .content(objectMapper.writeValueAsString(differentOrder)))
                .andExpect(status().isConflict());

       
        PaymentRequest differentCurrency = new PaymentRequest("ORDER-301", new BigDecimal("1000.00"), "USD");
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", sharedKey)
                        .content(objectMapper.writeValueAsString(differentCurrency)))
                .andExpect(status().isConflict());

       
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 6: Concurrent duplicate requests with the same idempotency key create only one record")
    void test6_concurrentDuplicateRequests_createsSingleRecordSafely() throws Exception {
        paymentService.setIdempotencyEnabled(true);

        int concurrencyLevel = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrencyLevel);

        String sharedKey = "KEY-CONCURRENT-006";
        PaymentRequest request = new PaymentRequest("ORDER-CONC-100", new BigDecimal("350.00"), "INR");

        AtomicInteger successResponses = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrencyLevel; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); 
                    var response = paymentService.processPayment(request, sharedKey, null);
                    if (response != null && response.getOrderId().equals("ORDER-CONC-100")) {
                        successResponses.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(successResponses.get()).isEqualTo(concurrencyLevel);

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.findAllByIdempotencyKey(sharedKey)).hasSize(1);
    }

    @Test
    @DisplayName("Test 7: Statistics reflect the actual persisted records")
    void test7_statisticsVerification_reflectsActualDatabaseState() throws Exception {
        paymentService.setIdempotencyEnabled(true);

      
        mockMvc.perform(get("/api/payments/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords", is(0)))
                .andExpect(jsonPath("$.successfulPayments", is(0)))
                .andExpect(jsonPath("$.totalProcessedAmount", is(0)));

        paymentService.processPayment(new PaymentRequest("ORD-1", new BigDecimal("100.50"), "INR"), "KEY-1", null);
        paymentService.processPayment(new PaymentRequest("ORD-2", new BigDecimal("200.25"), "INR"), "KEY-2", null);
        paymentService.processPayment(new PaymentRequest("ORD-3", new BigDecimal("300.00"), "INR"), "KEY-3", null);

        mockMvc.perform(get("/api/payments/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords", is(3)))
                .andExpect(jsonPath("$.successfulPayments", is(3)))
                .andExpect(jsonPath("$.totalProcessedAmount", is(600.75)));
    }

    @Test
    @DisplayName("Missing Idempotency-Key header when protection is enabled returns HTTP 400 Bad Request")
    void testMissingIdempotencyKey_returns400BadRequest() throws Exception {
        paymentService.setIdempotencyEnabled(true);

        PaymentRequest request = new PaymentRequest("ORDER-400", new BigDecimal("500.00"), "INR");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key header is required")));
    }

    @Test
    @DisplayName("Query payment by idempotency key returns record when found and 404 when not found")
    void testGetPaymentByIdempotencyKey() throws Exception {
        paymentService.setIdempotencyEnabled(true);

        PaymentRequest request = new PaymentRequest("ORDER-500", new BigDecimal("880.00"), "INR");
        paymentService.processPayment(request, "KEY-500", null);

        // Found
        mockMvc.perform(get("/api/payments/by-key/KEY-500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is("ORDER-500")))
                .andExpect(jsonPath("$.amount", is(880.00)))
                .andExpect(jsonPath("$.idempotencyKey", is("KEY-500")));

        // Not Found
        mockMvc.perform(get("/api/payments/by-key/NON-EXISTENT-KEY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("Reset demonstration data endpoint clears synthetic payments")
    void testResetEndpoint_clearsRecords() throws Exception {
        PaymentRequest request = new PaymentRequest("ORDER-600", new BigDecimal("120.00"), "INR");
        paymentService.processPayment(request, "KEY-600", null);
        assertThat(paymentRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/demo/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.message", containsString("reset")));

        assertThat(paymentRepository.count()).isEqualTo(0);
    }
}
