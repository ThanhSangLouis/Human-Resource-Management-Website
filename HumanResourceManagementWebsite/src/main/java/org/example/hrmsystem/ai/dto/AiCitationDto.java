package org.example.hrmsystem.ai.dto;

public class AiCitationDto {

    private String source;
    private String excerpt;

    public AiCitationDto() {
    }

    public AiCitationDto(String source, String excerpt) {
        this.source = source;
        this.excerpt = excerpt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }
}
