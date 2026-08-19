package net.authsuite.fabric.mixin;

import net.authsuite.common.login.LoginAttempt;
import net.authsuite.common.login.LoginAttemptStore;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges the connection-scoped {@link LoginAttempt} from the netty thread that
 * receives the client's provider preference to the "User Authenticator" thread
 * that runs {@code hasJoinedServer} (post-audit §5).
 * <p>
 * {@code handleKey} constructs the authenticator {@code Thread}
 * ({@code ServerLoginPacketListenerImpl$1}) at its tail. The
 * {@code InheritableThreadLocal} is populated immediately BEFORE that thread is
 * constructed (so the value is copied into the child thread at construction) and
 * cleared immediately AFTER (so the netty thread never leaks the attempt to an
 * unrelated connection). The preference therefore belongs to one connection, not
 * to a username.
 * <p>
 * Mojmap names; remapped to intermediary at runtime via the Fabric refmap.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginAttemptMixin {

    private static final String AUTHENTICATOR_CTOR =
            "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl$1;"
                    + "<init>(Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;"
                    + "Ljava/lang/String;Ljava/lang/String;)V";

    @Inject(method = "handleKey",
            at = @At(value = "INVOKE", target = AUTHENTICATOR_CTOR),
            require = 1)
    private void authsuite$pushAttempt(CallbackInfo ci) {
        LoginAttempt attempt = LoginAttemptStore.forConnection(this);
        LoginAttemptStore.push(attempt);
    }

    @Inject(method = "handleKey",
            at = @At(value = "INVOKE", target = AUTHENTICATOR_CTOR, shift = At.Shift.AFTER),
            require = 1)
    private void authsuite$clearPushedAttempt(CallbackInfo ci) {
        LoginAttemptStore.clearPushed();
    }
}