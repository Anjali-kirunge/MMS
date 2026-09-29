package com.military.ams.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.entity.StockBalance;

import jakarta.persistence.LockModeType;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    Optional<StockBalance> findByBaseIdAndEquipmentTypeId(Long baseId, Long equipmentTypeId);

    boolean existsByBaseId(Long baseId);

    boolean existsByEquipmentTypeId(Long equipmentTypeId);

    /**
     * Acquires a row-level write lock so concurrent stock movements at the same
     * base/equipment pair are serialised. Callers must run inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sb from StockBalance sb where sb.base.id = :baseId and sb.equipmentType.id = :equipmentTypeId")
    Optional<StockBalance> findForUpdate(@Param("baseId") Long baseId, @Param("equipmentTypeId") Long equipmentTypeId);

    @Query("""
            select sb from StockBalance sb
            where (:baseId is null or sb.base.id = :baseId)
              and (:equipmentTypeId is null or sb.equipmentType.id = :equipmentTypeId)
            order by sb.base.name asc, sb.equipmentType.name asc
            """)
    Page<StockBalance> search(@Param("baseId") Long baseId,
                              @Param("equipmentTypeId") Long equipmentTypeId,
                              Pageable pageable);
}
