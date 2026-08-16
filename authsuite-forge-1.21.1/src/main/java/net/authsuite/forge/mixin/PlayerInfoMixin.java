package net.authsuite.forge.mixin;

import net.authsuite.common.skin.SkinDirective;
import net.authsuite.forge.client.AuthSuiteClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side skin override: when an authoritative {@link SkinDirective} is present
 * for a player, report the provider model so the renderer selects the correct
 * (slim/wide) geometry. Texture itself is already served through the GameProfile
 * {@code textures} property placed by {@code LoginProfileBuilder}.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true, remap = false)
    private void authsuite_getSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        AuthSuiteClient client = AuthSuiteClient.get();
        if (client == null) {
            return;
        }
        SkinDirective directive = client.directiveFor(self.getProfile().getId());
        if (directive == null || directive.modelType() == null) {
            return;
        }
        PlayerSkin.Model model = "slim".equalsIgnoreCase(directive.modelType())
                ? PlayerSkin.Model.SLIM
                : PlayerSkin.Model.WIDE;
        PlayerSkin original = cir.getReturnValue();
        if (original != null && original.model() != model) {
            cir.setReturnValue(new PlayerSkin(
                    original.texture(), original.textureUrl(), original.capeTexture(),
                    original.elytraTexture(), model, original.secure()));
        }
    }
}