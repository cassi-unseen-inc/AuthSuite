package net.authsuite.common.provider;

import java.util.Objects;

/**
 * Classifies provider failures so that fallthrough behavior cannot accidentally
 * convert provider outages into identity changes.
 * <p>
 * The mod distinguishes these error classes explicitly (spec §5):
 * <ul>
 *   <li>{@link ErrorClass#ACCOUNT_NOT_FOUND} - the account does not exist / is not valid on this provider</li>
 *   <li>{@link ErrorClass#AUTHENTICATION_FAILED} - credentials or session rejected by the provider</li>
 *   <li>{@link ErrorClass#RATE_LIMITED} - provider rate limitation</li>
 *   <li>{@link ErrorClass#UNAVAILABLE} - provider outage / network unreachable / timeout</li>
 *   <li>{@link ErrorClass#INVALID_ATTEMPT} - malformed request, missing native context, or policy violation</li>
 * </ul>
 */
public record ProviderFailure(ProviderId provider, ErrorClass errorClass, String message) {

    public enum ErrorClass {
        ACCOUNT_NOT_FOUND,
        AUTHENTICATION_FAILED,
        RATE_LIMITED,
        UNAVAILABLE,
        INVALID_ATTEMPT
    }

    public ProviderFailure {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(errorClass, "errorClass");
    }

    /**
     * Whether a failure of this class justifies trying the next provider in a
     * fallthrough chain. Real credential/auth failures short-circuit so a
     * mis-typed password on one provider does not silently hand the login to a
     * different provider.
     */
    public boolean isFallthroughEligible() {
        return switch (errorClass) {
            case ACCOUNT_NOT_FOUND, RATE_LIMITED, UNAVAILABLE -> true;
            case AUTHENTICATION_FAILED, INVALID_ATTEMPT -> false;
        };
    }

    @Override
    public String toString() {
        return provider.shortcode() + ":" + errorClass;
    }

    public static ProviderFailure accountNotFound(ProviderId provider, String message) {
        return new ProviderFailure(provider, ErrorClass.ACCOUNT_NOT_FOUND, message);
    }

    public static ProviderFailure authFailed(ProviderId provider, String message) {
        return new ProviderFailure(provider, ErrorClass.AUTHENTICATION_FAILED, message);
    }

    public static ProviderFailure rateLimited(ProviderId provider, String message) {
        return new ProviderFailure(provider, ErrorClass.RATE_LIMITED, message);
    }

    public static ProviderFailure unavailable(ProviderId provider, String message) {
        return new ProviderFailure(provider, ErrorClass.UNAVAILABLE, message);
    }

    public static ProviderFailure invalidAttempt(ProviderId provider, String message) {
        return new ProviderFailure(provider, ErrorClass.INVALID_ATTEMPT, message);
    }
}