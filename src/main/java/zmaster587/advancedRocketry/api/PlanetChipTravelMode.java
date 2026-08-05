package zmaster587.advancedRocketry.api;

import java.util.Locale;

/**
 * Selects how crewed rockets using a Planet Identification Chip travel
 * between landable dimensions.
 */
public enum PlanetChipTravelMode {
	DIRECT,
	MANUAL;

	public boolean usesFreeSpace() {
		return this == MANUAL;
	}

	public static PlanetChipTravelMode parse(String value) {
		if(value != null) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			}
			catch(IllegalArgumentException ignored) {
			}
		}
		return DIRECT;
	}
}
