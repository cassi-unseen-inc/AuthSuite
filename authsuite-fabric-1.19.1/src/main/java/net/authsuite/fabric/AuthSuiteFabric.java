package net.authsuite.fabric;

import net.authsuite.common.log.AuthSuiteLogger;
import net.authsuite.fabric.server.FabricServer;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric platform bootstrap (both server and client entrypoints route here).
 */
public final class AuthSuiteFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AuthSuiteLogger log = AuthSuiteLoggerFactory.get();
        FabricServer.init(log);
    }
}