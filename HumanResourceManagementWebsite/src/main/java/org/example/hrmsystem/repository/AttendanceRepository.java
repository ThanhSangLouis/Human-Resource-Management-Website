package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long>, JpaSpecificationExecutor<Attendance> {

    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    boolean existsByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate date);

    /** Bản ghi chấm công của nhân viên trong khoảng ngày (hai đầu inclusive). */
    List<Attendance> findByEmployeeIdAndAttendanceDateBetween(
            Long employeeId, LocalDate startInclusive, LocalDate endInclusive);

    /** Số bản ghi chấm công theo trạng thái trong khoảng ngày. */
    @Query("SELECT a.status, COUNT(a) FROM Attendance a WHERE a.attendanceDate BETWEEN :from AND :to GROUP BY a.status")
    List<Object[]> countByStatusInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Tổng số bản ghi chấm công trong khoảng ngày. */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceDate BETWEEN :from AND :to")
    long countInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
