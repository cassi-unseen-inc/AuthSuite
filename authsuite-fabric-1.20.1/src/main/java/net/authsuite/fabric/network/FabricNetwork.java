package net.authsuite.fabric.network;

import com.mojang.authlib.GameProfile;
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
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric 1.20.1 network wiring for AuthSuite messages (spec §4, §8) using the
 * legacy {@code FabricPacket} channel API.
 */
public final class FabricNetwork {

    public static final ResourceLocation PREFERENCE_ID = new ResourceLocation("authsuite", "preference");
    public static final ResourceLocation SKIN_DIRECTIVE_ID = new ResourceLocation("authsuite", "skin_directive");
    public static final ResourceLocation LOGIN_CHANNEL = new ResourceLocation("authsuite", "login");
    public static final int LOGIN_QUERY_ID = 0x5000;

    private FabricNetwork() {
    }

    public static void init() {
        ServerLoginNetworking.registerGlobalReceiver(LOGIN_CHANNEL, FabricNetwork::handleLoginPreference);
        ServerPlayNetworking.registerGlobalReceiver(AuthProviderPreferencePayload.TYPE,
                (payload, player, sender) -> player.server.execute(() -> {
                    PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
                    if (preference.isEmpty()) {
                        return;
                    }
                    String key = player.getGameProfile().getName();
                    FabricServer server = FabricServer.get();
                    if (server != null) {
                        server.recordPreference(key,
                                new net.authsuite.common.provider.AuthResolver.PreferenceHint(
                                        preference.preferredProviderId(), preference.sessionHint()));
                        server.log().debug("Recorded client provider preference for {}", key);
                    }
                }));
    }

    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientLoginNetworking.registerGlobalReceiver(LOGIN_CHANNEL,
                    (client, handler, buf, responder) -> buildLoginResponse());
            ClientPlayNetworking.registerGlobalReceiver(PlayerSkinDirectivePayload.TYPE,
                    (payload, player, sender) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        SkinDirective directive = PacketCodec.decodeSkinDirective(payload.data());
                        AuthSuiteFabricClient.applySkinDirective(directive);
                    }));
        }
    }

    /**
     * Server-side login-phase receiver. Fabric queries the connecting client
     * during the login handshake (see {@code ServerLoginHandlerMixin}); this
     * records the client's provider preference keyed by username before the
     * authenticator runs. Clients without AuthSuite respond with an empty buffer
     * ({@code understood == false}) and are ignored.
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
        GameProfile profile = ((ServerLoginHandlerAccessor) handler).authsuite$getGameProfile();
        String username = profile != null ? profile.getName() : null;
        if (username == null) {
            return;
        }
        FabricServer fabricServer = FabricServer.get();
        if (fabricServer != null) {
            fabricServer.recordPreference(username,
                    new net.authsuite.common.provider.AuthResolver.PreferenceHint(
                            preference.preferredProviderId(), preference.sessionHint()));
            fabricServer.log().info("Recorded login-phase provider preference for user '{}' = '{}'",
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

    public static final class AuthProviderPreferencePayload implements FabricPacket {
        public static final PacketType<AuthProviderPreferencePayload> TYPE =
                PacketType.create(PREFERENCE_ID, AuthProviderPreferencePayload::new);

        private final byte[] data;

        public AuthProviderPreferencePayload(byte[] data) {
            this.data = data;
        }

        private AuthProviderPreferencePayload(FriendlyByteBuf buf) {
            this.data = buf.readByteArray();
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBytes(data);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public byte[] data() {
            return data;
        }
    }

    public static final class PlayerSkinDirectivePayload implements FabricPacket {
        public static final PacketType<PlayerSkinDirectivePayload> TYPE =
                PacketType.create(SKIN_DIRECTIVE_ID, PlayerSkinDirectivePayload::new);

        private final byte[] data;

        public PlayerSkinDirectivePayload(byte[] data) {
            this.data = data;
        }

        private PlayerSkinDirectivePayload(FriendlyByteBuf buf) {
            this.data = buf.readByteArray();
        }

        public static PlayerSkinDirectivePayload of(SkinDirective directive) {
            return new PlayerSkinDirectivePayload(PacketCodec.encodeSkinDirective(directive));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBytes(data);
        }

        @Override
        public PacketType<?> getType() {
            return TYPE;
        }

        public byte[] data() {
            return data;
        }
    }
}