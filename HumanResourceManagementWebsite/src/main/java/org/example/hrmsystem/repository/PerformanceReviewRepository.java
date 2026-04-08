package org.example.hrmsystem.repository;

import org.example.hrmsystem.model.PerformanceReview;
import org.example.hrmsystem.model.PerformanceReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {

    boolean existsByEmployeeIdAndReviewYearAndReviewQuarter(Long employeeId, Integer year, Integer quarter);

    Optional<PerformanceReview> findByEmployeeIdAndReviewYearAndReviewQuarter(
            Long employeeId, Integer year, Integer quarter);

    Page<PerformanceReview> findByReviewYearAndReviewQuarter(Integer year, Integer quarter, Pageable pageable);

    Page<PerformanceReview> findByReviewYear(Integer year, Pageable pageable);

    Page<PerformanceReview> findByStatus(PerformanceReviewStatus status, Pageable pageable);
}
