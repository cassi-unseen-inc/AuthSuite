package net.authsuite.fabric.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.fabric.client.AuthSuiteFabricClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric network wiring for AuthSuite payloads (spec §4, §8).
 */
public final class FabricNetwork {

    public static final ResourceLocation PREFERENCE_ID = ResourceLocation.fromNamespaceAndPath("authsuite", "preference");
    public static final ResourceLocation SKIN_DIRECTIVE_ID = ResourceLocation.fromNamespaceAndPath("authsuite", "skin_directive");

    private FabricNetwork() {
    }

    public static void init() {
        PayloadTypeRegistry.configurationC2S().register(AuthProviderPreferencePayload.TYPE, AuthProviderPreferencePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AuthProviderPreferencePayload.TYPE, AuthProviderPreferencePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerSkinDirectivePayload.TYPE, PlayerSkinDirectivePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AuthProviderPreferencePayload.TYPE,
                (payload, context) -> context.player().server.execute(() -> {
                    PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(payload.data());
                    if (preference.isEmpty()) {
                        return;
                    }
                    String key = context.player().getUUID().toString();
                    net.authsuite.fabric.server.FabricServer server = net.authsuite.fabric.server.FabricServer.get();
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
            ClientPlayNetworking.registerGlobalReceiver(PlayerSkinDirectivePayload.TYPE,
                    (payload, context) -> context.client().execute(() -> {
                        SkinDirective directive = PacketCodec.decodeSkinDirective(payload.data());
                        AuthSuiteFabricClient.applySkinDirective(directive);
                    }));
        }
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

    public static record AuthProviderPreferencePayload(byte[] data) implements CustomPacketPayload {
        public static final Type<AuthProviderPreferencePayload> TYPE = new Type<>(PREFERENCE_ID);
        public static final StreamCodec<FriendlyByteBuf, AuthProviderPreferencePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBytes(p.data()),
                        buf -> new AuthProviderPreferencePayload(buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static record PlayerSkinDirectivePayload(byte[] data) implements CustomPacketPayload {
        public static final Type<PlayerSkinDirectivePayload> TYPE = new Type<>(SKIN_DIRECTIVE_ID);
        public static final StreamCodec<FriendlyByteBuf, PlayerSkinDirectivePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBytes(p.data()),
                        buf -> new PlayerSkinDirectivePayload(buf.readByteArray()));

        public static PlayerSkinDirectivePayload of(SkinDirective directive) {
            return new PlayerSkinDirectivePayload(PacketCodec.encodeSkinDirective(directive));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}