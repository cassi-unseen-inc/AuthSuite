package net.authsuite.forge.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.authsuite.common.identity.HybridIdentity;
import net.authsuite.common.identity.IdentityRegistry;
import net.authsuite.common.identity.IdentityResolver;
import net.authsuite.common.provider.AuthProvider;
import net.authsuite.forge.ForgeServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.Set;

/**
 * Brigadier argument type for an AuthSuite identity (post-audit §7, §8). Resolves
 * identities centrally through the {@link IdentityResolver}; a bare username is
 * deliberately treated as ambiguous (never arbitrarily selected) when several
 * providers host the same name.
 * <p>
 * Accepted forms:
 * <ul>
 *   <li>{@code @authsuite[identity=MA:cassi__confused]}</li>
 *   <li>{@code MA:cassi__confused} (provider id or shortcode qualifier)</li>
 *   <li>{@code cassi__confused} (bare; unambiguous only)</li>
 * </ul>
 */
public final class AuthIdentityArgument implements ArgumentType<HybridIdentity> {

    private static final SimpleCommandExceptionType ERROR_EXPECTED =
            new SimpleCommandExceptionType(new TextComponent("Expected an AuthSuite identity"));

    private static final SimpleCommandExceptionType ERROR_MALFORMED_AUTHSUITE =
            new SimpleCommandExceptionType(new TextComponent(
                    "Malformed @authsuite token; expected @authsuite[identity=PROVIDER:username]"));

    private final ForgeServer server;
    private final IdentityResolver resolver;
    private final IdentityRegistry registry;

    private AuthIdentityArgument(ForgeServer server) {
        this.server = server;
        this.resolver = server.identityResolver();
        this.registry = server.identityRegistry();
    }

    public static AuthIdentityArgument identity(ForgeServer server) {
        return new AuthIdentityArgument(server);
    }

    public static HybridIdentity getIdentity(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, HybridIdentity.class);
    }

    @Override
    public HybridIdentity parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String raw = reader.readUnquotedString();
        if (raw.isBlank()) {
            throw ERROR_EXPECTED.createWithContext(reader);
        }
        String token = unwrapAuthsuite(raw);
        Optional<IdentityResolver.QualifiedName> qualified = resolver.parseQualifiedName(token);
        if (qualified.isEmpty()) {
            throw ERROR_MALFORMED_AUTHSUITE.createWithContext(reader);
        }
        IdentityResolver.QualifiedName qn = qualified.get();
        if (qn.providerQualifier() != null) {
            Optional<IdentityRegistry.RegisteredIdentity> resolved =
                    resolver.resolveByQualifiedUsername(qn.providerQualifier(), qn.username());
            if (resolved.isPresent()) {
                return resolved.get().identity();
            }
            reader.setCursor(start);
            throw new SimpleCommandExceptionType(new TextComponent(
                    "No active " + qn.providerQualifier() + " identity for username '" + qn.username() + "'"))
                    .createWithContext(reader);
        }
        IdentityResolver.UsernameResult result = resolver.resolveByUsername(qn.username());
        switch (result.status()) {
            case FOUND -> {
                return result.identity().orElseThrow().identity();
            }
            case AMBIGUOUS -> {
                reader.setCursor(start);
                throw new SimpleCommandExceptionType(new TextComponent(
                        "Username '" + qn.username() + "' is ambiguous: " + result.candidates().size()
                                + " active identities share it; qualify with a provider (e.g. MA:"
                                + qn.username() + ")")).createWithContext(reader);
            }
            default -> {
                reader.setCursor(start);
                throw new SimpleCommandExceptionType(new TextComponent(
                        "No active identity for username '" + qn.username() + "'")).createWithContext(reader);
            }
        }
    }

    private static String unwrapAuthsuite(String raw) throws CommandSyntaxException {
        if (!raw.startsWith("@authsuite")) {
            return raw;
        }
        if (raw.equals("@authsuite")) {
            throw ERROR_MALFORMED_AUTHSUITE.create();
        }
        if (!raw.startsWith("@authsuite[") || !raw.endsWith("]")) {
            throw ERROR_MALFORMED_AUTHSUITE.create();
        }
        String inner = raw.substring("@authsuite[".length(), raw.length() - 1);
        String prefix = "identity=";
        if (!inner.startsWith(prefix) || inner.length() == prefix.length()) {
            throw ERROR_MALFORMED_AUTHSUITE.create();
        }
        return inner.substring(prefix.length());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        if (remaining.startsWith("@authsuite[identity=")) {
            String partial = remaining.substring("@authsuite[identity=".length());
            suggestTokens(builder, partial);
            return builder.buildFuture();
        }
        int colon = remaining.indexOf(':');
        if (colon > 0) {
            String qualifier = remaining.substring(0, colon).trim();
            String partial = remaining.substring(colon + 1);
            Optional<AuthProvider> provider = server.providerManager().byId(qualifier)
                    .or(() -> server.providerManager().byShortcode(qualifier.toUpperCase(Locale.ROOT)));
            if (provider.isPresent()) {
                String qualifierForm = provider.get().shortcode().toUpperCase(Locale.ROOT) + ":";
                for (String username : registry.usernames()) {
                    if (username.startsWith(partial)) {
                        builder.suggest(qualifierForm + username);
                    }
                }
            }
            return builder.buildFuture();
        }
        for (String username : registry.usernames()) {
            if (username.startsWith(remaining)) {
                builder.suggest(username);
            }
        }
        builder.suggest("@authsuite[identity=");
        return builder.buildFuture();
    }

    private void suggestTokens(SuggestionsBuilder builder, String partial) {
        int colon = partial.indexOf(':');
        if (colon > 0) {
            String qualifier = partial.substring(0, colon);
            String p = partial.substring(colon + 1);
            server.providerManager().byId(qualifier)
                    .or(() -> server.providerManager().byShortcode(qualifier.toUpperCase(Locale.ROOT)))
                    .ifPresent(provider -> {
                        String q = provider.shortcode().toUpperCase(Locale.ROOT) + ":";
                        for (String username : registry.usernames()) {
                            if (username.startsWith(p)) {
                                builder.suggest("@authsuite[identity=" + q + username + "]");
                            }
                        }
                    });
        } else {
            for (String username : registry.usernames()) {
                if (username.startsWith(partial)) {
                    builder.suggest("@authsuite[identity=" + username + "]");
                }
            }
        }
    }

    public Set<String> usernames() {
        return registry.usernames();
    }
}