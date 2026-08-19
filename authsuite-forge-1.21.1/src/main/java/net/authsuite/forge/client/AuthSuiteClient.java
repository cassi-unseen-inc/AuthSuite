package net.authsuite.forge.client;

import net.authsuite.common.skin.SkinDirective;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side AuthSuite logic: applies validated skin directives. All client skin
 * fetches are gated by the skin policy enforced in {@link ClientSkinApplier}.
 * <p>
 * No provider preference is sent: 1.21.1 Forge has no login-phase channel, so a
 * preference cannot be announced before identity verification.
 */
public final class AuthSuiteClient {

    private static volatile AuthSuiteClient instance;
    private final ClientSkinApplier skinApplier = new ClientSkinApplier();
    private final Map<UUID, SkinDirective> pendingDirectives = new ConcurrentHashMap<>();

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
        skinApplier.reset();
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

    public ClientSkinApplier skinApplier() {
        return skinApplier;
    }
}