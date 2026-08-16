package net.authsuite.neoforge.client;

import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.common.skin.SkinPolicy;
import net.authsuite.common.skin.SkinResource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side skin directive store. Every directive is validated against
 * {@link SkinPolicy} before it becomes eligible for rendering (spec §4).
 */
public final class ClientSkinApplier {

    private final SkinPolicy policy;
    private final Map<UUID, SkinDirective> validated = new ConcurrentHashMap<>();

    public ClientSkinApplier() {
        this.policy = new SkinPolicy(new AuthSuiteConfig(), net.authsuite.common.log.AuthSuiteLogger.noop());
    }

    public void apply(SkinDirective directive) {
        if (directive == null) {
            return;
        }
        if (directive.skinResource() == null) {
            validated.remove(directive.playerUUID());
            return;
        }
        SkinPolicy.ValidationResult result = policy.validate(directive.providerId(), directive.skinResource(), 0);
        if (!result.allowed()) {
            // A directive failing the provider policy must never reach the renderer.
            validated.remove(directive.playerUUID());
            return;
        }
        validated.put(directive.playerUUID(), directive);
    }

    public SkinDirective directiveFor(UUID playerUuid) {
        return validated.get(playerUuid);
    }

    public void reset() {
        validated.clear();
    }

    /** Resource referenced by the validated directive; policy guarantees HTTPS + allowed host. */
    public String resourceUrl(UUID playerUuid) {
        SkinDirective directive = validated.get(playerUuid);
        if (directive == null) {
            return null;
        }
        SkinResource skin = directive.skinResource();
        return skin == null ? null : skin.url();
    }

    public Map<UUID, SkinDirective> all() {
        return validated;
    }
}