package net.authsuite.fabric.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.fabric.server.FabricServer;
import net.authsuite.fabric.ops.OpsRouter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Intercepts {@code op}/{@code deop} invocations whose trailing token is a known
 * provider shortcode and routes them to the provider-isolated op store. Any
 * non-matching invocation (or the plain vanilla commands) falls through untouched.
 */
public final class OpCommandInterceptor {

    private OpCommandInterceptor() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The mixin on Commands.performCommand handles interception; nothing to
        // register here. The dispatcher is retained for future literal commands.
    }

    /**
     * Attempts to handle an op/deop command. Returns true if it was consumed
     * (a provider shortcode was detected), false to let vanilla run.
     */
    public static boolean tryHandle(CommandSourceStack source, String raw) {
        FabricServer server = FabricServer.get();
        if (server == null || server.opsRouter() == null) {
            return false;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        List<String> tokens = List.of(trimmed.split("\\s+"));
        if (tokens.size() < 3) {
            return false;
        }
        boolean op = tokens.get(0).equalsIgnoreCase("op") || tokens.get(0).equalsIgnoreCase("/op");
        boolean deop = tokens.get(0).equalsIgnoreCase("deop") || tokens.get(0).equalsIgnoreCase("/deop");
        if (!op && !deop) {
            return false;
        }
        String shortcode = tokens.get(tokens.size() - 1);
        String playerName = tokens.get(1);
        IdentityRegistry registry = server.identityRegistry();
        HybridIdentity identity = registry.byUsername(playerName)
                .map(reg -> reg.identity())
                .orElse(null);
        if (identity == null) {
            return false; // Not an AuthSuite identity; let vanilla try.
        }
        if (!server.providerManager().byShortcode(shortcode)
                .map(p -> p.providerId().equals(identity.providerId()))
                .orElse(false)) {
            return false; // Shortcode does not match this identity's provider.
        }

        int level = source.hasPermission(4) ? 4 : server.minecraftServer().getOperatorUserPermissionLevel();
        GameProfile profile = new GameProfile(identity.minecraftUUID(), identity.username());
        if (op) {
            server.opsRouter().opFor(identity.providerId(), profile, level, false);
            source.sendSuccess(new TextComponent("Made " + playerName + " an operator in provider "
                    + identity.providerId()), true);
        } else {
            server.opsRouter().deopFor(identity.providerId(), profile);
            source.sendSuccess(new TextComponent("Removed " + playerName + " from operators in provider "
                    + identity.providerId()), true);
        }
        return true;
    }

    /** Called from the CommandDispatcherMixin before vanilla execution. */
    public static boolean preDispatch(CommandSourceStack source, String command) {
        return tryHandle(source, command);
    }
}