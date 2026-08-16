package net.authsuite.fabric.client;

import net.authsuite.common.packet.PacketCodec;
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
            if (!preferenceSent && client.getConnection() != null) {
                preferenceSent = true;
                String preferred = System.getProperty("authsuite.preferredProvider", "");
                FabricNetwork.sendPreference(preferred, "");
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