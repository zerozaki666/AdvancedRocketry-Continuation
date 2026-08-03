package zmaster587.advancedRocketry.integration.opencomputers;

import java.util.Locale;

import zmaster587.advancedRocketry.api.fuel.FuelRegistry.FuelType;

/** Parses the stable fuel names exposed by the monitoring station API. */
public final class OpenComputersFuelTypeCodec {

	private OpenComputersFuelTypeCodec() {
	}

	public static FuelType parse(String value) {
		if(value == null)
			return null;
		String normalized = value.trim().toUpperCase(Locale.ENGLISH);
		try {
			return FuelType.valueOf(normalized);
		}
		catch(IllegalArgumentException ignored) {
			return null;
		}
	}

	public static String name(FuelType type) {
		return type == null ? "" : type.name().toLowerCase(Locale.ENGLISH);
	}
}
