package net.authsuite.common.skin;

import net.authsuite.common.config.AuthSuiteConfig;
import net.authsuite.common.config.ProviderConfig;
import net.authsuite.common.log.AuthSuiteLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §4: client skin fetches must be validated against the provider policy;
 * only HTTPS URLs on an allowlisted host for the provider may be rendered.
 */
class SkinPolicyTest {

    private AuthSuiteConfig config;
    private SkinPolicy policy;

    @BeforeEach
    void setUp() {
        config = new AuthSuiteConfig();
        config.setProviders(List.of(
                ProviderConfig.of("littleskins", "LS", "littleskin.cn",
                        "https://api.littleskin.cn/", "https://api.littleskin.cn/", ""),
                ProviderConfig.of("elyby", "EB", "ely.by",
                        "https://auth.ely.by/", "https://auth.ely.by/", "")));
        policy = new SkinPolicy(config, AuthSuiteLogger.noop());
    }

    @Test
    void allowlistedHttpsUrlIsAllowed() {
        SkinPolicy.ValidationResult result = policy.validate(
                "littleskins", new SkinResource("https://littleskin.cn/skins/a.png"), 0);
        assertTrue(result.allowed());
    }

    @Test
    void wwwSubdomainIsAllowed() {
        assertTrue(policy.validate(
                "elyby", new SkinResource("https://www.ely.by/skin.png"), 0).allowed());
    }

    @Test
    void httpIsRejected() {
        assertFalse(policy.validate(
                "littleskins", new SkinResource("http://skins.littleskin.cn/a.png"), 0).allowed());
    }

    @Test
    void foreignHostIsRejected() {
        assertFalse(policy.validate(
                "littleskins", new SkinResource("https://evil.example.com/a.png"), 0).allowed());
    }

    @Test
    void unknownProviderHasNoAllowedHosts() {
        assertFalse(policy.validate(
                "unknown", new SkinResource("https://skins.littleskin.cn/a.png"), 0).allowed());
    }
}