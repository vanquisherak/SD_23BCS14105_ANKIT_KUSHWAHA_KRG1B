package com.flashsale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "flashsale")
public class FlashSaleProperties {

    private final RateLimit rateLimit = new RateLimit();
    private final Order order = new Order();
    private final Queue queue = new Queue();

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Order getOrder() {
        return order;
    }

    public Queue getQueue() {
        return queue;
    }

    public static class RateLimit {
        private int maxRequests = 5;
        private int windowSeconds = 5;

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class Order {
        private int expiryMinutes = 10;

        public int getExpiryMinutes() {
            return expiryMinutes;
        }

        public void setExpiryMinutes(int expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
        }
    }

    public static class Queue {
        private String key = "FLASHSALE:CHECKOUT:QUEUE";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }
}
