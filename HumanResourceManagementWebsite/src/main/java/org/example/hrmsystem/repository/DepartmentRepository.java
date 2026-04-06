package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByManagerId(Long managerId);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Page<Department> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Native UPDATE trực tiếp xuống DB – bypass JPA L1 cache.
     * Dùng cho PATCH /api/departments/{id}/manager để đảm bảo manager_id luôn được ghi.
     */
    @Modifying
    @Query(value = "UPDATE departments SET manager_id = :managerId WHERE id = :deptId",
           nativeQuery = true)
    int updateManagerId(@Param("deptId") Long deptId,
                        @Param("managerId") Long managerId);

    /**
     * Đếm số phòng mà một nhân viên đang làm manager – dùng sau flush để check demote role.
     */
    @Query("SELECT COUNT(d) FROM Department d WHERE d.managerId = :empId")
    long countByManagerId(@Param("empId") Long empId);
}
