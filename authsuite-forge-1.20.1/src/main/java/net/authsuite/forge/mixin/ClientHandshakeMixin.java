package net.authsuite.forge.mixin;

import net.authsuite.forge.network.ForgeNetwork;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Heralds the client's provider preference during the login handshake, before the
 * encryption key packet is sent (and thus before the server's first
 * {@code hasJoinedServer}). The preference packet is queued synchronously at the
 * head of {@code handleHello} on the netty event-loop thread, so it precedes the
 * {@code ServerboundKeyPacket} (which vanilla submits to the {@code HttpUtil}
 * executor) on the same connection.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakeMixin {

    @Shadow(remap = false)
    private Connection connection;

    @Inject(method = "handleHello", at = @At("HEAD"), remap = false)
    private void authsuite_sendLoginPreference(ClientboundHelloPacket packet, CallbackInfo ci) {
        ForgeNetwork.sendLoginPreference(this.connection);
    }
}
