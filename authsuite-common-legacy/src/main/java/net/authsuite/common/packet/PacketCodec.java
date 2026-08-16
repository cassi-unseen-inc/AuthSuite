package net.authsuite.common.packet;

import net.authsuite.common.skin.SkinDirective;
import net.authsuite.common.skin.SkinResource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * In-memory serialization for AuthSuite packet payloads. The platform layer wraps
 * these into loader-native custom payload packets (NeoForge / Fabric / Forge).
 * <p>
 * Binary layout is intentionally simple and load-balanced; no secret material is
 * ever serialized.
 */
public final class PacketCodec {

    private PacketCodec() {
    }

    // ---- AuthProviderHandshakePacket ----

    /** login channel payload: client -> server preferred provider + session hint. */
    public static byte[] encodePreference(String preferredProviderId, String sessionHint) {
        byte[] p = str(preferredProviderId == null ? "" : preferredProviderId);
        byte[] h = str(sessionHint == null ? "" : sessionHint);
        byte[] out = new byte[4 + p.length + 4 + h.length];
        int i = 0;
        i = putInt(out, i, p.length);
        i = putBytes(out, i, p);
        i = putInt(out, i, h.length);
        putBytes(out, i, h);
        return out;
    }

    public static final class PreferencePayload {
        private final String preferredProviderId;
        private final String sessionHint;

        public PreferencePayload(String preferredProviderId, String sessionHint) {
            this.preferredProviderId = preferredProviderId;
            this.sessionHint = sessionHint;
        }

        public String preferredProviderId() {
            return preferredProviderId;
        }

        public String sessionHint() {
            return sessionHint;
        }

        public boolean isEmpty() {
            return (preferredProviderId == null || preferredProviderId.trim().isEmpty())
                    && (sessionHint == null || sessionHint.trim().isEmpty());
        }
    }

    public static PreferencePayload decodePreference(byte[] data) {
        if (data == null) {
            return new PreferencePayload(null, null);
        }
        try {
            int i = 0;
            int plen = getInt(data, i);
            i += 4;
            String provider = new String(data, i, plen, StandardCharsets.UTF_8);
            i += plen;
            int hlen = getInt(data, i);
            i += 4;
            String hint = new String(data, i, hlen, StandardCharsets.UTF_8);
            return new PreferencePayload(provider, hint);
        } catch (RuntimeException e) {
            return new PreferencePayload(null, null);
        }
    }

    // ---- PlayerSkinDirectivePacket ----

    /** server -> client skin directive payload. */
    public static byte[] encodeSkinDirective(SkinDirective directive) {
        byte[] uuid = uuid(directive.playerUUID());
        byte[] provider = str(directive.providerId());
        byte[] skin = str(directive.skinResource() == null ? null : directive.skinResource().url());
        byte[] cape = str(directive.capeResource() == null ? null : directive.capeResource().url());
        byte[] model = str(directive.modelType());
        int size = 16 + 4 + provider.length + 4 + skin.length + 4 + cape.length + 4 + model.length + 8;
        byte[] out = new byte[size];
        int i = 0;
        i = putBytes(out, i, uuid);
        i = putInt(out, i, provider.length);
        i = putBytes(out, i, provider);
        i = putInt(out, i, skin.length);
        i = putBytes(out, i, skin);
        i = putInt(out, i, cape.length);
        i = putBytes(out, i, cape);
        i = putInt(out, i, model.length);
        i = putBytes(out, i, model);
        i = putLong(out, i, directive.revision());
        return out;
    }

    public static SkinDirective decodeSkinDirective(byte[] data) {
        int i = 0;
        UUID uuid = readUuid(data, i);
        i += 16;
        int plen = getInt(data, i);
        i += 4;
        String provider = new String(data, i, plen, StandardCharsets.UTF_8);
        i += plen;
        int slen = getInt(data, i);
        i += 4;
        String skin = new String(data, i, slen, StandardCharsets.UTF_8);
        i += slen;
        int clen = getInt(data, i);
        i += 4;
        String cape = new String(data, i, clen, StandardCharsets.UTF_8);
        i += clen;
        int mlen = getInt(data, i);
        i += 4;
        String model = new String(data, i, mlen, StandardCharsets.UTF_8);
        i += mlen;
        long rev = getLong(data, i);
        return new SkinDirective(uuid, provider,
                skin == null || skin.trim().isEmpty() ? null : new SkinResource(skin),
                cape == null || cape.trim().isEmpty() ? null : new SkinResource(cape),
                model, rev);
    }

    // ---- primitive helpers ----

    private static byte[] uuid(UUID uuid) {
        byte[] out = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (msb >>> (56 - i * 8));
        }
        for (int i = 0; i < 8; i++) {
            out[8 + i] = (byte) (lsb >>> (56 - i * 8));
        }
        return out;
    }

    private static UUID readUuid(byte[] data, int off) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (data[off + i] & 0xFF);
        }
        for (int i = 0; i < 8; i++) {
            lsb = (lsb << 8) | (data[off + 8 + i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }

    private static byte[] str(String s) {
        if (s == null) {
            return new byte[0];
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static int putInt(byte[] out, int i, int v) {
        out[i++] = (byte) (v >>> 24);
        out[i++] = (byte) (v >>> 16);
        out[i++] = (byte) (v >>> 8);
        out[i++] = (byte) v;
        return i;
    }

    private static int putLong(byte[] out, int i, long v) {
        for (int s = 56; s >= 0; s -= 8) {
            out[i++] = (byte) (v >>> s);
        }
        return i;
    }

    private static int putBytes(byte[] out, int i, byte[] src) {
        System.arraycopy(src, 0, out, i, src.length);
        return i + src.length;
    }

    private static int getInt(byte[] data, int i) {
        return ((data[i] & 0xFF) << 24) | ((data[i + 1] & 0xFF) << 16)
                | ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
    }

    private static long getLong(byte[] data, int i) {
        long v = 0;
        for (int s = 0; s < 8; s++) {
            v = (v << 8) | (data[i + s] & 0xFF);
        }
        return v;
    }
}