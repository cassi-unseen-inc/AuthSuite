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
        PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
        if (preference.isEmpty()) {
            return;
        }
        String key = context.player() != null
                ? context.player().getGameProfile().getName()
                : context.connection().getRemoteAddress() != null
                        ? context.connection().getRemoteAddress().toString()
                        : "anon";
        context.enqueueWork(() -> {
            net.authsuite.neoforge.NeoForgeServer server = net.authsuite.neoforge.NeoForgeServer.get();
            if (server != null) {
                server.recordPreference(key,
                        new net.authsuite.common.provider.AuthResolver.PreferenceHint(
                                preference.preferredProviderId(), preference.sessionHint()));
                server.log().debug("Recorded client provider preference for {}", key);
            }
        });
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