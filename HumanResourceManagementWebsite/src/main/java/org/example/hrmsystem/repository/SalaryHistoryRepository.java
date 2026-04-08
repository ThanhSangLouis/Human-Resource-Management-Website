package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.SalaryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {

    boolean existsByEmployeeIdAndSalaryMonth(Long employeeId, LocalDate salaryMonth);

    Page<SalaryHistory> findBySalaryMonth(LocalDate salaryMonth, Pageable pageable);

    java.util.List<SalaryHistory> findBySalaryMonthOrderByEmployeeIdAsc(LocalDate salaryMonth);
}
