package com.flashsale.service;

import com.flashsale.dto.CreateSaleEventRequest;
import com.flashsale.dto.SaleEventSummaryResponse;
import com.flashsale.entity.Product;
import com.flashsale.entity.SaleEvent;
import com.flashsale.exception.BusinessException;
import com.flashsale.repository.ProductRepository;
import com.flashsale.repository.SaleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SaleEventService {

    private final SaleEventRepository saleEventRepository;
    private final ProductRepository productRepository;
    private final FlashSaleRedisService flashSaleRedisService;

    public SaleEventService(
            SaleEventRepository saleEventRepository,
            ProductRepository productRepository,
            FlashSaleRedisService flashSaleRedisService
    ) {
        this.saleEventRepository = saleEventRepository;
        this.productRepository = productRepository;
        this.flashSaleRedisService = flashSaleRedisService;
    }

    public SaleEvent create(CreateSaleEventRequest request) {
        if (request.endTime().isBefore(request.startTime())) {
            throw new BusinessException("End time must be after start time");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException("Product not found"));

        if (request.saleStock() > product.getTotalStock()) {
            throw new BusinessException("Sale stock cannot exceed product total stock");
        }

        SaleEvent saleEvent = new SaleEvent();
        saleEvent.setProduct(product);
        saleEvent.setStartTime(request.startTime());
        saleEvent.setEndTime(request.endTime());
        saleEvent.setSaleStock(request.saleStock());
        saleEvent.setMaxPerUser(request.maxPerUser());
        saleEvent.setSalePrice(request.salePrice());

        SaleEvent saved = saleEventRepository.save(saleEvent);
        flashSaleRedisService.preloadSale(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SaleEventSummaryResponse> listAll() {
        LocalDateTime now = LocalDateTime.now();
        return saleEventRepository.findAll().stream()
                .map(event -> {
                    String status = "UPCOMING";
                    if (!now.isBefore(event.getStartTime()) && !now.isAfter(event.getEndTime())) {
                        status = "LIVE";
                    } else if (now.isAfter(event.getEndTime())) {
                        status = "ENDED";
                    }

                        Integer inventory = flashSaleRedisService.getInventory(event.getId());

                        return new SaleEventSummaryResponse(
                            event.getId(),
                            event.getProduct().getId(),
                            event.getProduct().getName(),
                            event.getProduct().getSku(),
                            event.getProduct().getRegularPrice(),
                            event.getSalePrice(),
                            inventory == null ? event.getSaleStock() : inventory,
                            event.getMaxPerUser(),
                            event.getStartTime(),
                            event.getEndTime(),
                            status
                    );
                })
                .toList();
    }
}
