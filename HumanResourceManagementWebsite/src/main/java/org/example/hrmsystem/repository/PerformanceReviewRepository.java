package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.PerformanceReview;
import org.example.hrmsystem.model.PerformanceReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {

    boolean existsByEmployeeIdAndReviewYearAndReviewQuarter(Long employeeId, Integer year, Integer quarter);

    Optional<PerformanceReview> findByEmployeeIdAndReviewYearAndReviewQuarter(
            Long employeeId, Integer year, Integer quarter);

    Page<PerformanceReview> findByReviewYearAndReviewQuarter(Integer year, Integer quarter, Pageable pageable);

    Page<PerformanceReview> findByReviewYear(Integer year, Pageable pageable);

    Page<PerformanceReview> findByStatus(PerformanceReviewStatus status, Pageable pageable);

    Page<PerformanceReview> findByEmployeeIdIn(Collection<Long> employeeIds, Pageable pageable);

    Page<PerformanceReview> findByEmployeeIdInAndReviewYearAndReviewQuarter(
            Collection<Long> employeeIds,
            Integer year,
            Integer quarter,
            Pageable pageable
    );

    Page<PerformanceReview> findByEmployeeIdInAndReviewYear(
            Collection<Long> employeeIds,
            Integer year,
            Pageable pageable
    );
}
