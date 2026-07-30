package zmaster587.advancedRocketry.client.render.atmosphere;

import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.util.AstronomicalBodyHelper;

/**
 * Resolves primary-star radiance while leaving view-specific direction
 * construction to the caller that also draws the star quad.  This prevents a
 * second, subtly different celestial-angle formula from appearing here.
 */
public final class CelestialLightResolver {

	public static final double MAX_PRIMARY_LIGHT_RADIANCE = 65536.0D;

	/**
	 * Convenience adapter for a canonical dimension.  Both direction
	 * arguments use the sample-to-star convention.  Call this when refreshing
	 * cached stellar data.  The packed RGB accessor owns the star's cached
	 * colour and avoids the legacy {@link StellarBody#getColor()} allocation
	 * on every rendered frame.
	 */
	public boolean resolve(DimensionProperties properties,
			AtmosphereVisualProfile profile,
			double viewDirectionX, double viewDirectionY,
			double viewDirectionZ, double localDirectionX,
			double localDirectionY, double localDirectionZ,
			float rainAttenuation, float eclipseMultiplier,
			long worldTime, float partialTicks,
			AtmosphereLightingContext out) {
		if(out == null)
			throw new IllegalArgumentException(
					"Lighting output context cannot be null");
		out.reset();
		out.setFrame(worldTime, partialTicks);
		out.setAttenuation(rainAttenuation, eclipseMultiplier);
		if(properties == null || profile == null
				|| !out.setDirections(viewDirectionX, viewDirectionY,
						viewDirectionZ, localDirectionX, localDirectionY,
						localDirectionZ))
			return false;

		StellarBody primaryStar = properties.getStar();
		return resolveRadiance(primaryStar,
				properties.getSolarOrbitalDistance(),
				profile.getSunIntensityMultiplier(), out);
	}

	/**
	 * Free-space adapter for callers that have already resolved a canonical
	 * body/star pair.
	 */
	public boolean resolve(StellarBody primaryStar, int orbitalDistance,
			AtmosphereVisualProfile profile,
			double viewDirectionX, double viewDirectionY,
			double viewDirectionZ, double localDirectionX,
			double localDirectionY, double localDirectionZ,
			float rainAttenuation, float eclipseMultiplier,
			long worldTime, float partialTicks,
			AtmosphereLightingContext out) {
		if(out == null)
			throw new IllegalArgumentException(
					"Lighting output context cannot be null");
		out.reset();
		out.setFrame(worldTime, partialTicks);
		out.setAttenuation(rainAttenuation, eclipseMultiplier);
		if(profile == null || !out.setDirections(viewDirectionX,
				viewDirectionY, viewDirectionZ, localDirectionX,
				localDirectionY, localDirectionZ))
			return false;
		return resolveRadiance(primaryStar, orbitalDistance,
				profile.getSunIntensityMultiplier(), out);
	}

	/**
	 * Steady-state, allocation-free adapter for callers that cache the
	 * sanitised top-of-atmosphere radiance when the star/profile revision
	 * changes.
	 */
	public boolean resolvePrecomputedRadiance(double radianceRed,
			double radianceGreen, double radianceBlue,
			double viewDirectionX, double viewDirectionY,
			double viewDirectionZ, double localDirectionX,
			double localDirectionY, double localDirectionZ,
			float rainAttenuation, float eclipseMultiplier,
			long worldTime, float partialTicks,
			AtmosphereLightingContext out) {
		if(out == null)
			throw new IllegalArgumentException(
					"Lighting output context cannot be null");
		out.reset();
		out.setFrame(worldTime, partialTicks);
		out.setAttenuation(rainAttenuation, eclipseMultiplier);
		if(!out.setDirections(viewDirectionX, viewDirectionY,
				viewDirectionZ, localDirectionX, localDirectionY,
				localDirectionZ))
			return false;
		out.setPrimaryLightRadiance(radianceRed, radianceGreen,
				radianceBlue);
		return out.isValid();
	}

	private boolean resolveRadiance(StellarBody primaryStar,
			int orbitalDistance, double sunIntensityMultiplier,
			AtmosphereLightingContext out) {
		if(!AtmosphereMath.isFinite(sunIntensityMultiplier)
				|| sunIntensityMultiplier < 0.0D)
			return false;
		if(sunIntensityMultiplier == 0.0D) {
			out.setPrimaryLightRadiance(0.0D, 0.0D, 0.0D);
			return out.isValid();
		}
		if(primaryStar == null || orbitalDistance <= 0)
			return false;

		double brightness = AstronomicalBodyHelper.getStellarBrightness(
				primaryStar, orbitalDistance);
		if(!AtmosphereMath.isFinite(brightness) || brightness <= 0.0D) {
			out.setPrimaryLightRadiance(0.0D, 0.0D, 0.0D);
			return out.isValid();
		}
		brightness = Math.min(MAX_PRIMARY_LIGHT_RADIANCE,
				brightness * sunIntensityMultiplier);

		int packedColor = primaryStar.getColorRGB8();
		double red = sanitiseStarColor((packedColor & 0xff) / 255.0F);
		double green = sanitiseStarColor(
				((packedColor >>> 8) & 0xff) / 255.0F);
		double blue = sanitiseStarColor(
				((packedColor >>> 16) & 0xff) / 255.0F);
		if(red < 0.0D || green < 0.0D || blue < 0.0D)
			return false;

		out.setPrimaryLightRadiance(
				Math.min(MAX_PRIMARY_LIGHT_RADIANCE, brightness * red),
				Math.min(MAX_PRIMARY_LIGHT_RADIANCE, brightness * green),
				Math.min(MAX_PRIMARY_LIGHT_RADIANCE, brightness * blue));
		return out.isValid();
	}

	private static double sanitiseStarColor(float value) {
		if(Float.isNaN(value) || Float.isInfinite(value) || value < 0.0F)
			return -1.0D;
		return AtmosphereMath.srgbToLinear(
				AtmosphereMath.clamp(value, 0.0D, 1.0D));
	}
}
