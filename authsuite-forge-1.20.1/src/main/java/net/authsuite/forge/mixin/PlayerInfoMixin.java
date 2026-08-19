package net.authsuite.forge.mixin;

import net.authsuite.common.skin.SkinDirective;
import net.authsuite.common.skin.SkinResource;
import net.authsuite.forge.client.AuthSuiteClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side skin override: when an authoritative {@link SkinDirective} is present
 * for a player, report the provider texture ({@code getSkinTextureLocation}) and
 * provider model ({@code getModelName}) so the renderer shows the alternative-auth
 * skin instead of the vanilla/default one.
 * <p>
 * Targets use SRG names ({@code m_105337_}, {@code m_105336_}) with {@code remap=false}
 * because the production jar is reobfuscated; vanilla 1.20.1 cannot render
 * non-Mojang texture hosts (authlib's {@code TextureUrlChecker}), so the directive
 * path is the authoritative renderer.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Inject(method = "m_105337_", at = @At("HEAD"), cancellable = true, remap = false)
    private void authsuite_getSkinTextureLocation(CallbackInfoReturnable<ResourceLocation> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        AuthSuiteClient client = AuthSuiteClient.get();
        if (client == null) {
            return;
        }
        SkinDirective directive = client.directiveFor(self.getProfile().getId());
        if (directive == null) {
            return;
        }
        SkinResource skin = directive.skinResource();
        if (skin == null) {
            return;
        }
        ResourceLocation location = client.resolveSkinLocation(directive);
        if (location != null) {
            cir.setReturnValue(location);
        }
    }

    @Inject(method = "m_105336_", at = @At("HEAD"), cancellable = true, remap = false)
    private void authsuite_getModelName(CallbackInfoReturnable<String> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        AuthSuiteClient client = AuthSuiteClient.get();
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