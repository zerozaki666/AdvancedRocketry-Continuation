package zmaster587.advancedRocketry.client.render.atmosphere;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties.CloudLayerMode;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties.Snapshot;
import zmaster587.advancedRocketry.dimension.DimensionProperties;

/**
 * Converts gameplay properties and optional pack-author overrides into an
 * immutable client profile.  Sanitisation is deliberately one-way: no
 * resolved visual value is written back to {@link DimensionProperties}.
 */
public final class AtmosphereProfileResolver {

	public interface WarningSink {
		void warnOnce(int dimensionId, String field, String message);
	}

	private static final double DEFAULT_GROUND_RADIUS_KM = 6360.0D;
	private static final double DEFAULT_ATMOSPHERE_HEIGHT_KM = 100.0D;
	private static final double GAS_GIANT_ATMOSPHERE_HEIGHT_KM = 200.0D;
	private static final double DEFAULT_ABSORPTION_CENTER_KM = 25.0D;
	private static final double DEFAULT_ABSORPTION_WIDTH_KM = 15.0D;
	private static final double LOG_TWO = Math.log(2.0D);

	private static final WarningSink DEFAULT_WARNING_SINK =
			new LoggingWarningSink();

	private final WarningSink warningSink;

	public AtmosphereProfileResolver() {
		this(DEFAULT_WARNING_SINK);
	}

	public AtmosphereProfileResolver(WarningSink warningSink) {
		this.warningSink = warningSink == null
				? DEFAULT_WARNING_SINK : warningSink;
	}

	public AtmosphereVisualProfile resolve(DimensionProperties properties) {
		if(properties == null)
			throw new IllegalArgumentException(
					"Dimension properties cannot be null");
		return resolve(properties.getId(), properties.hasAtmosphere(),
				properties.isGasGiant(), properties.getAtmosphereDensity(),
				properties.skyColor, properties.fogColor,
				properties.getAtmosphereVisualPropertiesSnapshot());
	}

	/**
	 * Primitive adapter used by tests and legacy callers.  The
	 * {@code hasAtmosphere} argument remains the gameplay authority for normal
	 * planets; this resolver never reproduces the pressure threshold.
	 */
	public AtmosphereVisualProfile resolve(int dimensionId,
			boolean hasAtmosphere, boolean gasGiant, int atmosphereDensity,
			float[] skyColor, float[] fogColor, Snapshot overrides) {
		AtmosphereVisualProfile.Builder builder =
				AtmosphereVisualProfile.builder();
		builder.dimensionId = dimensionId;
		builder.atmosphereDensity = atmosphereDensity;
		builder.gasGiant = gasGiant;
		builder.enabled = gasGiant || hasAtmosphere;

		double legacyPressure = mapLegacyPressure(atmosphereDensity);
		builder.visualPressure = gasGiant
				? Math.max(2.0D, legacyPressure) : legacyPressure;
		if(atmosphereDensity < DimensionProperties.MIN_ATM_PRESSURE
				|| atmosphereDensity > DimensionProperties.MAX_ATM_PRESSURE)
			warningSink.warnOnce(dimensionId, "atmosphereDensity",
					"value outside gameplay range was clamped for rendering");

		builder.groundRadiusKm = DEFAULT_GROUND_RADIUS_KM;
		builder.atmosphereHeightKm = gasGiant
				? GAS_GIANT_ATMOSPHERE_HEIGHT_KM
				: DEFAULT_ATMOSPHERE_HEIGHT_KM;
		builder.rayleighStrength = gasGiant ? 0.65D : 1.0D;
		builder.rayleighScaleHeightKm = gasGiant ? 20.0D : 8.0D;
		builder.mieStrength = gasGiant ? 2.50D : 1.0D;
		builder.mieScaleHeightKm = gasGiant ? 8.0D : 1.2D;
		builder.mieAnisotropy = gasGiant ? 0.82D : 0.76D;
		builder.multipleScatteringStrength = gasGiant ? 0.20D : 0.10D;
		builder.absorptionStrength = 0.0D;
		builder.absorptionCenterKm = DEFAULT_ABSORPTION_CENTER_KM;
		builder.absorptionWidthKm = DEFAULT_ABSORPTION_WIDTH_KM;
		builder.sunIntensityMultiplier = 1.0D;
		builder.exposure = 1.0D;
		builder.cloudLayerMode = CloudLayerMode.AUTO;

		double[] rayleighTint = resolveLegacyTint(dimensionId, "skyColor",
				skyColor, 0.85D);
		double[] mieTint = resolveLegacyTint(dimensionId, "fogColor",
				fogColor, 0.65D);
		builder.rayleighTintRed = rayleighTint[0];
		builder.rayleighTintGreen = rayleighTint[1];
		builder.rayleighTintBlue = rayleighTint[2];
		builder.mieTintRed = mieTint[0];
		builder.mieTintGreen = mieTint[1];
		builder.mieTintBlue = mieTint[2];
		builder.absorptionTintRed = 1.0D;
		builder.absorptionTintGreen = 1.0D;
		builder.absorptionTintBlue = 1.0D;

		applyOverrides(builder, overrides, dimensionId);
		return builder.build();
	}

	/**
	 * Monotonic high-pressure mapping from the gameplay density scale to a
	 * bounded rendering pressure.
	 */
	public static double mapLegacyPressure(int atmosphereDensity) {
		double pressure = AtmosphereMath.clamp(
				atmosphereDensity / 100.0D, 0.0D, 16.0D);
		if(pressure <= 1.0D)
			return pressure;
		return Math.min(4.0D, 1.0D + 0.75D
				* Math.log(pressure) / LOG_TWO);
	}

	private void applyOverrides(AtmosphereVisualProfile.Builder builder,
			Snapshot overrides, int dimensionId) {
		if(overrides == null || !overrides.hasOverrides())
			return;

		if(overrides.hasPlanetRadiusKm())
			builder.groundRadiusKm = overrides.getPlanetRadiusKm();

		double maximumAtmosphereHeight = Math.min(10000.0D,
				builder.groundRadiusKm * 0.5D);
		double fallbackAtmosphereHeight = Math.min(
				builder.atmosphereHeightKm, maximumAtmosphereHeight);
		if(overrides.hasAtmosphereHeightKm()) {
			double override = overrides.getAtmosphereHeightKm();
			if(override <= maximumAtmosphereHeight)
				builder.atmosphereHeightKm = override;
			else {
				builder.atmosphereHeightKm = fallbackAtmosphereHeight;
				warningSink.warnOnce(dimensionId, "atmosphereHeightKm",
						"value exceeds half the resolved planet radius; "
						+ "using the derived fallback");
			}
		}
		else
			builder.atmosphereHeightKm = fallbackAtmosphereHeight;

		if(overrides.hasRayleighColor()) {
			builder.rayleighTintRed =
					overrides.getRayleighColorComponent(0);
			builder.rayleighTintGreen =
					overrides.getRayleighColorComponent(1);
			builder.rayleighTintBlue =
					overrides.getRayleighColorComponent(2);
		}
		if(overrides.hasRayleighStrength())
			builder.rayleighStrength = overrides.getRayleighStrength();
		builder.rayleighScaleHeightKm = resolveScaleHeight(dimensionId,
				"rayleighScaleHeightKm",
				overrides.hasRayleighScaleHeightKm(),
				overrides.hasRayleighScaleHeightKm()
						? overrides.getRayleighScaleHeightKm() : 0.0D,
				builder.rayleighScaleHeightKm, 0.1D,
				builder.atmosphereHeightKm);

		if(overrides.hasMieColor()) {
			builder.mieTintRed = overrides.getMieColorComponent(0);
			builder.mieTintGreen = overrides.getMieColorComponent(1);
			builder.mieTintBlue = overrides.getMieColorComponent(2);
		}
		if(overrides.hasMieStrength())
			builder.mieStrength = overrides.getMieStrength();
		builder.mieScaleHeightKm = resolveScaleHeight(dimensionId,
				"mieScaleHeightKm", overrides.hasMieScaleHeightKm(),
				overrides.hasMieScaleHeightKm()
						? overrides.getMieScaleHeightKm() : 0.0D,
				builder.mieScaleHeightKm, 0.05D,
				builder.atmosphereHeightKm);
		if(overrides.hasMieAnisotropy())
			builder.mieAnisotropy = overrides.getMieAnisotropy();

		if(overrides.hasAbsorptionColor()) {
			builder.absorptionTintRed =
					overrides.getAbsorptionColorComponent(0);
			builder.absorptionTintGreen =
					overrides.getAbsorptionColorComponent(1);
			builder.absorptionTintBlue =
					overrides.getAbsorptionColorComponent(2);
		}
		if(overrides.hasAbsorptionStrength())
			builder.absorptionStrength =
					overrides.getAbsorptionStrength();
		builder.absorptionCenterKm = resolveLayerValue(dimensionId,
				"absorptionCenterKm", overrides.hasAbsorptionCenterKm(),
				overrides.hasAbsorptionCenterKm()
						? overrides.getAbsorptionCenterKm() : 0.0D,
				builder.absorptionCenterKm, 0.0D,
				builder.atmosphereHeightKm);
		builder.absorptionWidthKm = resolveLayerValue(dimensionId,
				"absorptionWidthKm", overrides.hasAbsorptionWidthKm(),
				overrides.hasAbsorptionWidthKm()
						? overrides.getAbsorptionWidthKm() : 0.0D,
				builder.absorptionWidthKm, 0.1D,
				builder.atmosphereHeightKm);

		if(overrides.hasMultipleScatteringStrength())
			builder.multipleScatteringStrength =
					overrides.getMultipleScatteringStrength();
		if(overrides.hasSunIntensityMultiplier())
			builder.sunIntensityMultiplier =
					overrides.getSunIntensityMultiplier();
		if(overrides.hasExposure())
			builder.exposure = overrides.getExposure();
		if(overrides.hasCloudLayerMode())
			builder.cloudLayerMode = overrides.getCloudLayerMode();
	}

	private double resolveScaleHeight(int dimensionId, String field,
			boolean hasOverride, double override, double fallback,
			double minimum, double atmosphereHeight) {
		if(hasOverride && override <= atmosphereHeight)
			return override;
		if(hasOverride)
			warningSink.warnOnce(dimensionId, field,
					"value exceeds the resolved atmosphere height; "
					+ "using the derived fallback");
		return AtmosphereMath.clamp(fallback, minimum, atmosphereHeight);
	}

	private double resolveLayerValue(int dimensionId, String field,
			boolean hasOverride, double override, double fallback,
			double minimum, double atmosphereHeight) {
		if(hasOverride && override >= minimum
				&& override <= atmosphereHeight)
			return override;
		if(hasOverride)
			warningSink.warnOnce(dimensionId, field,
					"value is outside the resolved atmosphere layer; "
					+ "using the derived fallback");
		return AtmosphereMath.clamp(fallback, minimum, atmosphereHeight);
	}

	private double[] resolveLegacyTint(int dimensionId, String field,
			float[] color, double tintAmount) {
		double[] result = new double[] { 1.0D, 1.0D, 1.0D };
		if(color == null || color.length != 3) {
			warningSink.warnOnce(dimensionId, field,
					"RGB array must contain exactly three finite components; "
					+ "using white");
			return result;
		}
		for(int component = 0; component < 3; component++) {
			double value = color[component];
			if(!AtmosphereMath.isFinite(value) || value < 0.0D) {
				warningSink.warnOnce(dimensionId, field,
						"RGB array must contain exactly three finite "
						+ "non-negative components; using white");
				result[0] = 1.0D;
				result[1] = 1.0D;
				result[2] = 1.0D;
				return result;
			}
			double linear = AtmosphereMath.srgbToLinear(
					AtmosphereMath.clamp(value, 0.0D, 1.0D));
			result[component] = 1.0D + (linear - 1.0D) * tintAmount;
		}
		return result;
	}

	private static final class LoggingWarningSink implements WarningSink {
		private final Set<String> emitted = Collections.synchronizedSet(
				new HashSet<String>());

		@Override
		public void warnOnce(int dimensionId, String field,
				String message) {
			String key = dimensionId + "|" + field + "|" + message;
			if(emitted.add(key))
				AdvancedRocketry.logger.warn(
						"Atmosphere profile for dimension "
						+ dimensionId + ", field '" + field + "': "
						+ message);
		}
	}
}
