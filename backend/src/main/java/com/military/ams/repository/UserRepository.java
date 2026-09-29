package com.military.ams.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.military.ams.entity.AppUser;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByBaseId(Long baseId);

    @Query("""
            select u from AppUser u
            where (:role is null or u.role.name = :role)
              and (:baseId is null or u.base.id = :baseId)
            order by u.username asc
            """)
    Page<AppUser> search(@Param("role") String role, @Param("baseId") Long baseId, Pageable pageable);
}
