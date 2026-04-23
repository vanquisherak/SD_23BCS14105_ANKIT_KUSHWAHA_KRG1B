package com.flashsale.controller;

import com.flashsale.dto.PurchaseRequest;
import com.flashsale.dto.PurchaseResponse;
import com.flashsale.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping("/{saleEventId}")
    public PurchaseResponse purchase(@PathVariable Long saleEventId, @RequestBody @Valid PurchaseRequest request) {
        return purchaseService.buy(saleEventId, request);
    }
}
