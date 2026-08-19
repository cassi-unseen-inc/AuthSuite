package net.authsuite.forge.client;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.authsuite.common.AuthSuiteConstants;
import net.authsuite.common.client.ClientPreference;
import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.forge.network.AuthProviderPreferencePayload;
import net.authsuite.forge.network.ForgeNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side AuthSuite logic: sends the provider preference at login and applies
 * validated skin directives. All client skin fetches are gated by the skin policy
 * enforced in {@link ClientSkinApplier}.
 */
public final class AuthSuiteClient {

    private static volatile AuthSuiteClient instance;
    private final ClientSkinApplier skinApplier = new ClientSkinApplier();
    private final Map<UUID, SkinDirective> pendingDirectives = new ConcurrentHashMap<>();
    private final Map<UUID, ResourceLocation> textureLocations = new ConcurrentHashMap<>();

    private AuthSuiteClient() {
    }

    public static void init(IEventBus modEventBus) {
        if (instance != null) {
            return;
        }
        instance = new AuthSuiteClient();
        MinecraftForge.EVENT_BUS.addListener(instance::onPlayerLoggedIn);
    }

    public static AuthSuiteClient get() {
        return instance;
    }

    private void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Provider preference is advisory and arrives after the first login on 1.21.x;
        // it is bound for reconnects and identity resolution (never proof of identity).
        String preferred = ClientPreference.detect();
        PacketCodec.PreferencePayload preference = new PacketCodec.PreferencePayload(preferred, "");
        ForgeNetwork.sendToServer(new AuthProviderPreferencePayload(
                PacketCodec.encodePreference(preference.preferredProviderId(), preference.sessionHint())));
        skinApplier.reset();
        textureLocations.clear();
    }

    public static void applySkinDirective(SkinDirective directive) {
        if (instance == null) {
            return;
        }
        instance.skinApplier.apply(directive);
    }

    /** Client-side directive lookup used by PlayerInfoMixin. */
    public SkinDirective directiveFor(UUID playerUuid) {
        return skinApplier.directiveFor(playerUuid);
    }

    /**
     * Registers (and caches) the directive's provider skin with the client
     * {@code SkinManager} and returns the renderable {@code ResourceLocation}.
     * Bypasses vanilla authlib texture handling entirely, which cannot render
     * non-Mojang texture hosts in 1.20.1. Must be called on the client thread.
     */
    public ResourceLocation resolveSkinLocation(SkinDirective directive) {
        if (directive == null || directive.skinResource() == null) {
            return null;
        }
        return textureLocations.computeIfAbsent(directive.playerUUID(), uuid -> {
            Map<String, String> metadata = new HashMap<>();
            if (directive.modelType() != null && !directive.modelType().isBlank()) {
                metadata.put("model", directive.modelType());
            }
            MinecraftProfileTexture texture = new MinecraftProfileTexture(
                    directive.skinResource().url(), metadata);
            return Minecraft.getInstance().getSkinManager()
                    .registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
        });
    }

    public ClientSkinApplier skinApplier() {
        return skinApplier;
    }
}