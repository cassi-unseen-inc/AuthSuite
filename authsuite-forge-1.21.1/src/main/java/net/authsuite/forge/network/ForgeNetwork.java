package net.authsuite.forge.network;

import net.authsuite.common.packet.PacketCodec;
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
 * <p>
 * The provider preference is intentionally inert here: 1.21.1 Forge has no
 * login-phase channel at all, so a client cannot announce its preference before
 * identity verification. The {@code AuthProviderPreferencePayload} registration is
 * retained only for wire compatibility; its handler is a no-op and the connection
 * preference is never recorded.
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
        // No-op: 1.21.1 Forge has no login-phase channel, so the preference is never
        // announced before identity verification and must not be trusted here.
        ForgeServer server = ForgeServer.get();
        if (server != null) {
            server.log().debug("Ignoring client provider preference (no login channel in this version)");
        }
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