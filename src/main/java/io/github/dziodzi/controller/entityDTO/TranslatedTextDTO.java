package io.github.dziodzi.controller.entityDTO;

public class TranslatedTextDTO {
    private final Long requestId;
    private final String outputText;

    public TranslatedTextDTO (Long requestId, String outputText) {
        this.requestId = requestId;
        this.outputText = outputText;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getOutputText() {
        return outputText;
    }
}
