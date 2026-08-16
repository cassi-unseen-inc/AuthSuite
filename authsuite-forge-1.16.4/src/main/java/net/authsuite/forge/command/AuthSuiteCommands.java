package net.authsuite.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.forge.ForgeServer;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;

/**
 * AuthSuite administrative commands: {@code /authsuite identity <player>} and
 * {@code /authsuite whoami}.
 */
public final class AuthSuiteCommands {

    private AuthSuiteCommands() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher, ForgeServer server) {
        dispatcher.register(Commands.literal("authsuite")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("identity")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> identity(ctx, server, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("whoami")
                        .executes(ctx -> whoami(ctx, server))));
    }

    private static int identity(CommandContext<CommandSource> ctx, ForgeServer server, ServerPlayerEntity target)
            throws CommandSyntaxException {
        IdentityRegistry registry = server.identityRegistry();
        HybridIdentity identity = registry.byUuid(target.getUUID())
                .map(reg -> reg.identity())
                .orElse(null);
        if (identity == null) {
            ctx.getSource().sendFailure(new StringTextComponent("No AuthSuite identity registered for " + target.getGameProfile().getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(new StringTextComponent(
                target.getGameProfile().getName() + " -> " + identity.providerId() + ":" + identity.providerAccountId()
                        + " (canonical " + identity.minecraftUUID() + ")"), false);
        return 1;
    }

    private static int whoami(CommandContext<CommandSource> ctx, ForgeServer server) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) {
            ctx.getSource().sendFailure(new StringTextComponent("Only players can use /authsuite whoami"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) ctx.getSource().getEntity();
        IdentityRegistry registry = server.identityRegistry();
        HybridIdentity identity = registry.byUuid(player.getUUID())
                .map(reg -> reg.identity())
                .orElse(null);
        if (identity == null) {
            ctx.getSource().sendFailure(new StringTextComponent("No AuthSuite identity registered for your session"));
            return 0;
        }
        ctx.getSource().sendSuccess(new StringTextComponent(
                "You are " + identity.providerId() + ":" + identity.providerAccountId()
                        + " (canonical " + identity.minecraftUUID() + ")"), false);
        return 1;
    }
}