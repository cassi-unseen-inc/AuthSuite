package net.authsuite.forge.network;

import net.authsuite.common.packet.PacketCodec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server provider preference delivered during the <em>login</em> phase,
 * before the encryption key exchange and thus before the first
 * {@code hasJoinedServer}. Used to herald the client's provider so the server can
 * prefer it on the very first join (spec §8).
 * <p>
 * Unlike {@link AuthProviderPreferencePayload} (PLAY_TO_SERVER), this message is
 * registered on the {@code LOGIN_TO_SERVER} direction and is wrapped inside the
 * Forge login wrapper channel by {@link ForgeNetwork#sendLoginPreference}.
 */
public final class AuthLoginPreferencePayload {

    private final byte[] data;

    public AuthLoginPreferencePayload(byte[] data) {
        this.data = data;
    }

    public static void encode(AuthLoginPreferencePayload message, FriendlyByteBuf buf) {
        buf.writeByteArray(message.data);
    }

    public static AuthLoginPreferencePayload decode(FriendlyByteBuf buf) {
        return new AuthLoginPreferencePayload(buf.readByteArray());
    }

    public byte[] data() {
        return data;
    }

    public static AuthLoginPreferencePayload fromPreference(PacketCodec.PreferencePayload preference) {
        return new AuthLoginPreferencePayload(
                PacketCodec.encodePreference(preference.preferredProviderId(), preference.sessionHint()));
    }
}