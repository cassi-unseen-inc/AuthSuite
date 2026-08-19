package net.authsuite.forge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.forge.ForgeServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * AuthSuite administrative commands: {@code /authsuite identity <identity>} and
 * {@code /authsuite whoami}.
 */
public final class AuthSuiteCommands {

    private AuthSuiteCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ForgeServer server) {
        dispatcher.register(Commands.literal("authsuite")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("identity")
                        .then(Commands.argument("identity", AuthIdentityArgument.identity(server))
                                .executes(ctx -> identity(ctx, server, AuthIdentityArgument.getIdentity(ctx, "identity")))))
                .then(Commands.literal("whoami")
                        .executes(ctx -> whoami(ctx, server))));
    }

    private static int identity(CommandContext<CommandSourceStack> ctx, ForgeServer server, HybridIdentity identity)
            throws CommandSyntaxException {
        ctx.getSource().sendSuccess(() -> Component.literal(
                identity.username() + " -> " + identity.providerId() + ":" + identity.providerAccountId()
                        + " (canonical " + identity.minecraftUUID() + ")"), false);
        return 1;
    }

    private static int whoami(CommandContext<CommandSourceStack> ctx, ForgeServer server) throws CommandSyntaxException {
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