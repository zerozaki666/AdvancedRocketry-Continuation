package zmaster587.advancedRocketry.api;

import java.util.Locale;

public enum BlackHoleLuminosityMode {
	UPSTREAM_COMPAT,
	ACCRETION_RATE;

	public static BlackHoleLuminosityMode parse(String value) {
		if(value != null) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			}
			catch(IllegalArgumentException ignored) {
			}
		}
		return UPSTREAM_COMPAT;
	}
}
