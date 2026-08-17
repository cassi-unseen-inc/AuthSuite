package net.authsuite.fabric.mixin;

import io.netty.buffer.Unpooled;
import net.authsuite.fabric.network.FabricNetwork;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends the {@code authsuite:login} query to the connecting client at the very
 * start of {@code handleHello} — before the vanilla encryption request — so the
 * client's provider preference reaches the server before the first
 * {@code hasJoinedServer}. Fabric's {@code ServerLoginNetworking} routes the
 * client's response to the registered receiver keyed by this query's
 * transaction id (recorded via {@code registerOutgoingPacket}); clients without
 * AuthSuite respond with an empty buffer (vanilla {@code handleCustomQueryPacket})
 * and are simply ignored.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginHandlerMixin {

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleHello", at = @At("HEAD"), require = 1)
    private void authsuite$sendLoginQuery(ServerboundHelloPacket packet, CallbackInfo ci) {
        if (!Boolean.parseBoolean(System.getProperty("authsuite.loginHerald", "true"))) {
            return;
        }
        if (this.connection == null || !this.connection.isConnected()) {
            return;
        }
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        this.connection.send(new ClientboundCustomQueryPacket(
                FabricNetwork.LOGIN_QUERY_ID, FabricNetwork.LOGIN_CHANNEL, data));
    }
}