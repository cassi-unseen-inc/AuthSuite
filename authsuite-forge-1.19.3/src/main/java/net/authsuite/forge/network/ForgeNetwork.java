package net.authsuite.forge.network;

import io.netty.buffer.Unpooled;
import net.authsuite.common.client.ClientPreference;
import net.authsuite.common.login.LoginAttempt;
import net.authsuite.common.login.LoginAttemptStore;
import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.provider.AuthResolver;
import net.authsuite.forge.ForgeServer;
import net.authsuite.forge.client.AuthSuiteClient;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 network wiring using the {@code SimpleChannel} API. Registers the
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
        CHANNEL.registerMessage(2, AuthLoginPreferencePayload.class,
                AuthLoginPreferencePayload::encode, AuthLoginPreferencePayload::decode,
                ForgeNetwork::handleLoginPreference, Optional.of(NetworkDirection.LOGIN_TO_SERVER));
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
        // Play-phase provider preference is deprecated: the provider preference is
        // bound to the login attempt via the login-phase herald (post-audit §5) and
        // must never be keyed by username. The payload remains registered for
        // protocol compatibility but carries no state.
        ctx.get().setPacketHandled(true);
    }

    private static void handleSkinDirective(PlayerSkinDirectivePayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                AuthSuiteClient.applySkinDirective(PacketCodec.decodeSkinDirective(payload.data())));
        context.setPacketHandled(true);
    }

    /**
     * Login-phase preference handler. Runs on the netty event-loop thread and MUST
     * record synchronously (no enqueueWork): {@code hasJoinedServer} runs on the
     * "User Authenticator" thread spawned afterwards, so the write must be visible
     * before that thread reads it (happens-before via the thread start in
     * {@code ServerLoginPacketListenerImpl#handleKey}).
     * <p>
     * The preference is bound to THIS connection's {@link LoginAttempt}, never to
     * a username, so simultaneous same-username logins stay independent.
     */
    private static void handleLoginPreference(AuthLoginPreferencePayload payload, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
        if (!preference.isEmpty()) {
            PacketListener listener = context.getNetworkManager().getPacketListener();
            if (listener instanceof ServerLoginPacketListenerImpl login) {
                String username = loginUsername(context.getNetworkManager());
                LoginAttempt attempt = LoginAttemptStore.forConnection(login);
                if (attempt == null) {
                    attempt = new LoginAttempt(login, username);
                    LoginAttemptStore.bind(login, attempt);
                }
                attempt.setPreference(new AuthResolver.PreferenceHint(
                        preference.preferredProviderId(), preference.sessionHint()));
                net.authsuite.forge.AuthSuiteLoggerFactory.get().info(
                        "Recorded login-phase provider preference for connection of '{}' = '{}'",
                        username, preference.preferredProviderId());
            }
        }
        context.setPacketHandled(true);
    }

    /**
     * Extracts the connecting player's username from the login-phase listener. The
     * server's {@code ServerLoginPacketListenerImpl} already holds the
     * {@code GameProfile} from the earlier hello packet; reading it here lets us key
     * the herald by username so {@code hasJoinedServer} can find it regardless of
     * whether the server passes the remote address (which is {@code null} unless
     * {@code prevent-proxy-connections} is enabled). Field is {@code gameProfile} in
     * dev (mojmap) and {@code f_10021_} in the reobfuscated production jar.
     */
    private static String loginUsername(Connection connection) {
        try {
            PacketListener listener = connection.getPacketListener();
            if (listener instanceof ServerLoginPacketListenerImpl login) {
                for (String fieldName : new String[]{"f_10021_", "gameProfile"}) {
                    try {
                        Field field = ServerLoginPacketListenerImpl.class.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object value = field.get(login);
                        if (value instanceof com.mojang.authlib.GameProfile profile) {
                            return profile.getName();
                        }
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Client-side login-phase preference send. Builds a {@link ServerboundCustomQueryPacket}
     * whose payload is wrapped in the Forge {@code fml:loginwrapper} frame
     * ({@code [targetChannel][varint length][data]}) and queues it at the head of
     * {@code ClientHandshakePacketListenerImpl#handleHello} — before the encryption
     * key packet, so the server records the preference before the first
     * {@code hasJoinedServer}.
     */
    public static void sendLoginPreference(Connection connection) {
        if (!Boolean.parseBoolean(System.getProperty("authsuite.loginHerald", "true"))) {
            return;
        }
        if (connection == null || !connection.isConnected()) {
            return;
        }
        String preferred = ClientPreference.detect();
        PacketCodec.PreferencePayload preference = new PacketCodec.PreferencePayload(preferred, "");
        if (preference.isEmpty()) {
            return;
        }
        AuthLoginPreferencePayload payload = AuthLoginPreferencePayload.fromPreference(preference);
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        CHANNEL.encodeMessage(payload, data);
        FriendlyByteBuf wrapped = new FriendlyByteBuf(Unpooled.buffer());
        wrapped.writeResourceLocation(new ResourceLocation("authsuite", "main"));
        wrapped.writeVarInt(data.readableBytes());
        wrapped.writeBytes(data);
        connection.send(new ServerboundCustomQueryPacket(0, wrapped));
        net.authsuite.forge.AuthSuiteLoggerFactory.get().info(
                "Sending login-phase provider preference for '{}'", preferred);
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