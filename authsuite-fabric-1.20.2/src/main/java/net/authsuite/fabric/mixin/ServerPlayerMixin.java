package net.authsuite.fabric.mixin;

import net.authsuite.fabric.server.FabricServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Routes {@code ServerPlayer#getPermissionLevel} through the {@code OpsRouter} so
 * external provider operators resolve their level from the provider-isolated
 * {@code ops.json} while Microsoft players keep the vanilla flow.
 * <p>
 * Also pins the provider shortcode to the player's display name, so e.g. a
 * {@code cassi__confused} from Microsoft ({@code [MA]}) and one from cassicloud
 * ({@code [CC]}) are distinguishable in chat and join messages.
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

    @Inject(method = "getDisplayName()Lnet/minecraft/network/chat/Component;", at = @At("HEAD"), cancellable = true)
    private void authsuite_pinShortcode(CallbackInfoReturnable<Component> cir) {
        String shortcode = authsuiteShortcode();
        if (shortcode == null) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        Component name = PlayerTeam.formatNameForTeam(self.getTeam(), self.getName());
        cir.setReturnValue(Component.literal("[" + shortcode + "] ")
                .withStyle(ChatFormatting.GRAY)
                .append(name));
    }

    private String authsuiteShortcode() {
        FabricServer server = FabricServer.get();
        if (server == null) {
            return null;
        }
        UUID uuid = ((ServerPlayer) (Object) this).getUUID();
        return server.identityRegistry().byUuid(uuid)
                .flatMap(reg -> server.providerManager().byId(reg.identity().providerId()))
                .map(provider -> provider.shortcode())
                .orElse(null);
    }
}