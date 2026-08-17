package net.authsuite.fabric.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.authsuite.fabric.client.AuthSuiteFabricClient;
import net.authsuite.fabric.server.FabricServer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric 1.18.2 network wiring for AuthSuite messages (spec §4, §8) using the
 * legacy channel API ({@code ResourceLocation} + {@code FriendlyByteBuf}).
 */
public final class FabricNetwork {

    public static final ResourceLocation PREFERENCE_ID = new ResourceLocation("authsuite", "preference");
    public static final ResourceLocation SKIN_DIRECTIVE_ID = new ResourceLocation("authsuite", "skin_directive");

    private FabricNetwork() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(PREFERENCE_ID,
                (server, player, handler, buf, sender) -> server.execute(() -> {
                    byte[] data = buf.readByteArray();
                    PacketCodec.PreferencePayload preference = PacketCodec.decodePreference(data);
                    if (preference.isEmpty()) {
                        return;
                    }
                    String key = player.getGameProfile().getName();
                    FabricServer fabricServer = FabricServer.get();
                    if (fabricServer != null) {
                        fabricServer.recordPreference(key,
                                new net.authsuite.common.provider.AuthResolver.PreferenceHint(
                                        preference.preferredProviderId(), preference.sessionHint()));
                        fabricServer.log().debug("Recorded client provider preference for {}", key);
                    }
                }));
    }

    public static void initClient() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientPlayNetworking.registerGlobalReceiver(SKIN_DIRECTIVE_ID,
                    (client, handler, buf, sender) -> client.execute(() -> {
                        byte[] data = buf.readByteArray();
                        SkinDirective directive = PacketCodec.decodeSkinDirective(data);
                        AuthSuiteFabricClient.applySkinDirective(directive);
                    }));
        }
    }

    public static void sendPreference(String preferredProviderId, String sessionHint) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(new io.netty.buffer.UnpooledByteBufAllocator(false).buffer());
        try {
            buf.writeBytes(PacketCodec.encodePreference(preferredProviderId, sessionHint));
            ClientPlayNetworking.send(PREFERENCE_ID, buf);
        } finally {
            buf.release();
        }
    }

    public static void sendSkinDirective(net.minecraft.server.level.ServerPlayer player, SkinDirective directive) {
        FriendlyByteBuf buf = new FriendlyByteBuf(new io.netty.buffer.UnpooledByteBufAllocator(false).buffer());
        try {
            buf.writeBytes(PacketCodec.encodeSkinDirective(directive));
            ServerPlayNetworking.send(player, SKIN_DIRECTIVE_ID, buf);
        } finally {
            buf.release();
        }
    }

    @SuppressWarnings("unused")
    private static void keepClientImports() {
        if (Minecraft.getInstance() == null) {
            return;
        }
    }
}