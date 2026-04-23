package com.flashsale.repository;

import com.flashsale.entity.SaleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SaleEventRepository extends JpaRepository<SaleEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SaleEvent s where s.id = :id")
    Optional<SaleEvent> findByIdForUpdate(@Param("id") Long id);
}
