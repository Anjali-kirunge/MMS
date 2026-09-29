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
import com.military.ams.entity.Transfer;
import com.military.ams.entity.TransferItem;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    boolean existsByReferenceNo(String referenceNo);

    Optional<Transfer> findByReferenceNo(String referenceNo);

    long countByReferenceNoStartingWith(String prefix);

    @Query("""
            select t from Transfer t
            where (:baseId is null or t.sourceBase.id = :baseId or t.destinationBase.id = :baseId)
              and (:from is null or t.transferDate >= :from)
              and (:to is null or t.transferDate <= :to)
            order by t.transferDate desc, t.id desc
            """)
    Page<Transfer> search(@Param("baseId") Long baseId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          Pageable pageable);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(ti.transfer.destinationBase.id, ti.equipmentType.id, coalesce(sum(ti.quantity), 0))
            from TransferItem ti
            where (:baseId is null or ti.transfer.destinationBase.id = :baseId)
              and (:equipmentTypeId is null or ti.equipmentType.id = :equipmentTypeId)
              and ti.transfer.transferDate between :from and :to
            group by ti.transfer.destinationBase.id, ti.equipmentType.id
            """)
    List<MovementAggregate> aggregateTransferInBetween(@Param("baseId") Long baseId,
                                                      @Param("equipmentTypeId") Long equipmentTypeId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(ti.transfer.destinationBase.id, ti.equipmentType.id, coalesce(sum(ti.quantity), 0))
            from TransferItem ti
            where (:baseId is null or ti.transfer.destinationBase.id = :baseId)
              and (:equipmentTypeId is null or ti.equipmentType.id = :equipmentTypeId)
              and ti.transfer.transferDate < :before
            group by ti.transfer.destinationBase.id, ti.equipmentType.id
            """)
    List<MovementAggregate> aggregateTransferInBefore(@Param("baseId") Long baseId,
                                                     @Param("equipmentTypeId") Long equipmentTypeId,
                                                     @Param("before") LocalDate before);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(ti.transfer.sourceBase.id, ti.equipmentType.id, coalesce(sum(ti.quantity), 0))
            from TransferItem ti
            where (:baseId is null or ti.transfer.sourceBase.id = :baseId)
              and (:equipmentTypeId is null or ti.equipmentType.id = :equipmentTypeId)
              and ti.transfer.transferDate between :from and :to
            group by ti.transfer.sourceBase.id, ti.equipmentType.id
            """)
    List<MovementAggregate> aggregateTransferOutBetween(@Param("baseId") Long baseId,
                                                       @Param("equipmentTypeId") Long equipmentTypeId,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(ti.transfer.sourceBase.id, ti.equipmentType.id, coalesce(sum(ti.quantity), 0))
            from TransferItem ti
            where (:baseId is null or ti.transfer.sourceBase.id = :baseId)
              and (:equipmentTypeId is null or ti.equipmentType.id = :equipmentTypeId)
              and ti.transfer.transferDate < :before
            group by ti.transfer.sourceBase.id, ti.equipmentType.id
            """)
    List<MovementAggregate> aggregateTransferOutBefore(@Param("baseId") Long baseId,
                                                      @Param("equipmentTypeId") Long equipmentTypeId,
                                                      @Param("before") LocalDate before);

    @Query("""
            select ti from TransferItem ti
            where ti.transfer.destinationBase.id = :baseId
              and (:from is null or ti.transfer.transferDate >= :from)
              and (:to is null or ti.transfer.transferDate <= :to)
              and (:equipmentTypeId is null or ti.equipmentType.id = :equipmentTypeId)
            order by ti.transfer.transferDate desc, ti.id desc
            """)
    List<TransferItem> findItemsReceivedBy(@Param("baseId") Long baseId,
                                           @Param("equipmentTypeId") Long equipmentTypeId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query("""
            select ti from TransferItem ti
            where ti.transfer.sourceBase.id = :baseId
              and (:from is null or ti.transfer.transferDate >= :from)
              and (:to is null or ti.transfer.transferDate <= :to)
              and (:equipmentTypeId is null or ti.equipmentType.id = :equipmentTypeId)
            order by ti.transfer.transferDate desc, ti.id desc
            """)
    List<TransferItem> findItemsSentBy(@Param("baseId") Long baseId,
                                       @Param("equipmentTypeId") Long equipmentTypeId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
