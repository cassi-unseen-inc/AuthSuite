package net.authsuite.neoforge.login;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.identity.CanonicalUuid;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.identity.ProviderIdentity;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.common.provider.AuthenticatedProfile;
import net.authsuite.common.provider.AuthAttempt;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.common.provider.ProviderManager;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.common.skin.SkinResource;
import net.authsuite.neoforge.skin.SkinBroadcaster;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Builds a canonical {@link GameProfile} and {@link ProfileResult} from a
 * provider-validated {@link AuthenticatedProfile}.
 * <p>
 * The canonical Minecraft UUID is always derived from the immutable
 * {@code providerId + providerAccountId} pair (spec §9) - never from a
 * client-supplied username or UUID. Provider textures are copied onto the profile
 * as a base64 {@code textures} property so vanilla clients render the provider's
 * skin without any client mod.
 */
public final class LoginProfileBuilder {

    private final IdentityRegistry identityRegistry;
    private final ProviderManager providerManager;
    private final AuthResolver resolver;
    private final AuthSuiteLogger log;
    private SkinBroadcaster skinBroadcaster;

    public LoginProfileBuilder(IdentityRegistry identityRegistry, ProviderManager providerManager,
                               AuthResolver resolver, AuthSuiteLogger log) {
        this.identityRegistry = identityRegistry;
        this.providerManager = providerManager;
        this.resolver = resolver;
        this.log = log;
    }

    public void setSkinBroadcaster(SkinBroadcaster skinBroadcaster) {
        this.skinBroadcaster = skinBroadcaster;
    }

    /** Blocking resolve used by the login handler (never on the server main thread). */
    public AuthResolver.Resolution resolveBlocking(String username, String serverId, InetAddress address, long timeout) {
        AuthAttempt attempt = new AuthAttempt(username, serverId, address, false);
        try {
            return resolver.resolve(attempt, null, null).get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Login resolution interrupted for '{}'", username);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Login resolution for '{}' failed: {}", username,
                    net.authsuite.common.security.LogSanitizer.sanitizeMessage(e.getCause() != null ? e.getCause() : e));
            return null;
        }
    }

    public ProfileResult buildProfileResult(AuthResolver.Resolution resolution, String username, String serverId) {
        AuthenticatedProfile profile = resolution.profile();
        UUID canonical = CanonicalUuid.from(profile.provider().providerId(), profile.providerAccountId());

        ProviderIdentity providerIdentity = new ProviderIdentity(
                profile.provider(),
                profile.providerAccountId(),
                profile.username(),
                profile.providerProfileUuid(),
                canonical,
                skinMetadata(profile));

        HybridIdentity hybrid = HybridIdentity.from(providerIdentity, serverId, providerIdentity.metadata().toString());
        boolean registered = identityRegistry.register(hybrid, serverId, false);
        if (!registered) {
            log.info("Refusing duplicate login: canonical identity {} already active", canonical);
            return null;
        }

        GameProfile gameProfile = new GameProfile(canonical, profile.username());
        for (Map.Entry<String, String> entry : profile.textures().entrySet()) {
            gameProfile.getProperties().put(entry.getKey(), new Property(entry.getKey(), entry.getValue()));
        }
        // Never let the client dictate chat-key or uploadable-texture properties.
        gameProfile.getProperties().removeAll("profilePublicKey");
        gameProfile.getProperties().removeAll("uploadableTextures");

        log.info("Authenticated '{}' via {} as {} (canonical {})",
                username, profile.provider().shortcode(), profile.safeDescription(), canonical);
        if (skinBroadcaster != null) {
            skinBroadcaster.cache(buildDirective(profile));
        }
        return new ProfileResult(gameProfile, Set.of());
    }

    /** Capture the authoritative skin directive for the login event + client. */
    public SkinDirective buildDirective(AuthenticatedProfile profile) {
        UUID canonical = CanonicalUuid.from(profile.provider().providerId(), profile.providerAccountId());
        return new SkinDirective(canonical, profile.provider().providerId(),
                skinResource(profile, "skin"), capeResource(profile), modelType(profile), 1);
    }

    private Map<String, String> skinMetadata(AuthenticatedProfile profile) {
        return Map.of(
                "skin", skinUrl(profile),
                "cape", capeUrl(profile),
                "model", modelType(profile));
    }

    private SkinResource skinResource(AuthenticatedProfile profile, String key) {
        String url = skinUrl(profile);
        return url == null || url.isBlank() ? null : new SkinResource(url);
    }

    private SkinResource capeResource(AuthenticatedProfile profile) {
        String url = capeUrl(profile);
        return url == null || url.isBlank() ? null : new SkinResource(url);
    }

    private String skinUrl(AuthenticatedProfile profile) {
        return extractTextureUrl(profile, "skin");
    }

    private String capeUrl(AuthenticatedProfile profile) {
        return extractTextureUrl(profile, "cape");
    }

    private String extractTextureUrl(AuthenticatedProfile profile, String kind) {
        // Provider responses embed a base64 'textures' property (name -> base64 value).
        String textures = profile.textures().get("textures");
        if (textures == null || textures.isBlank()) {
            return null;
        }
        try {
            String json = new String(java.util.Base64.getDecoder().decode(textures), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return null;
            }
            com.google.gson.JsonObject root = element.getAsJsonObject();
            if (!root.has("textures") || !root.get("textures").isJsonObject()) {
                return null;
            }
            com.google.gson.JsonObject texturesObj = root.getAsJsonObject("textures");
            com.google.gson.JsonElement target = texturesObj.has(kind.toUpperCase())
                    ? texturesObj.get(kind.toUpperCase())
                    : null;
            if (target == null || !target.isJsonObject()) {
                return null;
            }
            com.google.gson.JsonObject targetObj = target.getAsJsonObject();
            return targetObj.has("url") ? targetObj.get("url").getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String modelType(AuthenticatedProfile profile) {
        String textures = profile.textures().get("textures");
        if (textures == null || textures.isBlank()) {
            return "classic";
        }
        try {
            String json = new String(java.util.Base64.getDecoder().decode(textures), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(json);
            if (element.isJsonObject()
                    && element.getAsJsonObject().has("textures")
                    && element.getAsJsonObject().get("textures").isJsonObject()) {
                com.google.gson.JsonObject texturesObj = element.getAsJsonObject().getAsJsonObject("textures");
                if (texturesObj.has("SKIN") && texturesObj.get("SKIN").isJsonObject()) {
                    com.google.gson.JsonObject skin = texturesObj.getAsJsonObject("SKIN");
                    if (skin.has("metadata") && skin.get("metadata").isJsonObject()
                            && skin.getAsJsonObject("metadata").has("model")) {
                        return skin.getAsJsonObject("metadata").get("model").getAsString();
                    }
                }
            }
            return "classic";
        } catch (RuntimeException e) {
            return "classic";
        }
    }
}