package net.authsuite.common.provider;

/**
 * Wraps a {@link ProviderFailure} as an exception so async provider errors can be
 * classified through completer pipelines without losing the error class.
 */
public class ProviderFailureException extends RuntimeException {

    private final ProviderFailure failure;

    public ProviderFailureException(ProviderFailure failure) {
        super(failure == null ? "provider failure" : failure.toString());
        this.failure = failure;
    }

    public ProviderFailure failure() {
        return failure;
    }
}