package net.authsuite.neoforge.network;

import net.authsuite.common.packet.PacketCodec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server provider preference (spec §8). The preference is advisory:
 * it may influence resolution ordering, but never establishes identity.
 * <p>
 * NeoForge 1.20.2/1.20.3 message using the legacy SimpleChannel
 * {@code FriendlyByteBuf} codec.
 */
public final class AuthProviderPreferencePayload {

    private final byte[] data;

    public AuthProviderPreferencePayload(byte[] data) {
        this.data = data;
    }

    public static void encode(AuthProviderPreferencePayload message, FriendlyByteBuf buf) {
        buf.writeByteArray(message.data);
    }

    public static AuthProviderPreferencePayload decode(FriendlyByteBuf buf) {
        return new AuthProviderPreferencePayload(buf.readByteArray());
    }

    public static AuthProviderPreferencePayload fromPreference(PacketCodec.PreferencePayload preference) {
        return new AuthProviderPreferencePayload(
                PacketCodec.encodePreference(preference.preferredProviderId(), preference.sessionHint()));
    }

    public byte[] data() {
        return data;
    }
}