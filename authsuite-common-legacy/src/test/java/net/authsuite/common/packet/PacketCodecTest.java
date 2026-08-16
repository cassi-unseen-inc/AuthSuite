package net.authsuite.common.packet;

import net.authsuite.common.skin.SkinDirective;
import net.authsuite.common.skin.SkinResource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Payload codec round-trips: the preference and skin-directive byte encodings must
 * survive encode -> decode intact, and malformed input must never throw.
 */
class PacketCodecTest {

    @Test
    void preferenceRoundTrip() {
        byte[] data = PacketCodec.encodePreference("littleskins", "hint-123");
        PacketCodec.PreferencePayload decoded = PacketCodec.decodePreference(data);
        assertEquals("littleskins", decoded.preferredProviderId());
        assertEquals("hint-123", decoded.sessionHint());
    }

    @Test
    void preferenceEmptyDetection() {
        byte[] data = PacketCodec.encodePreference("", "");
        assertTrue(PacketCodec.decodePreference(data).isEmpty());
    }

    @Test
    void skinDirectiveRoundTrip() {
        UUID uuid = UUID.randomUUID();
        SkinDirective directive = new SkinDirective(
                uuid, "littleskins",
                new SkinResource("https://skins.littleskin.cn/texture/a.png"),
                new SkinResource("https://skins.littleskin.cn/texture/cape.png"),
                "slim", 3);
        SkinDirective decoded = PacketCodec.decodeSkinDirective(PacketCodec.encodeSkinDirective(directive));
        assertEquals(uuid, decoded.playerUUID());
        assertEquals("littleskins", decoded.providerId());
        assertEquals("https://skins.littleskin.cn/texture/a.png", decoded.skinResource().url());
        assertEquals("https://skins.littleskin.cn/texture/cape.png", decoded.capeResource().url());
        assertEquals("slim", decoded.modelType());
        assertEquals(3, decoded.revision());
    }

    @Test
    void skinDirectiveWithNullResources() {
        SkinDirective directive = new SkinDirective(UUID.randomUUID(), "elyby", null, null, "classic", 0);
        SkinDirective decoded = PacketCodec.decodeSkinDirective(PacketCodec.encodeSkinDirective(directive));
        assertEquals(null, decoded.skinResource());
        assertEquals(null, decoded.capeResource());
    }

    @Test
    void malformedInputNeverThrows() {
        assertTrue(PacketCodec.decodePreference(null).isEmpty());
        assertTrue(PacketCodec.decodePreference(new byte[]{1, 2, 3}).isEmpty());
    }
}