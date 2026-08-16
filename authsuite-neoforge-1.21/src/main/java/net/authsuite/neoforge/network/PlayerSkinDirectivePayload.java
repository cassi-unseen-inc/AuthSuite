package net.authsuite.neoforge.network;

import io.netty.buffer.ByteBuf;
import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client authoritative skin directive (spec §4). Payload is the
 * load-balanced {@code PacketCodec} encoding of a {@link SkinDirective}.
 */
public record PlayerSkinDirectivePayload(byte[] data) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("authsuite", "skin_directive");
    public static final Type<PlayerSkinDirectivePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<ByteBuf, PlayerSkinDirectivePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE_ARRAY, PlayerSkinDirectivePayload::data,
                    PlayerSkinDirectivePayload::new);

    public static PlayerSkinDirectivePayload of(SkinDirective directive) {
        return new PlayerSkinDirectivePayload(PacketCodec.encodeSkinDirective(directive));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}