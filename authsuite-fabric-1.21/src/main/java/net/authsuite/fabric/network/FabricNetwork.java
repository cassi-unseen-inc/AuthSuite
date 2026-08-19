package net.authsuite.fabric.network;

import io.netty.buffer.Unpooled;
import net.authsuite.common.client.ClientPreference;
import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.fabric.client.AuthSuiteFabricClient;
import net.authsuite.fabric.mixin.ServerLoginHandlerAccessor;
import net.authsuite.fabric.server.FabricServer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric payload-API network wiring for AuthSuite messages (spec §4, §8). This
 * Minecraft / Fabric API generation removed the legacy {@code FabricPacket}
 * channel API, so the play-phase channels use {@link CustomPacketPayload}
 * registered with a {@link PayloadTypeRegistry} {@link StreamCodec}.
 * <p>
 * Login-phase herald (post-audit §5): the server queries the connecting client
 * on {@code authsuite:login}; the client responds with its detected provider
 * preference, which the server binds to THIS connection's
 * {@link net.authsuite.common.login.LoginAttempt} — never to a username.
 */
public final class FabricNetwork {

    public static final ResourceLocation PREFERENCE_ID = ResourceLocation.fromNamespaceAndPath("authsuite", "preference");
    public static final ResourceLocation SKIN_DIRECTIVE_ID = ResourceLocation.fromNamespaceAndPath("authsuite", "skin_directive");
    public static final ResourceLocation LOGIN_CHANNEL = ResourceLocation.fromNamespaceAndPath("authsuite", "login");
    public static final int LOGIN_QUERY_ID = 0x5000;

    private static boolean codecsRegistered = false;

    private FabricNetwork() {
    }

    private static void registerCodecs() {
        if (codecsRegistered) {
            return;
        }
        codecsRegistered = true;
        PayloadTypeRegistry.playC2S().register(AuthProviderPreferencePayload.TYPE,
                ByteBufCodecs.BYTE_ARRAY.map(AuthProviderPreferencePayload::new, AuthProviderPreferencePayload::data));
        PayloadTypeRegistry.playS2C().register(PlayerSkinDirectivePayload.TYPE,
                ByteBufCodecs.BYTE_ARRAY.map(PlayerSkinDirectivePayload::new, PlayerSkinDirectivePayload::data));
    }

    public static void init() {
        registerCodecs();
        ServerLoginNetworking.registerGlobalReceiver(LOGIN_CHANNEL, FabricNetwork::handleLoginPreference);
        ServerPlayNetworking.registerGlobalReceiver(AuthProviderPreferencePayload.TYPE,
                (payload, context) -> {
                    // Play-phase provider preference is deprecated: the provider
                    // preference is bound to the login attempt via the login-phase
                    // herald (post-audit §5) and must never be keyed by username.
                    // The payload remains registered for protocol compatibility but
                    // carries no state.
                });
    }

    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registerCodecs();
            ClientLoginNetworking.registerGlobalReceiver(LOGIN_CHANNEL,
                    (client, handler, buf, responder) -> buildLoginResponse());
            ClientPlayNetworking.registerGlobalReceiver(PlayerSkinDirectivePayload.TYPE,
                    (payload, context) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        SkinDirective directive = PacketCodec.decodeSkinDirective(payload.data());
                        AuthSuiteFabricClient.applySkinDirective(directive);
                    }));
        }
    }

    /**
     * Server-side login-phase receiver. Fabric queries the connecting client
     * during the login handshake (see {@code ServerLoginHandlerMixin}); this
     * binds the client's provider preference to THIS connection's
     * {@link net.authsuite.common.login.LoginAttempt}, never to a username, so
     * simultaneous same-username logins stay independent. Clients without
     * AuthSuite respond with an empty buffer ({@code understood == false}) and
     * are ignored.
     */
    private static void handleLoginPreference(MinecraftServer server, ServerLoginPacketListenerImpl handler,
                                              boolean understood, FriendlyByteBuf buf,
                                              ServerLoginNetworking.LoginSynchronizer synchronizer, PacketSender responder) {
        if (!understood || buf == null) {
            return;
        }
        byte[] data;
        try {
            data = buf.readByteArray();
        } catch (Exception ignored) {
            return;
        }
        PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(data);
        if (preference.isEmpty()) {
            return;
        }
        String username = ((ServerLoginHandlerAccessor) handler).authsuite$getRequestedUsername();
        net.authsuite.common.login.LoginAttempt attempt = net.authsuite.common.login.LoginAttemptStore.forConnection(handler);
        if (attempt == null) {
            attempt = new net.authsuite.common.login.LoginAttempt(handler, username);
            net.authsuite.common.login.LoginAttemptStore.bind(handler, attempt);
        }
        attempt.setPreference(new net.authsuite.common.provider.AuthResolver.PreferenceHint(
                preference.preferredProviderId(), preference.sessionHint()));
        FabricServer fabricServer = FabricServer.get();
        if (fabricServer != null) {
            fabricServer.log().info("Recorded login-phase provider preference for connection of '{}' = '{}'",
                    username, preference.preferredProviderId());
        }
    }

    /**
     * Client-side login-phase response: returns the detected provider preference.
     * An empty (null) response is sent when no preference is available or the
     * herald is disabled, mirroring the vanilla empty-query-response behaviour.
     */
    private static CompletableFuture<FriendlyByteBuf> buildLoginResponse() {
        if (!Boolean.parseBoolean(System.getProperty("authsuite.loginHerald", "true"))) {
            return CompletableFuture.completedFuture(null);
        }
        String preferred = ClientPreference.detect();
        if (preferred == null || preferred.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
        response.writeByteArray(PacketCodec.encodePreference(preferred, ""));
        return CompletableFuture.completedFuture(response);
    }

    public static void sendPreference(String preferredProviderId, String sessionHint) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        byte[] data = PacketCodec.encodePreference(preferredProviderId, sessionHint);
        ClientPlayNetworking.send(new AuthProviderPreferencePayload(data));
    }

    public static void sendSkinDirective(net.minecraft.server.level.ServerPlayer player, SkinDirective directive) {
        ServerPlayNetworking.send(player, PlayerSkinDirectivePayload.of(directive));
    }

    public static final class AuthProviderPreferencePayload implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AuthProviderPreferencePayload> TYPE =
                new CustomPacketPayload.Type<>(PREFERENCE_ID);

        private final byte[] data;

        public AuthProviderPreferencePayload(byte[] data) {
            this.data = data;
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public byte[] data() {
            return data;
        }
    }

    public static final class PlayerSkinDirectivePayload implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayerSkinDirectivePayload> TYPE =
                new CustomPacketPayload.Type<>(SKIN_DIRECTIVE_ID);

        private final byte[] data;

        public PlayerSkinDirectivePayload(byte[] data) {
            this.data = data;
        }

        public static PlayerSkinDirectivePayload of(SkinDirective directive) {
            return new PlayerSkinDirectivePayload(PacketCodec.encodeSkinDirective(directive));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public byte[] data() {
            return data;
        }
    }
}