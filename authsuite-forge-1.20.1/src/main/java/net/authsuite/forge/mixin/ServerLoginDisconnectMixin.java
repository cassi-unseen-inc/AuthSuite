package net.authsuite.forge.mixin;

import com.mojang.authlib.GameProfile;
import net.authsuite.common.login.LoginAttempt;
import net.authsuite.common.login.LoginAttemptStore;
import net.authsuite.forge.ForgeServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases the AuthSuite session registered by a login-phase connection when that
 * connection terminates before the player actually joins. A connection that
 * authenticates ({@code buildProfileResult} registers the canonical identity) and
 * is then dropped during login - e.g. Forge's mod-channel negotiation kick for a
 * client without the mod, or a client disconnect right after auth - otherwise
 * leaves the canonical identity "active" forever, blocking every subsequent login
 * with "Failed to verify username!" until the server restarts.
 * <p>
 * SRG names (production): {@code m_7026_} logs "{} lost connection: {}" (remote
 * close detection) and {@code m_10053_} logs "Disconnecting {}: {}" (server-
 * initiated). Both fire only while this listener is still the connection's packet
 * listener, so a session that became a real player (listener swapped to
 * {@code ServerGamePacketListenerImpl}) is never released here; the at-most-one
 * active registration per canonical identity is guaranteed by
 * {@code IdentityRegistry.register}.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginDisconnectMixin {

    @Shadow(remap = false)
    private GameProfile f_10021_;

    @Inject(method = "m_7026_", at = @At("HEAD"), remap = false)
    private void authsuite_releaseOnConnectionLost(Component reason, CallbackInfo ci) {
        ForgeServer.releaseLoginSession(this.f_10021_);
        LoginAttempt attempt = LoginAttemptStore.remove(this);
        if (attempt != null) {
            attempt.setState(LoginAttempt.State.DISCONNECTED);
        }
    }

    @Inject(method = "m_10053_", at = @At("HEAD"), remap = false)
    private void authsuite_releaseOnDisconnecting(Component reason, CallbackInfo ci) {
        ForgeServer.releaseLoginSession(this.f_10021_);
        LoginAttempt attempt = LoginAttemptStore.remove(this);
        if (attempt != null) {
            attempt.setState(LoginAttempt.State.DISCONNECTED);
        }
    }
}