package org.example.hrmsystem.dto;

import org.example.hrmsystem.model.PerformanceReviewStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class PerformanceReviewResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long reviewerId;
    private Integer reviewYear;
    private Integer reviewQuarter;
    private Integer score;
    private String reviewComment;
    private PerformanceReviewStatus status;
    private LocalDate reviewDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
