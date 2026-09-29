package com.military.ams.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.entity.EquipmentType;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Long> {

    Optional<EquipmentType> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<EquipmentType> findAllByOrderByNameAsc();

    @Query("""
            select e from EquipmentType e
            where (:q is null or lower(e.name) like lower(concat('%', :q, '%'))
                                 or lower(e.code) like lower(concat('%', :q, '%')))
            order by e.name asc
            """)
    Page<EquipmentType> search(@Param("q") String q, Pageable pageable);
}
