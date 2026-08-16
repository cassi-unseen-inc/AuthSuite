package net.authsuite.forge;

import net.authsuite.common.AuthSuiteConstants;
import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.forge.network.ForgeNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entrypoint for the AuthSuite mod.
 * <p>
 * Architecture mirrors the spec: server-side core lives in the common module; this
 * class only wires platform-specific lifecycle events, servers and network.
 */
@Mod(AuthSuiteConstants.MOD_ID)
public final class AuthSuiteForge {

    public AuthSuiteForge(IEventBus modEventBus, net.minecraftforge.fml.ModContainer modContainer) {
        AuthSuiteLogger log = AuthSuiteLoggerFactory.get();
        log.info("AuthSuite loading (Forge)");

        ForgeServer.init(modEventBus, log);
        ForgeNetwork.init();
    }
}