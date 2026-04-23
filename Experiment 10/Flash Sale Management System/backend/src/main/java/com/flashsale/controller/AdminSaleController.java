package com.flashsale.controller;

import com.flashsale.dto.CreateSaleEventRequest;
import com.flashsale.entity.SaleEvent;
import com.flashsale.service.SaleEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/sales")
public class AdminSaleController {

    private final SaleEventService saleEventService;

    public AdminSaleController(SaleEventService saleEventService) {
        this.saleEventService = saleEventService;
    }

    @PostMapping
    public SaleEvent createSale(@RequestBody @Valid CreateSaleEventRequest request) {
        return saleEventService.create(request);
    }
}
