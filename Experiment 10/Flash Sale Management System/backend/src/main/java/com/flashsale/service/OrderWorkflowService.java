package com.flashsale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.config.FlashSaleProperties;
import com.flashsale.dto.*;
import com.flashsale.entity.OrderStatus;
import com.flashsale.entity.PurchaseOrder;
import com.flashsale.entity.SaleEvent;
import com.flashsale.exception.BusinessException;
import com.flashsale.repository.PurchaseOrderRepository;
import com.flashsale.repository.SaleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderWorkflowService {

    private final SaleEventRepository saleEventRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FlashSaleRedisService flashSaleRedisService;
    private final FlashSaleProperties properties;
    private final ObjectMapper objectMapper;

    public OrderWorkflowService(
            SaleEventRepository saleEventRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            FlashSaleRedisService flashSaleRedisService,
            FlashSaleProperties properties,
            ObjectMapper objectMapper
    ) {
        this.saleEventRepository = saleEventRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.flashSaleRedisService = flashSaleRedisService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JoinSaleResponse joinSale(Long saleId, JoinSaleRequest request) {
        SaleEvent saleEvent = saleEventRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException("Sale event not found"));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(saleEvent.getStartTime()) || now.isAfter(saleEvent.getEndTime())) {
            return JoinSaleResponse.blocked("SALE_NOT_ACTIVE");
        }

        if (!flashSaleRedisService.allowRequest(saleId, request.userId())) {
            return JoinSaleResponse.rateLimited(properties.getRateLimit().getWindowSeconds());
        }

        if (request.quantity() > saleEvent.getMaxPerUser()) {
            return JoinSaleResponse.blocked("MAX_PER_USER_EXCEEDED");
        }

        int alreadyPurchased = purchaseOrderRepository.getTotalPurchasedByUser(saleId, request.userId());
        if (alreadyPurchased + request.quantity() > saleEvent.getMaxPerUser()) {
            return JoinSaleResponse.blocked("MAX_PER_USER_EXCEEDED");
        }

        if (!flashSaleRedisService.markUserReservation(saleId, request.userId(), saleEvent.getEndTime())) {
            return JoinSaleResponse.blocked("DUPLICATE_RESERVATION");
        }

        int remaining = flashSaleRedisService.tryReserveInventory(saleId, request.quantity());
        if (remaining < 0) {
            flashSaleRedisService.clearUserReservation(saleId, request.userId());
            int waitPos = flashSaleRedisService.waitListPosition().intValue();
            return JoinSaleResponse.soldOut(waitPos);
        }

        LocalDateTime expiresAt = now.plusMinutes(properties.getOrder().getExpiryMinutes());
        String orderRef = UUID.randomUUID().toString();

        EnqueuedOrderMessage message = new EnqueuedOrderMessage(
                orderRef,
                saleId,
                request.userId(),
                request.quantity(),
                now,
                expiresAt
        );

        try {
            flashSaleRedisService.enqueue(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ex) {
            flashSaleRedisService.releaseInventory(saleId, request.quantity());
            flashSaleRedisService.clearUserReservation(saleId, request.userId());
            throw new BusinessException("Failed to queue reservation");
        }

        flashSaleRedisService.setOrderShadowState(orderRef, OrderStatus.QUEUED.name(), Duration.ofMinutes(30));

        String paymentUrl = "/pay/" + orderRef;
        return JoinSaleResponse.success(orderRef, expiresAt, paymentUrl);
    }

    @Transactional
    public void processQueueMessage(EnqueuedOrderMessage message) {
        if (purchaseOrderRepository.existsByOrderRef(message.orderId())) {
            return;
        }

        SaleEvent saleEvent = saleEventRepository.findById(message.saleId())
                .orElseThrow(() -> new BusinessException("Sale event not found while processing queue"));

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderRef(message.orderId());
        order.setSaleEvent(saleEvent);
        order.setUserId(message.userId());
        order.setQuantity(message.quantity());
        order.setStatus(OrderStatus.PENDING);
        order.setUnitPrice(saleEvent.getSalePrice());
        order.setTotalAmount(saleEvent.getSalePrice().multiply(BigDecimal.valueOf(message.quantity())));
        order.setReservedAt(message.reservedAt());
        order.setPurchasedAt(message.reservedAt());
        order.setExpiresAt(message.expiresAt());

        purchaseOrderRepository.save(order);
        flashSaleRedisService.setOrderShadowState(message.orderId(), OrderStatus.PENDING.name(), Duration.ofMinutes(30));
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse getOrder(String orderRef) {
        PurchaseOrder order = purchaseOrderRepository.findByOrderRef(orderRef)
                .orElse(null);

        if (order == null) {
            String queuedState = flashSaleRedisService.getOrderShadowState(orderRef);
            if (queuedState != null) {
                return new OrderStatusResponse(orderRef, null, null, null, OrderStatus.valueOf(queuedState), null, null, null, null);
            }
            throw new BusinessException("Order not found");
        }

        return new OrderStatusResponse(
                order.getOrderRef(),
                order.getSaleEvent().getId(),
                order.getUserId(),
                order.getQuantity(),
                order.getStatus(),
                order.getReservedAt(),
                order.getExpiresAt(),
                order.getPaidAt(),
                "/pay/" + order.getOrderRef()
        );
    }

    @Transactional
    public PayOrderResponse payOrder(String orderRef, PayOrderRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByOrderRef(orderRef)
                .orElseThrow(() -> new BusinessException("Order not found"));

        if (!order.getUserId().equals(request.userId())) {
            throw new BusinessException("ORDER_USER_MISMATCH");
        }

        if (order.getStatus() == OrderStatus.PAID) {
            return new PayOrderResponse(order.getOrderRef(), OrderStatus.PAID.name(), order.getPaidAt());
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("ORDER_NOT_PAYABLE");
        }

        if (LocalDateTime.now().isAfter(order.getExpiresAt())) {
            expireSingleOrder(order);
            throw new BusinessException("ORDER_EXPIRED");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setPaymentReference(request.paymentReference());
        purchaseOrderRepository.save(order);
        flashSaleRedisService.setOrderShadowState(orderRef, OrderStatus.PAID.name(), Duration.ofMinutes(30));

        return new PayOrderResponse(orderRef, OrderStatus.PAID.name(), order.getPaidAt());
    }

    @Transactional
    public int expirePendingOrders() {
        int expiredCount = 0;
        for (PurchaseOrder order : purchaseOrderRepository.findExpiredOrders(OrderStatus.PENDING, LocalDateTime.now())) {
            expireSingleOrder(order);
            expiredCount++;
        }
        return expiredCount;
    }

    private void expireSingleOrder(PurchaseOrder order) {
        order.setStatus(OrderStatus.EXPIRED);
        purchaseOrderRepository.save(order);
        flashSaleRedisService.releaseInventory(order.getSaleEvent().getId(), order.getQuantity());
        flashSaleRedisService.clearUserReservation(order.getSaleEvent().getId(), order.getUserId());
        flashSaleRedisService.setOrderShadowState(order.getOrderRef(), OrderStatus.EXPIRED.name(), Duration.ofMinutes(30));
    }
}
