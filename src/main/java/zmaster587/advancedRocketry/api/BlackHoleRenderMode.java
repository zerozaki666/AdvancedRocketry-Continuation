package zmaster587.advancedRocketry.api;

import java.util.Locale;

public enum BlackHoleRenderMode {
	AUTO,
	HIGH,
	FAST,
	LEGACY;

	public static BlackHoleRenderMode parse(String value) {
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
