package net.authsuite.forge.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.authsuite.forge.ForgeServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes {@code ServerPlayerEntity#func_184840_I} through the {@code OpsRouter}
 * so external provider operators resolve their level from the provider-isolated
 * {@code ops.json} while Microsoft players keep the vanilla flow.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "func_184840_I", at = @At("HEAD"), cancellable = true, remap = false)
    private void authsuite_permissionLevel(CallbackInfoReturnable<Integer> cir) {
        ForgeServer server = ForgeServer.get();
        if (server == null || server.opsRouter() == null) {
            return;
        }
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        int level = server.opsRouter().effectiveLevel(self.getGameProfile());
        cir.setReturnValue(level);
    }
}