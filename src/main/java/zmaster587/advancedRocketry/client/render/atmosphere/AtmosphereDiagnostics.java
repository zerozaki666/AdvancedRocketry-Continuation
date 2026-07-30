package zmaster587.advancedRocketry.client.render.atmosphere;

import java.util.HashSet;
import java.util.Set;

import zmaster587.advancedRocketry.AdvancedRocketry;

/**
 * Warn-once diagnostics for the optional atmosphere renderer.  A broken
 * shader or unavailable GL feature must degrade visuals, never the client.
 */
final class AtmosphereDiagnostics {

	private static final Set<String> WARNED_KEYS = new HashSet<String>();

	private AtmosphereDiagnostics() {
	}

	static synchronized void warnOnce(String key, String message) {
		if(WARNED_KEYS.add(key))
			AdvancedRocketry.logger.warn("[Atmosphere renderer] " + message);
	}

	static synchronized void warnOnce(String key, String message,
			Throwable throwable) {
		if(WARNED_KEYS.add(key))
			AdvancedRocketry.logger.warn("[Atmosphere renderer] " + message,
					throwable);
	}

	static synchronized void resetResourceWarnings() {
		WARNED_KEYS.clear();
	}
}
