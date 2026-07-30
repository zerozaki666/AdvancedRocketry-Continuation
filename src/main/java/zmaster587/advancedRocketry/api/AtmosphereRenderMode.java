package zmaster587.advancedRocketry.api;

import java.util.Locale;

/**
 * Selects the molecular-atmosphere rendering path without changing any
 * gameplay atmosphere semantics.
 */
public enum AtmosphereRenderMode {
	AUTO,
	FAST,
	HIGH,
	LEGACY,
	OFF;

	public static AtmosphereRenderMode parse(String value) {
		if(value != null) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			}
			catch(IllegalArgumentException ignored) {
			}
		}
		return AUTO;
	}
}
