package net.authsuite.neoforge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server provider preference (spec §8). The preference is advisory:
 * it may influence resolution ordering, but never establishes identity.
 */
public record AuthProviderPreferencePayload(byte[] data) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("authsuite", "preference");
    public static final Type<AuthProviderPreferencePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<ByteBuf, AuthProviderPreferencePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE_ARRAY, AuthProviderPreferencePayload::data,
                    AuthProviderPreferencePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}