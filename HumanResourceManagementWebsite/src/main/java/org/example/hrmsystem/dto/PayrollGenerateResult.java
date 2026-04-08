package org.example.hrmsystem.dto;

import java.util.List;

public class PayrollGenerateResult {

    private final String month;
    private final int created;
    private final int skippedAlreadyExists;
    private final List<String> messages;

    public PayrollGenerateResult(String month, int created, int skippedAlreadyExists, List<String> messages) {
        this.month = month;
        this.created = created;
        this.skippedAlreadyExists = skippedAlreadyExists;
        this.messages = messages;
    }

    public String getMonth() {
        return month;
    }

    public int getCreated() {
        return created;
    }

    public int getSkippedAlreadyExists() {
        return skippedAlreadyExists;
    }

    public List<String> getMessages() {
        return messages;
    }
}
