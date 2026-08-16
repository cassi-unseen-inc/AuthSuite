package net.authsuite.fabric.mixin;

import com.mojang.authlib.GameProfile;
import net.authsuite.fabric.server.FabricServer;
import net.authsuite.fabric.ops.OpsRouter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
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

    @Inject(method = "op(Lcom/mojang/authlib/GameProfile;)V", at = @At("HEAD"), cancellable = true)
    private void authsuite_op(GameProfile profile, CallbackInfo ci) {
        FabricServer server = FabricServer.get();
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

    @Inject(method = "deop(Lcom/mojang/authlib/GameProfile;)V", at = @At("HEAD"), cancellable = true)
    private void authsuite_deop(GameProfile profile, CallbackInfo ci) {
        FabricServer server = FabricServer.get();
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

    @Inject(method = "isOp(Lcom/mojang/authlib/GameProfile;)Z", at = @At("HEAD"), cancellable = true)
    private void authsuite_isOp(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        FabricServer server = FabricServer.get();
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
            method = "save(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/PlayerDataStorage;save(Lnet/minecraft/world/entity/player/Player;)V"))
    private void authsuite_routeSave(PlayerDataStorage vanillaStorage, Player player) {
        FabricServer server = FabricServer.get();
        if (server != null && server.playerDataRouter() != null && player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer) player;
            String providerId = server.playerDataRouter().providerOf(sp);
            if (!OpsRouter.PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
                server.playerDataRouter().save(sp);
                return;
            }
        }
        vanillaStorage.save(player);
    }

    @Redirect(
            method = "load(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/PlayerDataStorage;load(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag authsuite_routeLoad(PlayerDataStorage vanillaStorage, Player player) {
        FabricServer server = FabricServer.get();
        if (server != null && server.playerDataRouter() != null && player instanceof ServerPlayer) {
            ServerPlayer sp = (ServerPlayer) player;
            String providerId = server.playerDataRouter().providerOf(sp);
            if (!OpsRouter.PROVIDER_VANILLA.equalsIgnoreCase(providerId)) {
                return server.playerDataRouter().load(sp).orElse(null);
            }
        }
        return vanillaStorage.load(player);
    }
}