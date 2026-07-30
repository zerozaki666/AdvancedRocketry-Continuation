package zmaster587.advancedRocketry.tile.station;

/**
 * Maps the Altitude Controller's rate slider to a multiplier for its legacy
 * per-tick altitude step.
 *
 * Progress zero deliberately resolves to exactly 1x so old controllers and
 * worlds retain their existing movement rate.  The 0.5x increments keep the
 * minimum reachable with LibVulpes' legacy slider hit-testing while still
 * providing fine control over the complete 1x-10x range.
 */
public final class StationAltitudeChangeRate {

	public static final int SLIDER_STEPS = 18;
	public static final double MINIMUM_MULTIPLIER = 1.0D;
	public static final double MAXIMUM_MULTIPLIER = 10.0D;

	private static final double MULTIPLIER_PER_STEP = 0.5D;
	private static final double LEGACY_MAXIMUM_STEP = 0.1D;

	private StationAltitudeChangeRate() {
	}

	public static int clampSliderProgress(int progress) {
		return Math.max(0, Math.min(SLIDER_STEPS, progress));
	}

	public static double getMultiplier(int sliderProgress) {
		return MINIMUM_MULTIPLIER
				+ MULTIPLIER_PER_STEP
				*clampSliderProgress(sliderProgress);
	}

	/**
	 * Preserves the old altitude falloff through orbital distance 190.  The
	 * legacy expression reached zero at 191 and became negative above it even
	 * though the Target Altitude slider legally reaches 200.  A one-unit
	 * numerator is therefore used only in that previously broken upper range.
	 */
	public static double getLegacyStep(int targetSliderSteps,
			double currentOrbitalDistance) {
		if(targetSliderSteps <= 0)
			return 0.0D;
		double falloff = Math.max(1.0D,
				targetSliderSteps-currentOrbitalDistance+1.0D);
		return LEGACY_MAXIMUM_STEP*falloff/targetSliderSteps;
	}

	public static double scaleLegacyStep(double legacyStep,
			int sliderProgress) {
		return legacyStep*getMultiplier(sliderProgress);
	}

	public static double moveTowards(double current, double target,
			double legacyStep, int sliderProgress) {
		double maximumStep = Math.abs(scaleLegacyStep(
				legacyStep, sliderProgress));
		double difference = target-current;
		if(difference < 0.0D)
			return current+Math.max(difference, -maximumStep);
		if(difference > 0.0D)
			return current+Math.min(difference, maximumStep);
		return current;
	}
}
