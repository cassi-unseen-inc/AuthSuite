package net.authsuite.neoforge.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client authoritative skin directive (spec §4). Payload is the
 * load-balanced {@code PacketCodec} encoding of a {@link SkinDirective}.
 * <p>
 * NeoForge 1.20.4 payload using the pre-stream-codec {@code CustomPacketPayload}
 * contract ({@link #id()} + {@link #write(FriendlyByteBuf)}).
 */
public final class PlayerSkinDirectivePayload implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation("authsuite", "skin_directive");
    public static final FriendlyByteBuf.Reader<PlayerSkinDirectivePayload> READER =
            buf -> new PlayerSkinDirectivePayload(buf.readByteArray());

    private final byte[] data;

    public PlayerSkinDirectivePayload(byte[] data) {
        this.data = data;
    }

    public static PlayerSkinDirectivePayload of(SkinDirective directive) {
        return new PlayerSkinDirectivePayload(PacketCodec.encodeSkinDirective(directive));
    }

    public byte[] data() {
        return data;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByteArray(data);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}