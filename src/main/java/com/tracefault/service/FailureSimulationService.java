package com.tracefault.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FailureSimulationService {

    private static final Logger log = LoggerFactory.getLogger(FailureSimulationService.class);

    public void simulateDelayIfNeeded(Long delayMs) {
        if (delayMs != null && delayMs > 0) {
            log.warn("=== [FAILURE INJECTION] Delaying HTTP response by {} ms AFTER database commit ===", delayMs);
            try {
                Thread.sleep(delayMs);
                log.info("=== [FAILURE INJECTION] Delay of {} ms completed. Resuming HTTP response. ===", delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Delay simulation interrupted: {}", e.getMessage());
            }
        }
    }
}
