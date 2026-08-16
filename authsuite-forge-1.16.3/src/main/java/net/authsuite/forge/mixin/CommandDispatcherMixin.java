package net.authsuite.forge.mixin;

import net.authsuite.forge.command.OpCommandInterceptor;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@code op}/{@code deop} with a trailing provider shortcode before
 * vanilla command execution. Both the console ({@code performPrefixedCommand}) and
 * chat paths funnel through {@link Commands#performCommand}. Non-matching commands
 * fall through untouched (defaultRequire 0 keeps this lenient).
 */
@Mixin(Commands.class)
public abstract class CommandDispatcherMixin {

    @Inject(
            method = "func_197059_a(Lnet/minecraft/command/CommandSource;Ljava/lang/String;)I",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void authsuite_interceptOp(CommandSource source, String command, CallbackInfoReturnable<Integer> cir) {
        if (OpCommandInterceptor.preDispatch(source, command)) {
            cir.setReturnValue(0);
        }
    }
}