package zmaster587.advancedRocketry.client.render.atmosphere;

/**
 * Reusable frame-local atmosphere lighting data.  It deliberately stores
 * primitive vector components so {@link #reset()} and resolver calls do not
 * allocate temporary vector objects.
 */
public final class AtmosphereLightingContext {

	private float primaryLightDirectionViewX;
	private float primaryLightDirectionViewY;
	private float primaryLightDirectionViewZ;
	private float primaryLightDirectionLocalX;
	private float primaryLightDirectionLocalY;
	private float primaryLightDirectionLocalZ;
	private float primaryLightRadianceRed;
	private float primaryLightRadianceGreen;
	private float primaryLightRadianceBlue;
	private float rainAttenuation;
	private float eclipseMultiplier;
	private long worldTime;
	private float partialTicks;
	private boolean directionsValid;
	private boolean radianceValid;
	private boolean valid;

	public AtmosphereLightingContext() {
		reset();
	}

	public void reset() {
		primaryLightDirectionViewX = 0.0F;
		primaryLightDirectionViewY = 0.0F;
		primaryLightDirectionViewZ = 0.0F;
		primaryLightDirectionLocalX = 0.0F;
		primaryLightDirectionLocalY = 0.0F;
		primaryLightDirectionLocalZ = 0.0F;
		primaryLightRadianceRed = 0.0F;
		primaryLightRadianceGreen = 0.0F;
		primaryLightRadianceBlue = 0.0F;
		rainAttenuation = 1.0F;
		eclipseMultiplier = 1.0F;
		worldTime = 0L;
		partialTicks = 0.0F;
		directionsValid = false;
		radianceValid = false;
		valid = false;
	}

	public boolean setDirections(double viewX, double viewY, double viewZ,
			double localX, double localY, double localZ) {
		double viewLengthSquared = viewX * viewX + viewY * viewY
				+ viewZ * viewZ;
		double localLengthSquared = localX * localX + localY * localY
				+ localZ * localZ;
		if(!validDirection(viewX, viewY, viewZ, viewLengthSquared)
				|| !validDirection(localX, localY, localZ,
						localLengthSquared)) {
			directionsValid = false;
			valid = false;
			return false;
		}

		double inverseViewLength = 1.0D / Math.sqrt(viewLengthSquared);
		double inverseLocalLength = 1.0D / Math.sqrt(localLengthSquared);
		primaryLightDirectionViewX = (float)(viewX * inverseViewLength);
		primaryLightDirectionViewY = (float)(viewY * inverseViewLength);
		primaryLightDirectionViewZ = (float)(viewZ * inverseViewLength);
		primaryLightDirectionLocalX = (float)(localX * inverseLocalLength);
		primaryLightDirectionLocalY = (float)(localY * inverseLocalLength);
		primaryLightDirectionLocalZ = (float)(localZ * inverseLocalLength);
		directionsValid = true;
		// A finite zero-radiance light is a valid fail-dark result.  It is
		// intentionally distinct from an unresolved or invalid direction.
		valid = radianceValid;
		return true;
	}

	public void setPrimaryLightRadiance(double red, double green,
			double blue) {
		if(!AtmosphereMath.isFinite(red) || !AtmosphereMath.isFinite(green)
				|| !AtmosphereMath.isFinite(blue)) {
			primaryLightRadianceRed = 0.0F;
			primaryLightRadianceGreen = 0.0F;
			primaryLightRadianceBlue = 0.0F;
			radianceValid = false;
			valid = false;
			return;
		}
		primaryLightRadianceRed = (float)clampRadiance(red);
		primaryLightRadianceGreen = (float)clampRadiance(green);
		primaryLightRadianceBlue = (float)clampRadiance(blue);
		radianceValid = true;
		valid = directionsValid;
	}

	public void setAttenuation(float rainAttenuation,
			float eclipseMultiplier) {
		this.rainAttenuation = finiteClamp01(rainAttenuation, 1.0F);
		this.eclipseMultiplier = finiteClamp01(eclipseMultiplier, 1.0F);
	}

	public void setFrame(long worldTime, float partialTicks) {
		this.worldTime = worldTime;
		this.partialTicks = finiteClamp01(partialTicks, 0.0F);
	}

	public boolean isValid() {
		return valid;
	}

	public boolean hasPositiveRadiance() {
		return valid && (primaryLightRadianceRed > 0.0F
				|| primaryLightRadianceGreen > 0.0F
				|| primaryLightRadianceBlue > 0.0F);
	}

	public float getPrimaryLightDirectionViewX() {
		return primaryLightDirectionViewX;
	}

	public float getPrimaryLightDirectionViewY() {
		return primaryLightDirectionViewY;
	}

	public float getPrimaryLightDirectionViewZ() {
		return primaryLightDirectionViewZ;
	}

	public float getPrimaryLightDirectionLocalX() {
		return primaryLightDirectionLocalX;
	}

	public float getPrimaryLightDirectionLocalY() {
		return primaryLightDirectionLocalY;
	}

	public float getPrimaryLightDirectionLocalZ() {
		return primaryLightDirectionLocalZ;
	}

	public float getPrimaryLightRadianceRed() {
		return primaryLightRadianceRed;
	}

	public float getPrimaryLightRadianceGreen() {
		return primaryLightRadianceGreen;
	}

	public float getPrimaryLightRadianceBlue() {
		return primaryLightRadianceBlue;
	}

	public float getRainAttenuation() {
		return rainAttenuation;
	}

	public float getEclipseMultiplier() {
		return eclipseMultiplier;
	}

	public float getEffectiveLightMultiplier() {
		return rainAttenuation * eclipseMultiplier;
	}

	public long getWorldTime() {
		return worldTime;
	}

	public float getPartialTicks() {
		return partialTicks;
	}

	private static boolean validDirection(double x, double y, double z,
			double lengthSquared) {
		return AtmosphereMath.isFinite(x) && AtmosphereMath.isFinite(y)
				&& AtmosphereMath.isFinite(z)
				&& AtmosphereMath.isFinite(lengthSquared)
				&& lengthSquared > AtmosphereMath.DIRECTION_EPSILON
						* AtmosphereMath.DIRECTION_EPSILON;
	}

	private static float finiteClamp01(float value, float fallback) {
		if(Float.isNaN(value) || Float.isInfinite(value))
			return fallback;
		return Math.max(0.0F, Math.min(1.0F, value));
	}

	private static double clampRadiance(double value) {
		return Math.max(0.0D, Math.min(
				CelestialLightResolver.MAX_PRIMARY_LIGHT_RADIANCE, value));
	}
}
