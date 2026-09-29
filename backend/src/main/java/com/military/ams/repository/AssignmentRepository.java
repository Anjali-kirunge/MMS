package com.military.ams.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.dto.MovementAggregate;
import com.military.ams.entity.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("""
            select a from Assignment a
            where (:baseId is null or a.base.id = :baseId)
              and (:equipmentTypeId is null or a.equipmentType.id = :equipmentTypeId)
              and (:personnelId is null or a.personnel.id = :personnelId)
              and (:from is null or a.assignedDate >= :from)
              and (:to is null or a.assignedDate <= :to)
            order by a.assignedDate desc, a.id desc
            """)
    Page<Assignment> search(@Param("baseId") Long baseId,
                            @Param("equipmentTypeId") Long equipmentTypeId,
                            @Param("personnelId") Long personnelId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            Pageable pageable);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(a.base.id, a.equipmentType.id, coalesce(sum(a.quantity), 0))
            from Assignment a
            where (:baseId is null or a.base.id = :baseId)
              and (:equipmentTypeId is null or a.equipmentType.id = :equipmentTypeId)
              and a.assignedDate between :from and :to
            group by a.base.id, a.equipmentType.id
            """)
    List<MovementAggregate> aggregateBetween(@Param("baseId") Long baseId,
                                            @Param("equipmentTypeId") Long equipmentTypeId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("""
            select new com.military.ams.dto.MovementAggregate(a.base.id, a.equipmentType.id, coalesce(sum(a.quantity), 0))
            from Assignment a
            where (:baseId is null or a.base.id = :baseId)
              and (:equipmentTypeId is null or a.equipmentType.id = :equipmentTypeId)
              and a.assignedDate < :before
            group by a.base.id, a.equipmentType.id
            """)
    List<MovementAggregate> aggregateBefore(@Param("baseId") Long baseId,
                                           @Param("equipmentTypeId") Long equipmentTypeId,
                                           @Param("before") LocalDate before);
}
