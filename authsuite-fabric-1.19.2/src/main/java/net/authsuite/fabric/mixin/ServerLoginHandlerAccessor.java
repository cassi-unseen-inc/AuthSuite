package net.authsuite.fabric.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the connecting player's {@link GameProfile} (set from the earlier
 * hello packet) to the login-phase preference receiver so the herald can be
 * keyed by username.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginHandlerAccessor {

    @Accessor("gameProfile")
    GameProfile authsuite$getGameProfile();
}