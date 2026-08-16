package net.authsuite.neoforge;

import net.authsuite.common.AuthSuiteConstants;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.neoforge.network.NeoForgeNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entrypoint for the AuthSuite mod.
 * <p>
 * Architecture mirrors the spec: server-side core lives in the common module; this
 * class only wires platform-specific lifecycle events, servers and network.
 */
@Mod(AuthSuiteConstants.MOD_ID)
public final class AuthSuiteNeoForge {

    public AuthSuiteNeoForge(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        AuthSuiteLogger log = AuthSuiteLoggerFactory.get();
        log.info("AuthSuite loading (NeoForge)");

        NeoForgeServer.init(modEventBus, modContainer, log);
        NeoForgeNetwork.init(modEventBus);
    }
}