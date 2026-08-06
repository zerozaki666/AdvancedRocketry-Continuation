package zmaster587.advancedRocketry.integration.opencomputers;

import zmaster587.advancedRocketry.tile.station.StationAltitudeChangeRate;

/**
 * Numeric conversions shared by the station controller GUIs and their
 * OpenComputers callbacks. This class deliberately has no Minecraft, Forge,
 * or OpenComputers dependencies so its boundary behavior can be unit tested.
 */
public final class OpenComputersValueCodec {

	public static final double MINIMUM_ALTITUDE_KM = 2100.0D;
	public static final double MAXIMUM_ALTITUDE_KM = 40100.0D;
	public static final double ALTITUDE_STEP_KM = 200.0D;
	public static final int MINIMUM_ORBITAL_DISTANCE = 10;
	public static final int MAXIMUM_ORBITAL_DISTANCE = 200;

	public static final double MINIMUM_ROTATIONS_PER_HOUR = -60.0D;
	public static final double MAXIMUM_ROTATIONS_PER_HOUR = 60.0D;
	public static final double ROTATION_RATE_CONVERSION = 72000.0D;

	public static final double MINIMUM_GRAVITY_MULTIPLIER = 0.10D;
	public static final double MAXIMUM_GRAVITY_MULTIPLIER = 1.00D;
	public static final int MINIMUM_GRAVITY_PERCENT = 10;
	public static final int MAXIMUM_GRAVITY_PERCENT = 100;

	private OpenComputersValueCodec() {
	}

	public static boolean isFinite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	public static boolean isInRange(double value, double minimum,
			double maximum) {
		return value >= minimum && value <= maximum;
	}

	public static int altitudeProgressFromKm(double altitudeKm) {
		int orbitalDistance = clamp((int)Math.round(
				(altitudeKm-100.0D)/ALTITUDE_STEP_KM),
				MINIMUM_ORBITAL_DISTANCE, MAXIMUM_ORBITAL_DISTANCE);
		return orbitalDistance-MINIMUM_ORBITAL_DISTANCE;
	}

	public static int altitudeKmFromProgress(int progress) {
		int orbitalDistance = clamp(progress+MINIMUM_ORBITAL_DISTANCE,
				MINIMUM_ORBITAL_DISTANCE, MAXIMUM_ORBITAL_DISTANCE);
		return (int)(orbitalDistance*ALTITUDE_STEP_KM+100.0D);
	}

	public static double currentAltitudeKm(double orbitalDistance) {
		return orbitalDistance*ALTITUDE_STEP_KM+100.0D;
	}

	public static int changeRateProgressFromMultiplier(double multiplier) {
		return StationAltitudeChangeRate.getSliderProgress(multiplier);
	}

	public static double changeRateMultiplierFromProgress(int progress) {
		return StationAltitudeChangeRate.getMultiplier(progress);
	}

	public static int rotationRateFromInput(double rotationsPerHour) {
		return clamp((int)Math.round(rotationsPerHour),
				(int)MINIMUM_ROTATIONS_PER_HOUR,
				(int)MAXIMUM_ROTATIONS_PER_HOUR);
	}

	public static double rotationsPerHourFromCyclesPerTick(
			double cyclesPerTick) {
		return cyclesPerTick*ROTATION_RATE_CONVERSION;
	}

	public static double cyclesPerTickFromRotationsPerHour(
			double rotationsPerHour) {
		return rotationsPerHour/ROTATION_RATE_CONVERSION;
	}

	public static double angleDegreesFromRotationCycles(
			double rotationCycles) {
		double degrees = rotationCycles*360.0D;
		return ((degrees%360.0D)+360.0D)%360.0D;
	}

	public static int gravityPercentFromMultiplier(double multiplier) {
		return clamp((int)Math.round(multiplier*100.0D),
				MINIMUM_GRAVITY_PERCENT, MAXIMUM_GRAVITY_PERCENT);
	}

	public static double gravityMultiplierFromPercent(int percent) {
		return clamp(percent, MINIMUM_GRAVITY_PERCENT,
				MAXIMUM_GRAVITY_PERCENT)/100.0D;
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
