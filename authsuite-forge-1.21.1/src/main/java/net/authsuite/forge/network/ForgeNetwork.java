package net.authsuite.forge.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.forge.ForgeServer;
import net.authsuite.forge.client.AuthSuiteClient;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

/**
 * Forge network wiring using the Forge 1.21.1 {@code ChannelBuilder} /
 * {@code PayloadChannel} API. Registers the provider preference (client -> server)
 * and the authoritative skin directive (server -> client).
 */
public final class ForgeNetwork {

    private static final Channel<net.minecraft.network.protocol.common.custom.CustomPacketPayload> CHANNEL;

    static {
        CHANNEL = ChannelBuilder.named("authsuite:main")
                .networkProtocolVersion(1)
                .acceptedVersions(Channel.VersionTest.exact(1))
                .payloadChannel()
                .play()
                .flow(PacketFlow.SERVERBOUND)
                .add(AuthProviderPreferencePayload.TYPE, AuthProviderPreferencePayload.STREAM_CODEC,
                        ForgeNetwork::handlePreference)
                .flow(PacketFlow.CLIENTBOUND)
                .add(PlayerSkinDirectivePayload.TYPE, PlayerSkinDirectivePayload.STREAM_CODEC,
                        ForgeNetwork::handleSkinDirective)
                .build();
    }

    private ForgeNetwork() {
    }

    public static void init() {
        // Channel is built eagerly in the static initializer above; nothing to do here.
    }

    public static void registerClientHandlers(IEventBus modEventBus) {
        if (FMLEnvironment.dist.isClient()) {
            AuthSuiteClient.init(modEventBus);
        }
    }

    private static void handlePreference(AuthProviderPreferencePayload payload, CustomPayloadEvent.Context context) {
        PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
        if (preference.isEmpty()) {
            context.setPacketHandled(true);
            return;
        }
        ServerPlayer player = context.getSender();
        String key = player != null
                ? player.getUUID().toString()
                : context.getConnection().getRemoteAddress() != null
                        ? context.getConnection().getRemoteAddress().toString()
                        : "anon";
        context.enqueueWork(() -> {
            ForgeServer server = ForgeServer.get();
            if (server != null) {
                server.recordPreference(key,
                        new AuthResolver.PreferenceHint(
                                preference.preferredProviderId(), preference.sessionHint()));
                server.log().debug("Recorded client provider preference for {}", key);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleSkinDirective(PlayerSkinDirectivePayload payload, CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> AuthSuiteClient.applySkinDirective(PacketCodec.decodeSkinDirective(payload.data())));
        context.setPacketHandled(true);
    }

    /** Client -> server preference send. */
    public static void sendToServer(AuthProviderPreferencePayload payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }

    /** Server -> client skin directive send. */
    public static void sendToPlayer(ServerPlayer player, PlayerSkinDirectivePayload payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }
}