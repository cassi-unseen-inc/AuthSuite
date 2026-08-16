package net.authsuite.forge.network;

import net.authsuite.common.packet.PacketCodec;
import net.minecraft.network.PacketBuffer;

/**
 * Client -> server provider preference (spec §8). The preference is advisory:
 * it may influence resolution ordering, but never establishes identity.
 * <p>
 * Forge 1.16.5 message carrying the load-balanced {@code PacketCodec} encoding.
 */
public final class AuthProviderPreferencePayload {

    private final byte[] data;

    public AuthProviderPreferencePayload(byte[] data) {
        this.data = data;
    }

    public static void encode(AuthProviderPreferencePayload message, PacketBuffer buf) {
        buf.writeByteArray(message.data);
    }

    public static AuthProviderPreferencePayload decode(PacketBuffer buf) {
        return new AuthProviderPreferencePayload(buf.readByteArray());
    }

    public byte[] data() {
        return data;
    }

    public static AuthProviderPreferencePayload fromPreference(PacketCodec.PreferencePayload preference) {
        return new AuthProviderPreferencePayload(
                PacketCodec.encodePreference(preference.preferredProviderId(), preference.sessionHint()));
    }
}