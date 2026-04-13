package org.example.hrmsystem.ai.dto;

import java.util.List;
import java.util.Map;

public class AiChatResponse {

    private String reply;
    private String intent;
    private List<AiCitationDto> citations;
    private Map<String, Object> dataSnapshot;
    private boolean fallback;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<AiCitationDto> getCitations() {
        return citations;
    }

    public void setCitations(List<AiCitationDto> citations) {
        this.citations = citations;
    }

    public Map<String, Object> getDataSnapshot() {
        return dataSnapshot;
    }

    public void setDataSnapshot(Map<String, Object> dataSnapshot) {
        this.dataSnapshot = dataSnapshot;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }
}
