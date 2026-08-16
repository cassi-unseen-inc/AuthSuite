package net.authsuite.forge.network;

import net.authsuite.common.packet.PacketCodec;
import net.authsuite.common.skin.SkinDirective;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client authoritative skin directive (spec §4). Payload is the
 * load-balanced {@code PacketCodec} encoding of a {@link SkinDirective}.
 * <p>
 * Forge 1.20.1 message using the SimpleChannel {@code FriendlyByteBuf} codec.
 */
public final class PlayerSkinDirectivePayload {

    private final byte[] data;

    public PlayerSkinDirectivePayload(byte[] data) {
        this.data = data;
    }

    public static void encode(PlayerSkinDirectivePayload message, FriendlyByteBuf buf) {
        buf.writeByteArray(message.data);
    }

    public static PlayerSkinDirectivePayload decode(FriendlyByteBuf buf) {
        return new PlayerSkinDirectivePayload(buf.readByteArray());
    }

    public static PlayerSkinDirectivePayload of(SkinDirective directive) {
        return new PlayerSkinDirectivePayload(PacketCodec.encodeSkinDirective(directive));
    }

    public byte[] data() {
        return data;
    }
}