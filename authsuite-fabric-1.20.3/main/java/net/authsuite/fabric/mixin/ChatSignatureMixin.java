package net.authsuite.fabric.mixin;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.ChatTrustLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forces display of NOT_SECURE chat messages. AuthSuite strips chat keys (empty
 * ServicesKeySet) so all messages from authenticated provider players are
 * NOT_SECURE; without this the vanilla client silently drops them whenever
 * {@code onlyShowSecureChat} is enabled. Redirecting the trust check to "secure"
 * keeps every authenticated message visible.
 */
@Mixin(ChatListener.class)
public abstract class ChatSignatureMixin {

    @Redirect(
            method = "showMessageToPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatTrustLevel;isNotSecure()Z"))
    private boolean authsuite_forceDisplay(ChatTrustLevel instance) {
        return false;
    }
}