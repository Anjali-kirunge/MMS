package com.military.ams.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.entity.AuditLog;

/**
 * Append-only repository. No update or delete operations are exposed.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:entity is null or a.entity = :entity)
              and (:action is null or a.action = :action)
              and (:userId is null or a.userId = :userId)
              and (:baseId is null or a.base.id = :baseId)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt <= :to)
            order by a.createdAt desc, a.id desc
            """)
    Page<AuditLog> search(@Param("entity") String entity,
                          @Param("action") String action,
                          @Param("userId") Long userId,
                          @Param("baseId") Long baseId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);

    @Query("select distinct a.entity from AuditLog a order by a.entity asc")
    List<String> findDistinctEntities();
}
