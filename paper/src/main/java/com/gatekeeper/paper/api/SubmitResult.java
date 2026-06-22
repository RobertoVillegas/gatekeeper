package com.gatekeeper.paper.api;

/**
 * Result of a submit-application call.
 */
public record SubmitResult(boolean success, Long applicationId, String error) {

    public static SubmitResult ok(Long id) {
        return new SubmitResult(true, id, null);
    }

    public static SubmitResult fail(String error) {
        return new SubmitResult(false, null, error);
    }
}
