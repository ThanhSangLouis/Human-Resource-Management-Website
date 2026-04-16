package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.LeaveRequest;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.model.LeaveType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Page<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveStatus status, Pageable pageable);

    /** Đơn chờ duyệt, loại trừ đơn của chính người xem (Admin/HR không tự duyệt đơn của mình). */
    Page<LeaveRequest> findByStatusAndEmployeeIdNotOrderByCreatedAtDesc(
            LeaveStatus status,
            Long excludeEmployeeId,
            Pageable pageable);

    Page<LeaveRequest> findByStatusAndEmployeeIdInOrderByCreatedAtDesc(
            LeaveStatus status,
            Collection<Long> employeeIds,
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

    /**
     * Số đơn APPROVED mà ngày {@code refDate} nằm trong [startDate, endDate] (hai cận gồm cả ngày đó).
     * Dùng cùng một quy tắc với danh sách đơn đã duyệt (theo ngày, không có giờ trong DB).
     */
    @Query("""
            SELECT COUNT(lr) FROM LeaveRequest lr
            WHERE lr.employeeId = :employeeId
              AND lr.status = :approved
              AND lr.startDate <= :refDate
              AND lr.endDate >= :refDate
            """)
    long countApprovedLeavesCoveringDate(
            @Param("employeeId") Long employeeId,
            @Param("approved") LeaveStatus approved,
            @Param("refDate") LocalDate refDate
    );

    List<LeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LeaveStatus status,
            LocalDate endBound,
            LocalDate startBound
    );

    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.status = :approved
            AND lr.startDate <= :rangeEnd
            AND lr.endDate >= :rangeStart
            AND lr.employeeId = :employeeId
            ORDER BY lr.createdAt DESC
            """)
    Page<LeaveRequest> findApprovedOverlappingForEmployee(
            @Param("approved") LeaveStatus approved,
            @Param("employeeId") Long employeeId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd,
            Pageable pageable
    );

    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.status = :approved
            AND lr.startDate <= :rangeEnd
            AND lr.endDate >= :rangeStart
            AND lr.employeeId IN :employeeIds
            ORDER BY lr.createdAt DESC
            """)
    Page<LeaveRequest> findApprovedOverlappingForEmployeeIds(
            @Param("approved") LeaveStatus approved,
            @Param("employeeIds") Collection<Long> employeeIds,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd,
            Pageable pageable
    );

    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.status = :approved
            AND lr.startDate <= :rangeEnd
            AND lr.endDate >= :rangeStart
            ORDER BY lr.createdAt DESC
            """)
    Page<LeaveRequest> findApprovedOverlappingAll(
            @Param("approved") LeaveStatus approved,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd,
            Pageable pageable
    );

    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.status = :approved
            AND lr.startDate <= :rangeEnd
            AND lr.endDate >= :rangeStart
            AND lr.employeeId IN (SELECT e.id FROM Employee e WHERE e.departmentId = :deptId)
            ORDER BY lr.createdAt DESC
            """)
    Page<LeaveRequest> findApprovedOverlappingForDepartment(
            @Param("approved") LeaveStatus approved,
            @Param("deptId") Long deptId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd,
            Pageable pageable
    );

    /**
     * Tổng ngày phép năm (ANNUAL) đã duyệt, giao với khoảng [yearStart, yearEnd] (thường cả năm dương lịch).
     */
    @Query("""
            SELECT COALESCE(SUM(lr.totalDays), 0) FROM LeaveRequest lr
            WHERE lr.employeeId = :employeeId
              AND lr.status = :approved
              AND lr.leaveType = :annualType
              AND lr.startDate <= :yearEnd
              AND lr.endDate >= :yearStart
            """)
    Long sumApprovedAnnualDaysOverlappingRange(
            @Param("employeeId") Long employeeId,
            @Param("approved") LeaveStatus approved,
            @Param("annualType") LeaveType annualType,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd
    );

    /**
     * Tổng ngày phép đã duyệt theo loại (ANNUAL/UNPAID/SICK/...) giao với khoảng [rangeStart, rangeEnd].
     */
    @Query("""
            SELECT COALESCE(SUM(lr.totalDays), 0) FROM LeaveRequest lr
            WHERE lr.employeeId = :employeeId
              AND lr.status = :approved
              AND lr.leaveType = :leaveType
              AND lr.startDate <= :rangeEnd
              AND lr.endDate >= :rangeStart
            """)
    Long sumApprovedDaysOverlappingRangeByType(
            @Param("employeeId") Long employeeId,
            @Param("approved") LeaveStatus approved,
            @Param("leaveType") LeaveType leaveType,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );
}
