package com.military.ams.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.entity.Personnel;

public interface PersonnelRepository extends JpaRepository<Personnel, Long> {

    Optional<Personnel> findByServiceNumber(String serviceNumber);

    boolean existsByServiceNumber(String serviceNumber);

    boolean existsByServiceNumberAndIdNot(String serviceNumber, Long id);

    boolean existsByBaseId(Long baseId);

    List<Personnel> findByBaseIdOrderByFullNameAsc(Long baseId);

    @Query("""
            select p from Personnel p
            where (:baseId is null or p.base.id = :baseId)
              and (:q is null or lower(p.fullName) like lower(concat('%', :q, '%'))
                                 or lower(p.serviceNumber) like lower(concat('%', :q, '%')))
            order by p.fullName asc
            """)
    Page<Personnel> search(@Param("baseId") Long baseId, @Param("q") String q, Pageable pageable);
}
