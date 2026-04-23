package com.flashsale.repository;

import com.flashsale.entity.PurchaseOrder;
import com.flashsale.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByOrderRef(String orderRef);

    boolean existsByOrderRef(String orderRef);

    @Query("select o from PurchaseOrder o where o.status = :status and o.expiresAt <= :now")
    List<PurchaseOrder> findExpiredOrders(@Param("status") OrderStatus status, @Param("now") LocalDateTime now);

    @Query("select coalesce(sum(o.quantity), 0) from PurchaseOrder o where o.saleEvent.id = :saleEventId and o.userId = :userId and o.status in ('PENDING', 'PAID')")
    int getTotalPurchasedByUser(@Param("saleEventId") Long saleEventId, @Param("userId") Long userId);
}
