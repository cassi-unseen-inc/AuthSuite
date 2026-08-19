package net.authsuite.fabric.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.fabric.server.FabricServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Re-registers {@code op} and {@code deop} inside the Minecraft command framework,
 * overriding the vanilla commands. Syntax: {@code op <identity>} and
 * {@code deop <identity>}; the identity is resolved centrally through the
 * {@link net.authsuite.common.identity.IdentityResolver} ({@code @authsuite[...]},
 * {@code MA:cassi__confused}, or an unambiguous bare username), so the target no
 * longer needs to be online and no per-command provider selection is needed.
 */
public final class OpCommands {

    private OpCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, FabricServer server) {
        dispatcher.getRoot().getChildren().remove("op");
        dispatcher.getRoot().getChildren().remove("deop");

        dispatcher.register(Commands.literal("op")
                .requires(src -> src.hasPermission(3))
                .then(Commands.argument("identity", AuthIdentityArgument.identity(server))
                        .executes(ctx -> op(ctx, server, AuthIdentityArgument.getIdentity(ctx, "identity")))));

        dispatcher.register(Commands.literal("deop")
                .requires(src -> src.hasPermission(3))
                .then(Commands.argument("identity", AuthIdentityArgument.identity(server))
                        .executes(ctx -> deop(ctx, server, AuthIdentityArgument.getIdentity(ctx, "identity")))));
    }

    private static int op(CommandContext<CommandSourceStack> ctx, FabricServer server, HybridIdentity identity)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int level = source.hasPermission(4) ? 4 : server.minecraftServer().getOperatorUserPermissionLevel();
        GameProfile profile = new GameProfile(identity.minecraftUUID(), identity.username());
        server.opsRouter().opFor(identity.providerId(), profile, level, false);
        source.sendSuccess(Component.literal("Made " + identity.providerId() + ":" + identity.username()
                + " an operator (level " + level + ")"), true);
        return 1;
    }

    private static int deop(CommandContext<CommandSourceStack> ctx, FabricServer server, HybridIdentity identity)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        GameProfile profile = new GameProfile(identity.minecraftUUID(), identity.username());
        server.opsRouter().deopFor(identity.providerId(), profile);
        source.sendSuccess(Component.literal("Removed " + identity.providerId() + ":" + identity.username()
                + " from operators"), true);
        return 1;
    }
}