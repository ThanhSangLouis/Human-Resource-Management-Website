package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByDepartmentIdIn(Collection<Long> departmentIds);

    List<Employee> findByStatus(EmployeeStatus status);

    long countByStatus(EmployeeStatus status);

    /** Số nhân viên theo từng phòng ban (chỉ các nhân viên có department_id). */
    @Query("SELECT e.departmentId, COUNT(e) FROM Employee e WHERE e.departmentId IS NOT NULL GROUP BY e.departmentId")
    List<Object[]> countGroupByDepartment();

    // ── Unique checks ────────────────────────────────────────────────────────

    boolean existsByEmployeeCode(String employeeCode);
    boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);

    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);

    // ── Search + Filter + Pagination ─────────────────────────────────────────

    /**
     * Tìm kiếm theo keyword (fullName / email / employeeCode),
     * lọc theo status và departmentId (nullable).
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE
              (:keyword IS NULL OR :keyword = '' OR
               LOWER(e.fullName)     LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(e.email)        LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(e.phone)        LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(e.position)     LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:status IS NULL OR e.status = :status)
            AND (:departmentId IS NULL OR e.departmentId = :departmentId)
            """)
    Page<Employee> search(
            @Param("keyword")      String keyword,
            @Param("status")       EmployeeStatus status,
            @Param("departmentId") Long departmentId,
            Pageable pageable
    );
}
