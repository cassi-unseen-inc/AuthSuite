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
public final class SkinResource {

    private final String url;

    public SkinResource(String url) {
        Objects.requireNonNull(url, "url");
        this.url = url;
    }

    public String url() {
        return url;
    }

    public URI uri() {
        return URI.create(url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkinResource)) {
            return false;
        }
        return url.equals(((SkinResource) o).url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public String toString() {
        return "SkinResource[url=" + url + "]";
    }
}