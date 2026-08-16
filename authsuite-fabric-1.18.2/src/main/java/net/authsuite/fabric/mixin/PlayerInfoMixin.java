package net.authsuite.fabric.mixin;

import net.authsuite.common.skin.SkinDirective;
import net.authsuite.fabric.client.AuthSuiteFabricClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side skin override: when an authoritative {@link SkinDirective} is present
 * for a player, report the provider model ("slim"/"default") so the renderer
 * selects the correct geometry. Texture itself is already served through the
 * GameProfile {@code textures} property placed by {@code LoginProfileBuilder}.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void authsuite_getModelName(CallbackInfoReturnable<String> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        AuthSuiteFabricClient client = AuthSuiteFabricClient.get();
        if (client == null) {
            return;
        }
        SkinDirective directive = client.directiveFor(self.getProfile().getId());
        if (directive == null || directive.modelType() == null) {
            return;
        }
        cir.setReturnValue("slim".equalsIgnoreCase(directive.modelType()) ? "slim" : "default");
    }
}