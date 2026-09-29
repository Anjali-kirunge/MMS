package com.military.ams.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.military.ams.entity.MilitaryBase;

public interface BaseRepository extends JpaRepository<MilitaryBase, Long> {

    Optional<MilitaryBase> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<MilitaryBase> findAllByOrderByNameAsc();
}
