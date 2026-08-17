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
 * era API (before stream-codec payloads). Registers the provider preference
 * (client -> server, both config and play phases) and the authoritative skin
 * directive (server -> client, play phase).
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
        PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
        if (preference.isEmpty()) {
            return;
        }
        String key = player != null
                ? player.getGameProfile().getName()
                : "anon";
        net.authsuite.neoforge.NeoForgeServer server = net.authsuite.neoforge.NeoForgeServer.get();
        if (server != null) {
            server.recordPreference(key,
                    new net.authsuite.common.provider.AuthResolver.PreferenceHint(
                            preference.preferredProviderId(), preference.sessionHint()));
            server.log().debug("Recorded client provider preference for {}", key);
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