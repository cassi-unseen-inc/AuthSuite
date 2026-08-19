package net.authsuite.neoforge.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.neoforge.client.AuthSuiteClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge network wiring: payload type registration plus client-side event
 * subscription for sending the provider preference.
 * <p>
 * The provider preference is intentionally inert here: 1.20.5 NeoForge has no
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

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Server-bound provider preference (config phase, earliest server-visible point).
        registrar.configurationToServer(
                AuthProviderPreferencePayload.TYPE,
                AuthProviderPreferencePayload.STREAM_CODEC,
                NeoForgeNetwork::handlePreference);
        registrar.playToServer(
                AuthProviderPreferencePayload.TYPE,
                AuthProviderPreferencePayload.STREAM_CODEC,
                NeoForgeNetwork::handlePreference);

        // Client-bound authoritative skin directive (play phase).
        registrar.playToClient(
                PlayerSkinDirectivePayload.TYPE,
                PlayerSkinDirectivePayload.STREAM_CODEC,
                NeoForgeNetwork::handleSkinDirective);
    }

    private static void handlePreference(AuthProviderPreferencePayload payload, IPayloadContext context) {
        // No-op: 1.20.5 has no login-phase channel, so the preference is never
        // announced before identity verification and must not be trusted here.
        net.authsuite.neoforge.NeoForgeServer server = net.authsuite.neoforge.NeoForgeServer.get();
        if (server != null) {
            server.log().debug("Ignoring client provider preference (no login channel in this version)");
        }
    }

    private static void handleSkinDirective(PlayerSkinDirectivePayload payload, IPayloadContext context) {
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