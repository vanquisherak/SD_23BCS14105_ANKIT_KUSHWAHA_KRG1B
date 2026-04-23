package com.flashsale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.dto.EnqueuedOrderMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlashSaleQueueWorker {

    private final FlashSaleRedisService flashSaleRedisService;
    private final OrderWorkflowService orderWorkflowService;
    private final ObjectMapper objectMapper;

    public FlashSaleQueueWorker(
            FlashSaleRedisService flashSaleRedisService,
            OrderWorkflowService orderWorkflowService,
            ObjectMapper objectMapper
    ) {
        this.flashSaleRedisService = flashSaleRedisService;
        this.orderWorkflowService = orderWorkflowService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 250)
    public void processCheckoutQueue() {
        for (int i = 0; i < 100; i++) {
            String payload = flashSaleRedisService.dequeue();
            if (payload == null) {
                return;
            }

            try {
                EnqueuedOrderMessage message = objectMapper.readValue(payload, EnqueuedOrderMessage.class);
                orderWorkflowService.processQueueMessage(message);
            } catch (JsonProcessingException ex) {
                // Skip malformed payload and continue to avoid blocking queue processing.
            }
        }
    }
}
