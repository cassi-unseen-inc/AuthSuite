package net.authsuite.forge.client;

import net.authsuite.common.AuthSuiteConstants;
import net.authsuite.common.client.ClientPreference;
import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.forge.network.AuthProviderPreferencePayload;
import net.authsuite.forge.network.ForgeNetwork;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

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

    private void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        // Provider preference is advisory and arrives after the first login on 1.21.x;
        // it is bound for reconnects and identity resolution (never proof of identity).
        String preferred = ClientPreference.detect();
        PacketCodec.PreferencePayload preference = new PacketCodec.PreferencePayload(preferred, "");
        ForgeNetwork.sendToServer(new AuthProviderPreferencePayload(
                PacketCodec.encodePreference(preference.preferredProviderId(), preference.sessionHint())));
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