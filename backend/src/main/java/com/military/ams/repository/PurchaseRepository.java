package com.military.ams.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.dto.MovementAggregate;
import com.military.ams.entity.Purchase;
import com.military.ams.entity.PurchaseItem;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByReferenceNo(String referenceNo);

    Optional<Purchase> findByReferenceNo(String referenceNo);

    long countByReferenceNoStartingWith(String prefix);

    @Query("""
            select p from Purchase p
            where (:baseId is null or p.base.id = :baseId)
              and (:from is null or p.purchaseDate >= :from)
              and (:to is null or p.purchaseDate <= :to)
            order by p.purchaseDate desc, p.id desc
            """)
    Page<Purchase> search(@Param("baseId") Long baseId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          Pageable pageable);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(pi.purchase.base.id, pi.equipmentType.id, coalesce(sum(pi.quantity), 0))
            from PurchaseItem pi
            where (:baseId is null or pi.purchase.base.id = :baseId)
              and (:equipmentTypeId is null or pi.equipmentType.id = :equipmentTypeId)
              and pi.purchase.purchaseDate between :from and :to
            group by pi.purchase.base.id, pi.equipmentType.id
            """)
    List<MovementAggregate> aggregateBetween(@Param("baseId") Long baseId,
                                            @Param("equipmentTypeId") Long equipmentTypeId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(pi.purchase.base.id, pi.equipmentType.id, coalesce(sum(pi.quantity), 0))
            from PurchaseItem pi
            where (:baseId is null or pi.purchase.base.id = :baseId)
              and (:equipmentTypeId is null or pi.equipmentType.id = :equipmentTypeId)
              and pi.purchase.purchaseDate < :before
            group by pi.purchase.base.id, pi.equipmentType.id
            """)
    List<MovementAggregate> aggregateBefore(@Param("baseId") Long baseId,
                                           @Param("equipmentTypeId") Long equipmentTypeId,
                                           @Param("before") LocalDate before);

    @Query("""
            select pi from PurchaseItem pi
            where (:baseId is null or pi.purchase.base.id = :baseId)
              and (:equipmentTypeId is null or pi.equipmentType.id = :equipmentTypeId)
              and pi.purchase.purchaseDate between :from and :to
            order by pi.purchase.purchaseDate desc, pi.id desc
            """)
    List<PurchaseItem> findItemsBetween(@Param("baseId") Long baseId,
                                        @Param("equipmentTypeId") Long equipmentTypeId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
