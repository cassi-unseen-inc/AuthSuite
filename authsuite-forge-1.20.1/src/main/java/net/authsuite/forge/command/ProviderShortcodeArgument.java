package net.authsuite.forge.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.authsuite.common.provider.AuthProvider;
import net.authsuite.forge.ForgeServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier argument type for a provider shortcode. Suggestions only include
 * shortcodes of currently enabled providers.
 */
public final class ProviderShortcodeArgument implements ArgumentType<String> {

    private static final SimpleCommandExceptionType ERROR_UNKNOWN_SHORTCODE =
            new SimpleCommandExceptionType(Component.literal("Unknown provider shortcode"));

    private final ForgeServer server;

    private ProviderShortcodeArgument(ForgeServer server) {
        this.server = server;
    }

    public static ProviderShortcodeArgument shortcodes(ForgeServer server) {
        return new ProviderShortcodeArgument(server);
    }

    public static String getShortcode(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String raw = reader.readUnquotedString();
        if (raw.isBlank()) {
            throw new SimpleCommandExceptionType(Component.literal("Expected a provider shortcode")).create();
        }
        String shortcode = raw.toUpperCase(Locale.ROOT);
        if (server.providerManager().byShortcode(shortcode).isEmpty()) {
            reader.setCursor(start);
            throw ERROR_UNKNOWN_SHORTCODE.createWithContext(reader);
        }
        return shortcode;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (AuthProvider provider : server.providerManager().enabled()) {
            String shortcode = provider.shortcode().toUpperCase(Locale.ROOT);
            if (shortcode.startsWith(remaining)) {
                builder.suggest(shortcode);
            }
        }
        return builder.buildFuture();
    }
}