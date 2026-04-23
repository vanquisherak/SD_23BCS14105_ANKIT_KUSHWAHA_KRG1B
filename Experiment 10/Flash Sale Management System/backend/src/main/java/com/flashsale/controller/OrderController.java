package com.flashsale.controller;

import com.flashsale.dto.OrderStatusResponse;
import com.flashsale.dto.PayOrderRequest;
import com.flashsale.dto.PayOrderResponse;
import com.flashsale.service.OrderWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderWorkflowService orderWorkflowService;

    public OrderController(OrderWorkflowService orderWorkflowService) {
        this.orderWorkflowService = orderWorkflowService;
    }

    @GetMapping("/{orderRef}")
    public OrderStatusResponse getOrder(@PathVariable String orderRef) {
        return orderWorkflowService.getOrder(orderRef);
    }

    @PostMapping("/{orderRef}/pay")
    public PayOrderResponse pay(@PathVariable String orderRef, @RequestBody @Valid PayOrderRequest request) {
        return orderWorkflowService.payOrder(orderRef, request);
    }
}
