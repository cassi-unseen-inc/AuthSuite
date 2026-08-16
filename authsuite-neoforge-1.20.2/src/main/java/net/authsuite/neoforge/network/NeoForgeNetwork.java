package net.authsuite.neoforge.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.neoforge.client.AuthSuiteClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * NeoForge 1.20.2/1.20.3 network wiring using the legacy {@link SimpleChannel}
 * API (before the payload registry era). Registers the provider preference
 * (client -> server) and the authoritative skin directive (server -> client).
 */
public final class NeoForgeNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("authsuite", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    static {
        CHANNEL.registerMessage(0, AuthProviderPreferencePayload.class,
                AuthProviderPreferencePayload::encode, AuthProviderPreferencePayload::decode,
                NeoForgeNetwork::handlePreference, Optional.of(PlayNetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, PlayerSkinDirectivePayload.class,
                PlayerSkinDirectivePayload::encode, PlayerSkinDirectivePayload::decode,
                NeoForgeNetwork::handleSkinDirective, Optional.of(PlayNetworkDirection.PLAY_TO_CLIENT));
    }

    private NeoForgeNetwork() {
    }

    public static void init(IEventBus modEventBus) {
        // Channel + messages are registered eagerly in the static initializer above.
    }

    private static void handlePreference(AuthProviderPreferencePayload payload, NetworkEvent.Context context) {
        PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
        if (preference.isEmpty()) {
            return;
        }
        ServerPlayer player = context.getSender();
        String key = player != null
                ? player.getUUID().toString()
                : context.getNetworkManager().getRemoteAddress() != null
                        ? context.getNetworkManager().getRemoteAddress().toString()
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

    private static void handleSkinDirective(PlayerSkinDirectivePayload payload, NetworkEvent.Context context) {
        if (FMLEnvironment.dist.isClient()) {
            AuthSuiteClient.applySkinDirective(PacketCodec.decodeSkinDirective(payload.data()));
        }
    }

    public static void registerClientHandlers(IEventBus modEventBus) {
        if (FMLEnvironment.dist.isClient()) {
            AuthSuiteClient.init(modEventBus);
        }
    }

    /** Client -> server provider preference send. */
    public static void sendToServer(AuthProviderPreferencePayload payload) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), payload);
    }

    /** Server -> client authoritative skin directive send. */
    public static void sendToPlayer(ServerPlayer player, PlayerSkinDirectivePayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    @SuppressWarnings("unused")
    private static void unused(FriendlyByteBuf buf) {
    }
}