package net.authsuite.forge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
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
    public static final StreamCodec<RegistryFriendlyByteBuf, AuthProviderPreferencePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBytes(p.data()),
                    buf -> new AuthProviderPreferencePayload(buf.readByteArray()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}