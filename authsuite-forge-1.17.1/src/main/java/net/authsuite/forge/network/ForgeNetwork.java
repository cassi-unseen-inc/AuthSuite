package net.authsuite.forge.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.forge.ForgeServer;
import net.authsuite.forge.client.AuthSuiteClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fmllegacy.network.NetworkDirection;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import net.minecraftforge.fmllegacy.network.NetworkRegistry;
import net.minecraftforge.fmllegacy.network.PacketDistributor;
import net.minecraftforge.fmllegacy.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Forge 1.17.1 network wiring using the legacy FML {@code SimpleChannel} API. Registers the
 * provider preference (client -> server) and the authoritative skin directive
 * (server -> client).
 */
public final class ForgeNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("authsuite", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    static {
        CHANNEL.registerMessage(0, AuthProviderPreferencePayload.class,
                AuthProviderPreferencePayload::encode, AuthProviderPreferencePayload::decode,
                ForgeNetwork::handlePreference, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, PlayerSkinDirectivePayload.class,
                PlayerSkinDirectivePayload::encode, PlayerSkinDirectivePayload::decode,
                ForgeNetwork::handleSkinDirective, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private ForgeNetwork() {
    }

    public static void init() {
        // Channel + messages are registered eagerly in the static initializer above.
    }

    public static void registerClientHandlers(IEventBus modEventBus) {
        if (FMLEnvironment.dist.isClient()) {
            AuthSuiteClient.init(modEventBus);
        }
    }

    private static void handlePreference(AuthProviderPreferencePayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
        if (!preference.isEmpty()) {
            ServerPlayer player = context.getSender();
            String key = player != null
                    ? player.getGameProfile().getName()
                    : context.getNetworkManager().getRemoteAddress() != null
                            ? context.getNetworkManager().getRemoteAddress().toString()
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
        }
        context.setPacketHandled(true);
    }

    private static void handleSkinDirective(PlayerSkinDirectivePayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                AuthSuiteClient.applySkinDirective(PacketCodec.decodeSkinDirective(payload.data())));
        context.setPacketHandled(true);
    }

    /** Client -> server preference send. */
    public static void sendToServer(AuthProviderPreferencePayload payload) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), payload);
    }

    /** Server -> client skin directive send. */
    public static void sendToPlayer(ServerPlayer player, PlayerSkinDirectivePayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}