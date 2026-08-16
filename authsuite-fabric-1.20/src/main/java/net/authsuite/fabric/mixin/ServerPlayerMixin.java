package net.authsuite.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.authsuite.fabric.server.FabricServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes {@code ServerPlayer#getPermissionLevel} through the {@code OpsRouter} so
 * external provider operators resolve their level from the provider-isolated
 * {@code ops.json} while Microsoft players keep the vanilla flow.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "getPermissionLevel", at = @At("HEAD"), cancellable = true)
    private void authsuite_permissionLevel(CallbackInfoReturnable<Integer> cir) {
        FabricServer server = FabricServer.get();
        if (server == null || server.opsRouter() == null) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        int level = server.opsRouter().effectiveLevel(self.getGameProfile());
        cir.setReturnValue(level);
    }
}