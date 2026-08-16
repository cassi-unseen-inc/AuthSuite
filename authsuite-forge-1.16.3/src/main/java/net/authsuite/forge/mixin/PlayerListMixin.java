package net.authsuite.forge.mixin;

import com.mojang.authlib.GameProfile;
import net.authsuite.forge.ForgeServer;
import net.authsuite.forge.ops.OpsRouter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.storage.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Routes {@code op}/{@code deop}/{@code isOp} and player data I/O to the
 * provider-isolated stores for external provider identities; Microsoft identities
 * and unknown profiles keep the vanilla behavior (fall-through).
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "func_152605_a(Lcom/mojang/authlib/GameProfile;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void authsuite_op(GameProfile profile, CallbackInfo ci) {
        ForgeServer server = ForgeServer.get();
        if (server == null || server.opsRouter() == null) {
            return;
        }
        String providerId = server.opsRouter().providerOf(profile);
        if (OpsRouter.PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            return; // vanilla flow
        }
        PlayerList self = (PlayerList) (Object) this;
        int level = self.getServer().getOperatorUserPermissionLevel();
        server.opsRouter().opFor(providerId, profile, level, false);
        ci.cancel();
    }

    @Inject(method = "defunc_152605_a(Lcom/mojang/authlib/GameProfile;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void authsuite_deop(GameProfile profile, CallbackInfo ci) {
        ForgeServer server = ForgeServer.get();
        if (server == null || server.opsRouter() == null) {
            return;
        }
        String providerId = server.opsRouter().providerOf(profile);
        if (OpsRouter.PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            return; // vanilla flow
        }
        server.opsRouter().deopFor(providerId, profile);
        ci.cancel();
    }

    @Inject(method = "func_152596_g(Lcom/mojang/authlib/GameProfile;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void authsuite_isOp(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        ForgeServer server = ForgeServer.get();
        if (server == null || server.opsRouter() == null) {
            return;
        }
        String providerId = server.opsRouter().providerOf(profile);
        if (OpsRouter.PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
            return; // vanilla flow
        }
        cir.setReturnValue(server.opsRouter().isOp(profile));
    }

    // ---- player data routing ----

    @Redirect(
            method = "func_72391_b(Lnet/minecraft/entity/player/ServerPlayerEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/PlayerData;func_237335_a_(Lnet/minecraft/entity/player/PlayerEntity;)V"),
            remap = false)
    private void authsuite_routeSave(PlayerData vanillaStorage, PlayerEntity player) {
        ForgeServer server = ForgeServer.get();
        if (server != null && server.playerDataRouter() != null && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            String providerId = server.playerDataRouter().providerOf(sp);
            if (!OpsRouter.PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
                server.playerDataRouter().save(sp);
                return;
            }
        }
        vanillaStorage.save(player);
    }

    @Redirect(
            method = "func_72380_a(Lnet/minecraft/entity/player/ServerPlayerEntity;)Lnet/minecraft/nbt/CompoundNBT;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/storage/PlayerData;func_237336_b_(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/nbt/CompoundNBT;"),
            remap = false)
    private CompoundNBT authsuite_routeLoad(PlayerData vanillaStorage, PlayerEntity player) {
        ForgeServer server = ForgeServer.get();
        if (server != null && server.playerDataRouter() != null && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            String providerId = server.playerDataRouter().providerOf(sp);
            if (!OpsRouter.PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
                return server.playerDataRouter().load(sp).orElse(null);
            }
        }
        return vanillaStorage.load(player);
    }
}