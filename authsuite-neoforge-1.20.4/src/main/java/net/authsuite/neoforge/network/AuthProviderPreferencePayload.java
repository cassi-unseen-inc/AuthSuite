package net.authsuite.neoforge.network;

import net.authsuite.common.packet.PacketCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server provider preference (spec §8). The preference is advisory:
 * it may influence resolution ordering, but never establishes identity.
 * <p>
 * NeoForge 1.20.4 payload using the pre-stream-codec {@code CustomPacketPayload}
 * contract ({@link #id()} + {@link #write(FriendlyByteBuf)}).
 */
public final class AuthProviderPreferencePayload implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation("authsuite", "preference");
    public static final FriendlyByteBuf.Reader<AuthProviderPreferencePayload> READER =
            buf -> new AuthProviderPreferencePayload(buf.readByteArray());

    private final byte[] data;

    public AuthProviderPreferencePayload(byte[] data) {
        this.data = data;
    }

    public static AuthProviderPreferencePayload fromPreference(PacketCodec.PreferencePayload preference) {
        return new AuthProviderPreferencePayload(
                PacketCodec.encodePreference(preference.preferredProviderId(), preference.sessionHint()));
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