package zmaster587.advancedRocketry.client.render.atmosphere;

import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties.CloudLayerMode;

/**
 * Immutable, fully sanitised atmosphere parameters consumed by render code.
 * This object contains no live {@code DimensionProperties} references, so it
 * is safe to retain in a client cache and to share with a LUT worker.
 */
public final class AtmosphereVisualProfile {

	public static final double BASE_RAYLEIGH_RED = 0.005802D;
	public static final double BASE_RAYLEIGH_GREEN = 0.013558D;
	public static final double BASE_RAYLEIGH_BLUE = 0.033100D;
	public static final double BASE_MIE_SCATTERING = 0.003996D;
	public static final double BASE_MIE_EXTINCTION = 0.004440D;
	public static final double BASE_ABSORPTION_RED = 0.000650D;
	public static final double BASE_ABSORPTION_GREEN = 0.001881D;
	public static final double BASE_ABSORPTION_BLUE = 0.000085D;

	private final int dimensionId;
	private final int atmosphereDensity;
	private final boolean enabled;
	private final boolean gasGiant;
	private final double visualPressure;

	private final double groundRadiusKm;
	private final double atmosphereHeightKm;
	private final double atmosphereRadiusKm;

	private final double rayleighTintRed;
	private final double rayleighTintGreen;
	private final double rayleighTintBlue;
	private final double rayleighStrength;
	private final double rayleighScaleHeightKm;

	private final double mieTintRed;
	private final double mieTintGreen;
	private final double mieTintBlue;
	private final double mieStrength;
	private final double mieScaleHeightKm;
	private final double mieAnisotropy;

	private final double absorptionTintRed;
	private final double absorptionTintGreen;
	private final double absorptionTintBlue;
	private final double absorptionStrength;
	private final double absorptionCenterKm;
	private final double absorptionWidthKm;

	private final double betaRayleighScatteringRed;
	private final double betaRayleighScatteringGreen;
	private final double betaRayleighScatteringBlue;
	private final double betaMieScatteringRed;
	private final double betaMieScatteringGreen;
	private final double betaMieScatteringBlue;
	private final double betaMieExtinctionRed;
	private final double betaMieExtinctionGreen;
	private final double betaMieExtinctionBlue;
	private final double betaAbsorptionRed;
	private final double betaAbsorptionGreen;
	private final double betaAbsorptionBlue;

	private final double multipleScatteringStrength;
	private final double sunIntensityMultiplier;
	private final double exposure;
	private final CloudLayerMode cloudLayerMode;
	private final AtmosphereOpticalDepthLut.Key opticalDepthLutKey;
	private final int hashCode;

	AtmosphereVisualProfile(Builder builder) {
		validate(builder);
		dimensionId = builder.dimensionId;
		atmosphereDensity = builder.atmosphereDensity;
		enabled = builder.enabled;
		gasGiant = builder.gasGiant;
		visualPressure = builder.visualPressure;
		groundRadiusKm = builder.groundRadiusKm;
		atmosphereHeightKm = builder.atmosphereHeightKm;
		atmosphereRadiusKm = groundRadiusKm + atmosphereHeightKm;

		rayleighTintRed = builder.rayleighTintRed;
		rayleighTintGreen = builder.rayleighTintGreen;
		rayleighTintBlue = builder.rayleighTintBlue;
		rayleighStrength = builder.rayleighStrength;
		rayleighScaleHeightKm = builder.rayleighScaleHeightKm;
		mieTintRed = builder.mieTintRed;
		mieTintGreen = builder.mieTintGreen;
		mieTintBlue = builder.mieTintBlue;
		mieStrength = builder.mieStrength;
		mieScaleHeightKm = builder.mieScaleHeightKm;
		mieAnisotropy = builder.mieAnisotropy;
		absorptionTintRed = builder.absorptionTintRed;
		absorptionTintGreen = builder.absorptionTintGreen;
		absorptionTintBlue = builder.absorptionTintBlue;
		absorptionStrength = builder.absorptionStrength;
		absorptionCenterKm = builder.absorptionCenterKm;
		absorptionWidthKm = builder.absorptionWidthKm;

		double rayleighScale = visualPressure * rayleighStrength;
		betaRayleighScatteringRed = BASE_RAYLEIGH_RED
				* rayleighScale * rayleighTintRed;
		betaRayleighScatteringGreen = BASE_RAYLEIGH_GREEN
				* rayleighScale * rayleighTintGreen;
		betaRayleighScatteringBlue = BASE_RAYLEIGH_BLUE
				* rayleighScale * rayleighTintBlue;

		double mieScale = visualPressure * mieStrength;
		betaMieScatteringRed = BASE_MIE_SCATTERING
				* mieScale * mieTintRed;
		betaMieScatteringGreen = BASE_MIE_SCATTERING
				* mieScale * mieTintGreen;
		betaMieScatteringBlue = BASE_MIE_SCATTERING
				* mieScale * mieTintBlue;
		betaMieExtinctionRed = Math.max(BASE_MIE_EXTINCTION
				* mieScale * mieTintRed, betaMieScatteringRed);
		betaMieExtinctionGreen = Math.max(BASE_MIE_EXTINCTION
				* mieScale * mieTintGreen, betaMieScatteringGreen);
		betaMieExtinctionBlue = Math.max(BASE_MIE_EXTINCTION
				* mieScale * mieTintBlue, betaMieScatteringBlue);

		double absorptionScale = visualPressure * absorptionStrength;
		betaAbsorptionRed = BASE_ABSORPTION_RED
				* absorptionScale * absorptionTintRed;
		betaAbsorptionGreen = BASE_ABSORPTION_GREEN
				* absorptionScale * absorptionTintGreen;
		betaAbsorptionBlue = BASE_ABSORPTION_BLUE
				* absorptionScale * absorptionTintBlue;

		multipleScatteringStrength = builder.multipleScatteringStrength;
		sunIntensityMultiplier = builder.sunIntensityMultiplier;
		exposure = builder.exposure;
		cloudLayerMode = builder.cloudLayerMode;
		opticalDepthLutKey = new AtmosphereOpticalDepthLut.Key(
				groundRadiusKm, atmosphereHeightKm,
				rayleighScaleHeightKm, mieScaleHeightKm,
				absorptionCenterKm, absorptionWidthKm);
		hashCode = computeHashCode();
	}

	public int getDimensionId() {
		return dimensionId;
	}

	public int getAtmosphereDensity() {
		return atmosphereDensity;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isGasGiant() {
		return gasGiant;
	}

	public double getVisualPressure() {
		return visualPressure;
	}

	public double getGroundRadiusKm() {
		return groundRadiusKm;
	}

	public double getPlanetRadiusKm() {
		return groundRadiusKm;
	}

	public double getAtmosphereHeightKm() {
		return atmosphereHeightKm;
	}

	public double getAtmosphereRadiusKm() {
		return atmosphereRadiusKm;
	}

	public double getRayleighTintRed() {
		return rayleighTintRed;
	}

	public double getRayleighTintGreen() {
		return rayleighTintGreen;
	}

	public double getRayleighTintBlue() {
		return rayleighTintBlue;
	}

	public double getRayleighTint(int component) {
		switch(component) {
			case 0:
				return rayleighTintRed;
			case 1:
				return rayleighTintGreen;
			case 2:
				return rayleighTintBlue;
			default:
				throw new IndexOutOfBoundsException("RGB component: "
						+ component);
		}
	}

	public double getRayleighStrength() {
		return rayleighStrength;
	}

	public double getRayleighScaleHeightKm() {
		return rayleighScaleHeightKm;
	}

	public double getMieTintRed() {
		return mieTintRed;
	}

	public double getMieTintGreen() {
		return mieTintGreen;
	}

	public double getMieTintBlue() {
		return mieTintBlue;
	}

	public double getMieTint(int component) {
		switch(component) {
			case 0:
				return mieTintRed;
			case 1:
				return mieTintGreen;
			case 2:
				return mieTintBlue;
			default:
				throw new IndexOutOfBoundsException("RGB component: "
						+ component);
		}
	}

	public double getMieStrength() {
		return mieStrength;
	}

	public double getMieScaleHeightKm() {
		return mieScaleHeightKm;
	}

	public double getMieAnisotropy() {
		return mieAnisotropy;
	}

	public double getAbsorptionTintRed() {
		return absorptionTintRed;
	}

	public double getAbsorptionTintGreen() {
		return absorptionTintGreen;
	}

	public double getAbsorptionTintBlue() {
		return absorptionTintBlue;
	}

	public double getAbsorptionTint(int component) {
		switch(component) {
			case 0:
				return absorptionTintRed;
			case 1:
				return absorptionTintGreen;
			case 2:
				return absorptionTintBlue;
			default:
				throw new IndexOutOfBoundsException("RGB component: "
						+ component);
		}
	}

	public double getAbsorptionStrength() {
		return absorptionStrength;
	}

	public double getAbsorptionCenterKm() {
		return absorptionCenterKm;
	}

	public double getAbsorptionWidthKm() {
		return absorptionWidthKm;
	}

	public double getBetaRayleighScatteringRed() {
		return betaRayleighScatteringRed;
	}

	public double getBetaRayleighScatteringGreen() {
		return betaRayleighScatteringGreen;
	}

	public double getBetaRayleighScatteringBlue() {
		return betaRayleighScatteringBlue;
	}

	public double getBetaRayleighScattering(int component) {
		switch(component) {
			case 0:
				return betaRayleighScatteringRed;
			case 1:
				return betaRayleighScatteringGreen;
			case 2:
				return betaRayleighScatteringBlue;
			default:
				throw new IndexOutOfBoundsException("RGB component: "
						+ component);
		}
	}

	public double getBetaRayleighExtinctionRed() {
		return betaRayleighScatteringRed;
	}

	public double getBetaRayleighExtinctionGreen() {
		return betaRayleighScatteringGreen;
	}

	public double getBetaRayleighExtinctionBlue() {
		return betaRayleighScatteringBlue;
	}

	public double getBetaRayleighExtinction(int component) {
		return getBetaRayleighScattering(component);
	}

	public double getBetaMieScatteringRed() {
		return betaMieScatteringRed;
	}

	public double getBetaMieScatteringGreen() {
		return betaMieScatteringGreen;
	}

	public double getBetaMieScatteringBlue() {
		return betaMieScatteringBlue;
	}

	public double getBetaMieScattering(int component) {
		switch(component) {
			case 0:
				return betaMieScatteringRed;
			case 1:
				return betaMieScatteringGreen;
			case 2:
				return betaMieScatteringBlue;
			default:
				throw new IndexOutOfBoundsException("RGB component: "
						+ component);
		}
	}

	public double getBetaMieExtinctionRed() {
		return betaMieExtinctionRed;
	}

	public double getBetaMieExtinctionGreen() {
		return betaMieExtinctionGreen;
	}

	public double getBetaMieExtinctionBlue() {
		return betaMieExtinctionBlue;
	}

	public double getBetaMieExtinction(int component) {
		switch(component) {
			case 0:
				return betaMieExtinctionRed;
			case 1:
				return betaMieExtinctionGreen;
			case 2:
				return betaMieExtinctionBlue;
			default:
				throw new IndexOutOfBoundsException("RGB component: "
						+ component);
		}
	}

	public double getBetaAbsorptionRed() {
		return betaAbsorptionRed;
	}

	public double getBetaAbsorptionGreen() {
		return betaAbsorptionGreen;
	}

	public double getBetaAbsorptionBlue() {
		return betaAbsorptionBlue;
	}

	public double getBetaAbsorption(int component) {
		switch(component) {
			case 0:
				return betaAbsorptionRed;
			case 1:
				return betaAbsorptionGreen;
			case 2:
				return betaAbsorptionBlue;
			default:
				throw new IndexOutOfBoundsException("RGB component: "
						+ component);
		}
	}

	public double getMultipleScatteringStrength() {
		return multipleScatteringStrength;
	}

	public double getSunIntensityMultiplier() {
		return sunIntensityMultiplier;
	}

	public double getExposure() {
		return exposure;
	}

	public CloudLayerMode getCloudLayerMode() {
		return cloudLayerMode;
	}

	public AtmosphereOpticalDepthLut.Key getOpticalDepthLutKey() {
		return opticalDepthLutKey;
	}

	@Override
	public boolean equals(Object other) {
		if(this == other)
			return true;
		if(!(other instanceof AtmosphereVisualProfile))
			return false;
		AtmosphereVisualProfile profile = (AtmosphereVisualProfile)other;
		return dimensionId == profile.dimensionId
				&& atmosphereDensity == profile.atmosphereDensity
				&& enabled == profile.enabled && gasGiant == profile.gasGiant
				&& same(visualPressure, profile.visualPressure)
				&& same(groundRadiusKm, profile.groundRadiusKm)
				&& same(atmosphereHeightKm, profile.atmosphereHeightKm)
				&& same(rayleighTintRed, profile.rayleighTintRed)
				&& same(rayleighTintGreen, profile.rayleighTintGreen)
				&& same(rayleighTintBlue, profile.rayleighTintBlue)
				&& same(rayleighStrength, profile.rayleighStrength)
				&& same(rayleighScaleHeightKm,
						profile.rayleighScaleHeightKm)
				&& same(mieTintRed, profile.mieTintRed)
				&& same(mieTintGreen, profile.mieTintGreen)
				&& same(mieTintBlue, profile.mieTintBlue)
				&& same(mieStrength, profile.mieStrength)
				&& same(mieScaleHeightKm, profile.mieScaleHeightKm)
				&& same(mieAnisotropy, profile.mieAnisotropy)
				&& same(absorptionTintRed, profile.absorptionTintRed)
				&& same(absorptionTintGreen, profile.absorptionTintGreen)
				&& same(absorptionTintBlue, profile.absorptionTintBlue)
				&& same(absorptionStrength, profile.absorptionStrength)
				&& same(absorptionCenterKm, profile.absorptionCenterKm)
				&& same(absorptionWidthKm, profile.absorptionWidthKm)
				&& same(multipleScatteringStrength,
						profile.multipleScatteringStrength)
				&& same(sunIntensityMultiplier,
						profile.sunIntensityMultiplier)
				&& same(exposure, profile.exposure)
				&& cloudLayerMode == profile.cloudLayerMode;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		return "AtmosphereVisualProfile{dimension=" + dimensionId
				+ ", enabled=" + enabled + ", gasGiant=" + gasGiant
				+ ", pressure=" + visualPressure + ", groundRadiusKm="
				+ groundRadiusKm + ", atmosphereHeightKm="
				+ atmosphereHeightKm + '}';
	}

	static Builder builder() {
		return new Builder();
	}

	static final class Builder {
		int dimensionId;
		int atmosphereDensity;
		boolean enabled;
		boolean gasGiant;
		double visualPressure;
		double groundRadiusKm = 6360.0D;
		double atmosphereHeightKm = 100.0D;
		double rayleighTintRed = 1.0D;
		double rayleighTintGreen = 1.0D;
		double rayleighTintBlue = 1.0D;
		double rayleighStrength = 1.0D;
		double rayleighScaleHeightKm = 8.0D;
		double mieTintRed = 1.0D;
		double mieTintGreen = 1.0D;
		double mieTintBlue = 1.0D;
		double mieStrength = 1.0D;
		double mieScaleHeightKm = 1.2D;
		double mieAnisotropy = 0.76D;
		double absorptionTintRed = 1.0D;
		double absorptionTintGreen = 1.0D;
		double absorptionTintBlue = 1.0D;
		double absorptionStrength;
		double absorptionCenterKm = 25.0D;
		double absorptionWidthKm = 15.0D;
		double multipleScatteringStrength = 0.10D;
		double sunIntensityMultiplier = 1.0D;
		double exposure = 1.0D;
		CloudLayerMode cloudLayerMode = CloudLayerMode.AUTO;

		AtmosphereVisualProfile build() {
			return new AtmosphereVisualProfile(this);
		}
	}

	private static void validate(Builder builder) {
		if(!inRange(builder.visualPressure, 0.0D, 4.0D)
				|| !inRange(builder.groundRadiusKm, 100.0D, 200000.0D)
				|| !inRange(builder.atmosphereHeightKm, 1.0D,
						Math.min(10000.0D,
								builder.groundRadiusKm * 0.5D))
				|| !inRange(builder.rayleighScaleHeightKm, 0.1D,
						builder.atmosphereHeightKm)
				|| !inRange(builder.mieScaleHeightKm, 0.05D,
						builder.atmosphereHeightKm)
				|| !inRange(builder.rayleighStrength, 0.0D, 16.0D)
				|| !inRange(builder.mieStrength, 0.0D, 16.0D)
				|| !inRange(builder.mieAnisotropy,
						AtmosphereMath.MIN_MIE_ANISOTROPY,
						AtmosphereMath.MAX_MIE_ANISOTROPY)
				|| !inRange(builder.absorptionStrength, 0.0D, 16.0D)
				|| !inRange(builder.absorptionCenterKm, 0.0D,
						builder.atmosphereHeightKm)
				|| !inRange(builder.absorptionWidthKm, 0.1D,
						builder.atmosphereHeightKm)
				|| !inRange(builder.multipleScatteringStrength, 0.0D,
						1.0D)
				|| !inRange(builder.sunIntensityMultiplier, 0.0D, 16.0D)
				|| !inRange(builder.exposure, 0.05D, 8.0D)
				|| !validColor(builder.rayleighTintRed,
						builder.rayleighTintGreen,
						builder.rayleighTintBlue)
				|| !validColor(builder.mieTintRed, builder.mieTintGreen,
						builder.mieTintBlue)
				|| !validColor(builder.absorptionTintRed,
						builder.absorptionTintGreen,
						builder.absorptionTintBlue)
				|| builder.cloudLayerMode == null)
			throw new IllegalArgumentException(
					"Atmosphere profile builder contains invalid values");
	}

	private int computeHashCode() {
		int result = dimensionId;
		result = 31 * result + atmosphereDensity;
		result = 31 * result + (enabled ? 1 : 0);
		result = 31 * result + (gasGiant ? 1 : 0);
		result = appendHash(result, visualPressure);
		result = appendHash(result, groundRadiusKm);
		result = appendHash(result, atmosphereHeightKm);
		result = appendHash(result, rayleighTintRed);
		result = appendHash(result, rayleighTintGreen);
		result = appendHash(result, rayleighTintBlue);
		result = appendHash(result, rayleighStrength);
		result = appendHash(result, rayleighScaleHeightKm);
		result = appendHash(result, mieTintRed);
		result = appendHash(result, mieTintGreen);
		result = appendHash(result, mieTintBlue);
		result = appendHash(result, mieStrength);
		result = appendHash(result, mieScaleHeightKm);
		result = appendHash(result, mieAnisotropy);
		result = appendHash(result, absorptionTintRed);
		result = appendHash(result, absorptionTintGreen);
		result = appendHash(result, absorptionTintBlue);
		result = appendHash(result, absorptionStrength);
		result = appendHash(result, absorptionCenterKm);
		result = appendHash(result, absorptionWidthKm);
		result = appendHash(result, multipleScatteringStrength);
		result = appendHash(result, sunIntensityMultiplier);
		result = appendHash(result, exposure);
		return 31 * result + cloudLayerMode.hashCode();
	}

	private static int appendHash(int current, double value) {
		long bits = Double.doubleToLongBits(value);
		return 31 * current + (int)(bits ^ bits >>> 32);
	}

	private static boolean same(double first, double second) {
		return Double.doubleToLongBits(first)
				== Double.doubleToLongBits(second);
	}

	private static boolean inRange(double value, double minimum,
			double maximum) {
		return AtmosphereMath.isFinite(value) && value >= minimum
				&& value <= maximum;
	}

	private static boolean validColor(double red, double green, double blue) {
		return inRange(red, 0.0D, 4.0D)
				&& inRange(green, 0.0D, 4.0D)
				&& inRange(blue, 0.0D, 4.0D);
	}
}
