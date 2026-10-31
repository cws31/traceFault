package com.tracefault.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIndexManager implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseIndexManager.class);
    private static final String UNIQUE_INDEX_NAME = "uk_payment_idempotency_key";

    private final JdbcTemplate jdbcTemplate;
    private final TraceFaultProperties properties;

    public DatabaseIndexManager(JdbcTemplate jdbcTemplate, TraceFaultProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        syncIndexWithMode(properties.getIdempotency().isEnabled());
    }

    public synchronized void syncIndexWithMode(boolean idempotencyEnabled) {
        try {
            if (idempotencyEnabled) {
                log.info("Enforcing database unique index '{}' on payments(idempotency_key)", UNIQUE_INDEX_NAME);
                jdbcTemplate.execute(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " + UNIQUE_INDEX_NAME + " ON payments(idempotency_key)");
                log.info("Enforced database unique index '{}' successfully.", UNIQUE_INDEX_NAME);
            } else {
                log.info("Checking/dropping database unique index '{}' to allow duplicate payment demonstration",
                        UNIQUE_INDEX_NAME);
                jdbcTemplate.execute("DROP INDEX IF EXISTS " + UNIQUE_INDEX_NAME);
                log.info("Dropped database unique index '{}' successfully (if existed).", UNIQUE_INDEX_NAME);
            }
        } catch (Exception ex) {
            log.warn("Failed to synchronize database index with mode (enabled={}): {}", idempotencyEnabled,
                    ex.getMessage());
        }
    }
}
