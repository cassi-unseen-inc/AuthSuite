package net.authsuite.common.skin;

import java.net.URI;
import java.util.Objects;

/**
 * A skin or cape resource reference belonging to an authenticated provider.
 * <p>
 * The client must never be instructed to fetch arbitrary server-supplied URLs;
 * every resource is validated against the provider allowlist before a fetch can
 * even be considered (spec §4).
 */
public record SkinResource(String url) {

    public SkinResource {
        Objects.requireNonNull(url, "url");
    }

    public URI uri() {
        return URI.create(url);
    }
}