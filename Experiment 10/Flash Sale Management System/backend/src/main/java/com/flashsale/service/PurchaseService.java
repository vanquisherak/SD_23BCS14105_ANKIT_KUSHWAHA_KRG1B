package com.flashsale.service;

import com.flashsale.dto.PurchaseRequest;
import com.flashsale.dto.PurchaseResponse;
import com.flashsale.entity.OrderStatus;
import com.flashsale.entity.PurchaseOrder;
import com.flashsale.entity.SaleEvent;
import com.flashsale.exception.BusinessException;
import com.flashsale.repository.PurchaseOrderRepository;
import com.flashsale.repository.SaleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PurchaseService {

    private final SaleEventRepository saleEventRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public PurchaseService(SaleEventRepository saleEventRepository, PurchaseOrderRepository purchaseOrderRepository) {
        this.saleEventRepository = saleEventRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional
    public PurchaseResponse buy(Long saleEventId, PurchaseRequest request) {
        SaleEvent saleEvent = saleEventRepository.findByIdForUpdate(saleEventId)
                .orElseThrow(() -> new BusinessException("Sale event not found"));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(saleEvent.getStartTime()) || now.isAfter(saleEvent.getEndTime())) {
            throw new BusinessException("Sale event is not currently active");
        }

        if (request.quantity() > saleEvent.getSaleStock()) {
            throw new BusinessException("Not enough sale stock available");
        }

        int purchasedByUser = purchaseOrderRepository.getTotalPurchasedByUser(saleEventId, request.userId());
        if (purchasedByUser + request.quantity() > saleEvent.getMaxPerUser()) {
            throw new BusinessException("User purchase limit exceeded for this sale");
        }

        saleEvent.setSaleStock(saleEvent.getSaleStock() - request.quantity());
        saleEventRepository.save(saleEvent);

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderRef(UUID.randomUUID().toString());
        order.setSaleEvent(saleEvent);
        order.setUserId(request.userId());
        order.setQuantity(request.quantity());
        order.setStatus(OrderStatus.PAID);
        order.setUnitPrice(saleEvent.getSalePrice());
        BigDecimal total = saleEvent.getSalePrice().multiply(BigDecimal.valueOf(request.quantity()));
        order.setTotalAmount(total);
        order.setReservedAt(now);
        order.setPurchasedAt(now);
        order.setExpiresAt(now);
        order.setPaidAt(now);

        PurchaseOrder saved = purchaseOrderRepository.save(order);

        return new PurchaseResponse(
                saved.getId(),
                saleEventId,
                saved.getUserId(),
                saved.getQuantity(),
                saved.getTotalAmount(),
                saved.getPaidAt()
        );
    }
}
