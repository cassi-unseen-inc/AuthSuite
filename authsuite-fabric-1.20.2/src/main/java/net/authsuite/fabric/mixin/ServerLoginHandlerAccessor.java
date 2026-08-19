package net.authsuite.fabric.mixin;

import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the connecting player's claimed username (set from the earlier
 * hello packet) to the login-phase preference receiver so the herald can be
 * keyed by username. This Minecraft generation ({@code ServerLoginPacketListenerImpl}
 * post-1.20.2) stores the username in {@code requestedUsername}; the earlier
 * {@code gameProfile} field no longer exists.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginHandlerAccessor {

    @Accessor("requestedUsername")
    String authsuite$getRequestedUsername();
}