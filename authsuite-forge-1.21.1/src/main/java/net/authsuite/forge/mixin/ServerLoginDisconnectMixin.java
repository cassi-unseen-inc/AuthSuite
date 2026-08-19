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
 * is then dropped during login - e.g. mod-channel negotiation kick for a client
 * without the mod, or a client disconnect right after auth - otherwise leaves the
 * canonical identity "active" forever, blocking every subsequent login with
 * "Failed to verify username!" until the server restarts.
 * <p>
 * Forge 1.21.1 runs on official (Mojang) names at runtime; {@code remap=false}.
 * The login listener exposes the verified profile as {@code authenticatedProfile}
 * (set after a successful session check); it is {@code null} for connections that
 * never authenticated, so those are a no-op here. The at-most-one active
 * registration per canonical identity is guaranteed by {@code IdentityRegistry.register}.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginDisconnectMixin {

    @Shadow(remap = false)
    private GameProfile authenticatedProfile;

    @Inject(method = "disconnect", at = @At("HEAD"), remap = false)
    private void authsuite_releaseOnDisconnect(Component reason, CallbackInfo ci) {
        ForgeServer.releaseLoginSession(this.authenticatedProfile);
        LoginAttempt attempt = LoginAttemptStore.remove(this);
        if (attempt != null) {
            attempt.setState(LoginAttempt.State.DISCONNECTED);
        }
    }
}