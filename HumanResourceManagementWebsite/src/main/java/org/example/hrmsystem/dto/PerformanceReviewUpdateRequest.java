package org.example.hrmsystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Chỉnh sửa khi trạng thái DRAFT. */
public class PerformanceReviewUpdateRequest {

    @Min(0)
    @Max(100)
    private Integer score;

    private String reviewComment;

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
