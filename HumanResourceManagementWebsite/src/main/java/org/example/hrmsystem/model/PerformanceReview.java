package org.example.hrmsystem.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "performance_reviews")
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "review_year", nullable = false)
    private Integer reviewYear;

    @Column(name = "review_quarter", nullable = false)
    private Integer reviewQuarter;

    private Integer score;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerformanceReviewStatus status = PerformanceReviewStatus.DRAFT;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public Integer getReviewYear() {
        return reviewYear;
    }

    public void setReviewYear(Integer reviewYear) {
        this.reviewYear = reviewYear;
    }

    public Integer getReviewQuarter() {
        return reviewQuarter;
    }

    public void setReviewQuarter(Integer reviewQuarter) {
        this.reviewQuarter = reviewQuarter;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public PerformanceReviewStatus getStatus() {
        return status;
    }

    public void setStatus(PerformanceReviewStatus status) {
        this.status = status;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
