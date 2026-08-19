package net.authsuite.fabric.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.provider.AuthProvider;
import net.authsuite.fabric.server.FabricServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Re-registers {@code op} and {@code deop} inside the Minecraft command framework,
 * overriding the vanilla commands. Syntax: {@code op <player> <shortcode>} and
 * {@code deop <player> <shortcode>}; the trailing provider shortcode selects the
 * provider-isolated op store. Suggestions restrict the shortcode to enabled providers.
 */
public final class OpCommands {

    private OpCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, FabricServer server) {
        dispatcher.getRoot().getChildren().remove("op");
        dispatcher.getRoot().getChildren().remove("deop");

        dispatcher.register(Commands.literal("op")
                .requires(src -> src.hasPermission(3))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("provider", ProviderShortcodeArgument.shortcodes(server))
                                .executes(ctx -> op(ctx, server, EntityArgument.getPlayer(ctx, "player"))))));

        dispatcher.register(Commands.literal("deop")
                .requires(src -> src.hasPermission(3))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("provider", ProviderShortcodeArgument.shortcodes(server))
                                .executes(ctx -> deop(ctx, server, EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int op(CommandContext<CommandSourceStack> ctx, FabricServer server, ServerPlayer target)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        HybridIdentity identity = identityFor(server, target);
        if (identity == null) {
            source.sendFailure(Component.literal("No AuthSuite identity registered for " + target.getGameProfile().getName()));
            return 0;
        }
        String shortcode = ProviderShortcodeArgument.getShortcode(ctx, "provider");
        Optional<AuthProvider> provider = server.providerManager().byShortcode(shortcode);
        if (provider.isEmpty() || !provider.get().providerId().equals(identity.providerId())) {
            source.sendFailure(Component.literal("Shortcode '" + shortcode + "' does not match "
                    + target.getGameProfile().getName() + "'s provider (" + identity.providerId() + ")"));
            return 0;
        }
        int level = source.hasPermission(4) ? 4 : server.minecraftServer().getOperatorUserPermissionLevel();
        GameProfile profile = new GameProfile(identity.minecraftUUID(), identity.username());
        server.opsRouter().opFor(identity.providerId(), profile, level, false);
        source.sendSuccess(() -> Component.literal("Made " + target.getGameProfile().getName()
                + " an operator (level " + level + ") in provider " + identity.providerId()), true);
        return 1;
    }

    private static int deop(CommandContext<CommandSourceStack> ctx, FabricServer server, ServerPlayer target)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        HybridIdentity identity = identityFor(server, target);
        if (identity == null) {
            source.sendFailure(Component.literal("No AuthSuite identity registered for " + target.getGameProfile().getName()));
            return 0;
        }
        String shortcode = ProviderShortcodeArgument.getShortcode(ctx, "provider");
        Optional<AuthProvider> provider = server.providerManager().byShortcode(shortcode);
        if (provider.isEmpty() || !provider.get().providerId().equals(identity.providerId())) {
            source.sendFailure(Component.literal("Shortcode '" + shortcode + "' does not match "
                    + target.getGameProfile().getName() + "'s provider (" + identity.providerId() + ")"));
            return 0;
        }
        GameProfile profile = new GameProfile(identity.minecraftUUID(), identity.username());
        server.opsRouter().deopFor(identity.providerId(), profile);
        source.sendSuccess(() -> Component.literal("Removed " + target.getGameProfile().getName()
                + " from operators in provider " + identity.providerId()), true);
        return 1;
    }

    private static HybridIdentity identityFor(FabricServer server, ServerPlayer target) {
        return server.identityRegistry().byUuid(target.getUUID())
                .map(reg -> reg.identity())
                .orElse(null);
    }
}