package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.LeaveRequest;
import org.example.hrmsystem.model.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.example.hrmsystem.model.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Khóa hàng khi duyệt/từ chối để tránh race (spam click → trùng sync attendance). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    Page<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByStatusAndEmployeeIdInOrderByCreatedAtDesc(
            LeaveStatus status,
            Collection<Long> employeeIds,
            Pageable pageable
    );

    /**
     * Đơn chờ duyệt của những người có tài khoản với role thuộc danh sách (JOIN users.employee_id).
     */
    @Query("""
            SELECT lr FROM LeaveRequest lr
            JOIN UserAccount ua ON ua.employeeId = lr.employeeId
            WHERE lr.status = :status AND ua.role IN :roles
            """)
    Page<LeaveRequest> findByStatusAndApplicantAccountRolesIn(
            @Param("status") LeaveStatus status,
            @Param("roles") Collection<Role> roles,
            Pageable pageable
    );

    boolean existsByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId,
            java.util.List<LeaveStatus> statuses,
            LocalDate endDate,
            LocalDate startDate
    );

    boolean existsByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId,
            LeaveStatus status,
            LocalDate dateEnd,
            LocalDate dateStart
    );

    List<LeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LeaveStatus status,
            LocalDate endBound,
            LocalDate startBound
    );
}
