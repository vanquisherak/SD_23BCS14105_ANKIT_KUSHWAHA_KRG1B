package com.flashsale.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpiryScheduler {

    private final OrderWorkflowService orderWorkflowService;

    public OrderExpiryScheduler(OrderWorkflowService orderWorkflowService) {
        this.orderWorkflowService = orderWorkflowService;
    }

    @Scheduled(fixedDelay = 10000)
    public void expireOrders() {
        orderWorkflowService.expirePendingOrders();
    }
}
