package org.example.hrmsystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PerformanceReviewCreateRequest {

    @NotNull
    private Long employeeId;

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer reviewYear;

    @NotNull
    @Min(1)
    @Max(4)
    private Integer reviewQuarter;

    @Min(0)
    @Max(100)
    private Integer score;

    private String reviewComment;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
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
}
