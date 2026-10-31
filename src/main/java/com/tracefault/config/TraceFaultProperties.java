package com.tracefault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tracefault")
public class TraceFaultProperties {

    private IdempotencyConfig idempotency = new IdempotencyConfig();
    private DemoConfig demo = new DemoConfig();

    public IdempotencyConfig getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(IdempotencyConfig idempotency) {
        this.idempotency = idempotency;
    }

    public DemoConfig getDemo() {
        return demo;
    }

    public void setDemo(DemoConfig demo) {
        this.demo = demo;
    }

    public static class IdempotencyConfig {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class DemoConfig {
        /**
         * Whether demo endpoints (such as reset) are enabled.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
