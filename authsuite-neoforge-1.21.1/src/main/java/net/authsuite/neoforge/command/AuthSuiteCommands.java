package net.authsuite.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.neoforge.NeoForgeServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * AuthSuite administrative commands: {@code /authsuite identity <player>} and
 * {@code /authsuite whoami}.
 */
public final class AuthSuiteCommands {

    private AuthSuiteCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, NeoForgeServer server) {
        dispatcher.register(Commands.literal("authsuite")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("identity")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> identity(ctx, server, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("whoami")
                        .executes(ctx -> whoami(ctx, server))));
    }

    private static int identity(CommandContext<CommandSourceStack> ctx, NeoForgeServer server, ServerPlayer target)
            throws CommandSyntaxException {
        IdentityRegistry registry = server.identityRegistry();
        HybridIdentity identity = registry.byUuid(target.getUUID())
                .map(reg -> reg.identity())
                .orElse(null);
        if (identity == null) {
            ctx.getSource().sendFailure(Component.literal("No AuthSuite identity registered for " + target.getGameProfile().getName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                target.getGameProfile().getName() + " -> " + identity.providerId() + ":" + identity.providerAccountId()
                        + " (canonical " + identity.minecraftUUID() + ")"), false);
        return 1;
    }

    private static int whoami(CommandContext<CommandSourceStack> ctx, NeoForgeServer server) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only players can use /authsuite whoami"));
            return 0;
        }
        IdentityRegistry registry = server.identityRegistry();
        HybridIdentity identity = registry.byUuid(player.getUUID())
                .map(reg -> reg.identity())
                .orElse(null);
        if (identity == null) {
            ctx.getSource().sendFailure(Component.literal("No AuthSuite identity registered for your session"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "You are " + identity.providerId() + ":" + identity.providerAccountId()
                        + " (canonical " + identity.minecraftUUID() + ")"), false);
        return 1;
    }
}