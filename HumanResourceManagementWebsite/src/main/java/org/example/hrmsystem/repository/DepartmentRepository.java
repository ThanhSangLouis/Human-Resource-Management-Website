package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByManagerId(Long managerId);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Page<Department> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
            SELECT d FROM Department d
            WHERE d.id IN :ids
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Department> findByIdInWithOptionalKeyword(
            @Param("ids") Collection<Long> ids,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
