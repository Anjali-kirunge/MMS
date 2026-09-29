package com.military.ams.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.dto.MovementAggregate;
import com.military.ams.entity.Expenditure;

public interface ExpenditureRepository extends JpaRepository<Expenditure, Long> {

    @Query("""
            select e from Expenditure e
            where (:baseId is null or e.base.id = :baseId)
              and (:equipmentTypeId is null or e.equipmentType.id = :equipmentTypeId)
              and (:from is null or e.expendedDate >= :from)
              and (:to is null or e.expendedDate <= :to)
            order by e.expendedDate desc, e.id desc
            """)
    Page<Expenditure> search(@Param("baseId") Long baseId,
                             @Param("equipmentTypeId") Long equipmentTypeId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             Pageable pageable);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(e.base.id, e.equipmentType.id, coalesce(sum(e.quantity), 0))
            from Expenditure e
            where (:baseId is null or e.base.id = :baseId)
              and (:equipmentTypeId is null or e.equipmentType.id = :equipmentTypeId)
              and e.expendedDate between :from and :to
            group by e.base.id, e.equipmentType.id
            """)
    List<MovementAggregate> aggregateBetween(@Param("baseId") Long baseId,
                                            @Param("equipmentTypeId") Long equipmentTypeId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(e.base.id, e.equipmentType.id, coalesce(sum(e.quantity), 0))
            from Expenditure e
            where (:baseId is null or e.base.id = :baseId)
              and (:equipmentTypeId is null or e.equipmentType.id = :equipmentTypeId)
              and e.expendedDate < :before
            group by e.base.id, e.equipmentType.id
            """)
    List<MovementAggregate> aggregateBefore(@Param("baseId") Long baseId,
                                           @Param("equipmentTypeId") Long equipmentTypeId,
                                           @Param("before") LocalDate before);
}
