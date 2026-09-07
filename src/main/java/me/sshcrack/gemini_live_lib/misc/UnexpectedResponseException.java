package me.sshcrack.gemini_live_lib.misc;

public class UnexpectedResponseException extends Exception {
    private final int statusCode;
    private final String responseBody;

    public UnexpectedResponseException(String message) {
        this(message, -1, null);
    }

    public UnexpectedResponseException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /** HTTP status when the failure came directly from an API response, or {@code -1} otherwise. */
    public int getStatusCode() {
        return statusCode;
    }

    /** Raw provider response body when available. Callers should avoid logging it indiscriminately. */
    public String getResponseBody() {
        return responseBody;
    }
}
