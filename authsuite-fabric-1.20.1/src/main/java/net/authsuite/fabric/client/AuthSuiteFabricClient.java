package net.authsuite.fabric.client;

import net.authsuite.common.skin.SkinDirective;
import net.authsuite.fabric.network.FabricNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric client entrypoint: sends the provider preference after login and applies
 * validated skin directives.
 */
public final class AuthSuiteFabricClient implements ClientModInitializer {

    private static volatile AuthSuiteFabricClient instance;
    private final ClientSkinApplier skinApplier = new ClientSkinApplier();
    private final Map<UUID, SkinDirective> pendingDirectives = new ConcurrentHashMap<>();
    private boolean preferenceSent = false;

    @Override
    public void onInitializeClient() {
        instance = this;
        FabricNetwork.initClient();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // The provider preference is heralded during the LOGIN handshake
            // (FabricNetwork buildLoginResponse) and bound to that connection's
            // LoginAttempt. The play-phase preference send is intentionally gone:
            // username-keyed preference state is forbidden (post-audit §5).
            if (!preferenceSent && client.getConnection() != null) {
                preferenceSent = true;
                skinApplier.reset();
            }
        });
    }

    public static AuthSuiteFabricClient get() {
        return instance;
    }

    public static void applySkinDirective(SkinDirective directive) {
        if (instance != null) {
            instance.skinApplier.apply(directive);
        }
    }

    public SkinDirective directiveFor(UUID playerUuid) {
        return skinApplier.directiveFor(playerUuid);
    }

    public ClientSkinApplier skinApplier() {
        return skinApplier;
    }
}