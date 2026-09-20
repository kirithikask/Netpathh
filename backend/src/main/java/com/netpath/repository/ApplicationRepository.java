package com.netpath.repository;

import com.netpath.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @Query("SELECT a FROM Application a LEFT JOIN FETCH a.paths WHERE a.id = :id")
    Optional<Application> findByIdWithPaths(@Param("id") Long id);
}
