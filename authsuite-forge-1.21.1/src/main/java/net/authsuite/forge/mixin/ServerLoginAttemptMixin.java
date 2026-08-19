package net.authsuite.forge.mixin;

import net.authsuite.common.login.LoginAttempt;
import net.authsuite.common.login.LoginAttemptStore;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges the connection-scoped {@link LoginAttempt} from the netty thread that
 * receives the login to the "User Authenticator" thread that runs the session
 * check (post-audit §5).
 * <p>
 * {@code handleKey} constructs the authenticator {@code Thread}
 * ({@code ServerLoginPacketListenerImpl$1}) at its tail. The
 * {@code InheritableThreadLocal} is populated immediately BEFORE that thread is
 * constructed (so the value is copied into the child thread at construction) and
 * cleared immediately AFTER (so the netty thread never leaks the attempt to an
 * unrelated connection). The attempt therefore belongs to one connection, not to
 * a username.
 * <p>
 * Forge 1.21.1 runs on official (Mojang) names at runtime; {@code remap=false}.
 * Deviation from the 1.20.4 reference: in this version the authenticator
 * {@code Thread} is constructed with an explicit {@code ThreadGroup}, so the
 * {@code $1} ctor descriptor carries the extra parameter.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginAttemptMixin {

    private static final String AUTHENTICATOR_CTOR =
            "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl$1;"
                    + "<init>(Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;"
                    + "Ljava/lang/ThreadGroup;Ljava/lang/String;Ljava/lang/String;)V";

    @Inject(method = "handleKey", at = @At(value = "INVOKE", target = AUTHENTICATOR_CTOR),
            remap = false, require = 1)
    private void authsuite$pushAttempt(CallbackInfo ci) {
        LoginAttempt attempt = LoginAttemptStore.forConnection(this);
        LoginAttemptStore.push(attempt);
    }

    @Inject(method = "handleKey", at = @At(value = "INVOKE", target = AUTHENTICATOR_CTOR, shift = At.Shift.AFTER),
            remap = false, require = 1)
    private void authsuite$clearPushedAttempt(CallbackInfo ci) {
        LoginAttemptStore.clearPushed();
    }
}