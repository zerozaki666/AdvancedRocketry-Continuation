package zmaster587.advancedRocketry.client.render.blackhole;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import zmaster587.advancedRocketry.AdvancedRocketry;

/**
 * Session-scoped, rate-limited diagnostics for optional black-hole rendering.
 *
 * <p>Every failure in this package is recoverable: the caller drops to the
 * fixed-function renderer instead of allowing a shader problem to abort the
 * celestial pass.</p>
 */
final class BlackHoleDiagnostics {

	private static final Set<String> EMITTED = Collections.synchronizedSet(
			new HashSet<String>());

	private BlackHoleDiagnostics() {
	}

	static void warnOnce(String key, String message) {
		if(EMITTED.add(key))
			AdvancedRocketry.logger.warn("[BlackHoleRenderer] " + message);
	}

	static void warnOnce(String key, String message, Throwable throwable) {
		if(EMITTED.add(key))
			AdvancedRocketry.logger.warn("[BlackHoleRenderer] " + message,
					throwable);
	}
}
