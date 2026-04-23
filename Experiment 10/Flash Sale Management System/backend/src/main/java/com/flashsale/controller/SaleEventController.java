package com.flashsale.controller;

import com.flashsale.dto.CreateSaleEventRequest;
import com.flashsale.dto.JoinSaleRequest;
import com.flashsale.dto.JoinSaleResponse;
import com.flashsale.dto.SaleEventSummaryResponse;
import com.flashsale.entity.SaleEvent;
import com.flashsale.service.OrderWorkflowService;
import com.flashsale.service.SaleEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleEventController {

    private final SaleEventService saleEventService;
    private final OrderWorkflowService orderWorkflowService;

    public SaleEventController(SaleEventService saleEventService, OrderWorkflowService orderWorkflowService) {
        this.saleEventService = saleEventService;
        this.orderWorkflowService = orderWorkflowService;
    }

    @PostMapping
    public SaleEvent createSaleEvent(@RequestBody @Valid CreateSaleEventRequest request) {
        return saleEventService.create(request);
    }

    @GetMapping
    public List<SaleEventSummaryResponse> listSaleEvents() {
        return saleEventService.listAll();
    }

    @PostMapping("/{saleEventId}/join")
    public ResponseEntity<JoinSaleResponse> joinSale(
            @PathVariable Long saleEventId,
            @RequestBody @Valid JoinSaleRequest request
    ) {
        JoinSaleResponse response = orderWorkflowService.joinSale(saleEventId, request);
        if ("SOLD_OUT".equals(response.error())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        if ("TOO_MANY_REQUESTS".equals(response.error())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        if (response.error() != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
