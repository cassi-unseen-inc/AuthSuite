package net.authsuite.neoforge.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.neoforge.client.AuthSuiteClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.handling.ConfigurationPayloadContext;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

/**
 * NeoForge 1.20.4 network wiring using the {@link RegisterPayloadHandlerEvent}
 * era API (before stream-codec payloads). Registers the authoritative skin
 * directive (server -> client, play phase).
 * <p>
 * The provider preference is intentionally inert here: 1.20.4+ NeoForge has no
 * login-phase channel at all, so a client cannot announce its preference before
 * identity verification. The {@code AuthProviderPreferencePayload} registration is
 * retained only for wire compatibility; its handler is a no-op and the connection
 * preference is never recorded.
 */
public final class NeoForgeNetwork {

    private NeoForgeNetwork() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeNetwork::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar("1");

        // Server-bound provider preference (config phase, earliest server-visible point).
        registrar.configuration(AuthProviderPreferencePayload.ID,
                AuthProviderPreferencePayload.READER,
                NeoForgeNetwork::handlePreferenceConfig);
        registrar.play(AuthProviderPreferencePayload.ID,
                AuthProviderPreferencePayload.READER,
                NeoForgeNetwork::handlePreferencePlay);

        // Client-bound authoritative skin directive (play phase).
        registrar.play(PlayerSkinDirectivePayload.ID,
                PlayerSkinDirectivePayload.READER,
                NeoForgeNetwork::handleSkinDirective);
    }

    private static void handlePreferenceConfig(AuthProviderPreferencePayload payload, ConfigurationPayloadContext context) {
        handlePreference(payload, context.player().orElse(null));
    }

    private static void handlePreferencePlay(AuthProviderPreferencePayload payload, PlayPayloadContext context) {
        handlePreference(payload, context.player().orElse(null));
    }

    private static void handlePreference(AuthProviderPreferencePayload payload, net.minecraft.world.entity.player.Player player) {
        // No-op: 1.20.4+ has no login-phase channel, so the preference is never
        // announced before identity verification and must not be trusted here.
        net.authsuite.neoforge.NeoForgeServer server = net.authsuite.neoforge.NeoForgeServer.get();
        if (server != null) {
            server.log().debug("Ignoring client provider preference (no login channel in this version)");
        }
    }

    private static void handleSkinDirective(PlayerSkinDirectivePayload payload, PlayPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            AuthSuiteClient.applySkinDirective(PacketCodec.decodeSkinDirective(payload.data()));
        }
    }

    public static void registerClientHandlers(IEventBus modEventBus) {
        if (FMLEnvironment.dist.isClient()) {
            AuthSuiteClient.init(modEventBus);
        }
    }
}