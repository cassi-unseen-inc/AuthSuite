package net.authsuite.neoforge.mixin;

import com.mojang.brigadier.ParseResults;
import net.authsuite.neoforge.command.OpCommandInterceptor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
            method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void authsuite_interceptOp(ParseResults<CommandSourceStack> results, String command, CallbackInfoReturnable<Integer> cir) {
        if (OpCommandInterceptor.preDispatch(results, command)) {
            cir.setReturnValue(0);
        }
    }
}