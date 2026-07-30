package zmaster587.advancedRocketry.client.render.planet;

import net.minecraft.util.ResourceLocation;
import zmaster587.advancedRocketry.dimension.DimensionProperties;

/**
 * Full-fidelity internal planet draw contract.  Legacy overloads remain
 * available, but repository callers use this value so atmosphere density,
 * fog tint, gas-giant state and explicit render overrides are not discarded.
 */
public final class PlanetRenderContext {

	public enum ViewKind {
		SKY,
		STATION,
		FREE_SPACE,
		GUI,
		HOLOGRAM,
		ROCKET
	}

	private final DimensionProperties properties;
	private final ResourceLocation surfaceTexture;
	private final int locationX;
	private final int locationY;
	private final double zLevel;
	private final float size;
	private final float alpha;
	private final double legacyShadowAngle;
	private final float lightX;
	private final float lightY;
	private final float lightZ;
	private final boolean lightDirectionValid;
	private final boolean legacyHasAtmosphere;
	private final float[] legacySkyColor;
	private final float[] legacyFogColor;
	private final float[] ringColor;
	private final boolean legacyGasGiant;
	private final boolean hasRings;
	private final ViewKind viewKind;

	private PlanetRenderContext(DimensionProperties properties,
			ResourceLocation surfaceTexture, int locationX, int locationY,
			double zLevel, float size, float alpha,
			double legacyShadowAngle, float lightX, float lightY,
			float lightZ, boolean legacyHasAtmosphere,
			float[] legacySkyColor, float[] legacyFogColor,
			float[] ringColor, boolean legacyGasGiant, boolean hasRings,
			ViewKind viewKind) {
		this.properties = properties;
		this.surfaceTexture = surfaceTexture;
		this.locationX = locationX;
		this.locationY = locationY;
		this.zLevel = zLevel;
		this.size = sanitizePositive(size);
		this.alpha = sanitizeAlpha(alpha);
		this.legacyShadowAngle = finite(legacyShadowAngle)
				? legacyShadowAngle : 0D;
		double length = Math.sqrt(
				(double)lightX*lightX
						+(double)lightY*lightY
						+(double)lightZ*lightZ);
		if(finite(lightX) && finite(lightY) && finite(lightZ)
				&& finite(length) && length > 0.00001D) {
			this.lightX = (float)(lightX/length);
			this.lightY = (float)(lightY/length);
			this.lightZ = (float)(lightZ/length);
			this.lightDirectionValid = true;
		}
		else {
			this.lightX = 0F;
			this.lightY = 0F;
			this.lightZ = 0F;
			this.lightDirectionValid = false;
		}
		this.legacyHasAtmosphere = legacyHasAtmosphere;
		this.legacySkyColor = sanitizeColor(legacySkyColor);
		this.legacyFogColor = sanitizeColor(legacyFogColor);
		this.ringColor = sanitizeColor(ringColor);
		this.legacyGasGiant = legacyGasGiant;
		this.hasRings = hasRings;
		this.viewKind = viewKind == null ? ViewKind.SKY : viewKind;
	}

	public static PlanetRenderContext fromProperties(
			DimensionProperties properties, ResourceLocation surfaceTexture,
			int locationX, int locationY, double zLevel, float size,
			float alpha, double shadowAngle, ViewKind viewKind) {
		float lightX = finite(shadowAngle)
				? (float)Math.cos(shadowAngle) : Float.NaN;
		float lightZ = finite(shadowAngle)
				? (float)Math.sin(shadowAngle) : Float.NaN;
		return fromProperties(properties, surfaceTexture,
				locationX, locationY, zLevel, size, alpha,
				shadowAngle, lightX, 0F, lightZ, viewKind);
	}

	public static PlanetRenderContext fromProperties(
			DimensionProperties properties, ResourceLocation surfaceTexture,
			int locationX, int locationY, double zLevel, float size,
			float alpha, double shadowAngle,
			float lightX, float lightY, float lightZ,
			ViewKind viewKind) {
		float[] sky = properties == null ? null : properties.skyColor;
		float[] fog = properties == null ? null : properties.fogColor;
		float[] rings = properties == null ? null : properties.ringColor;
		return new PlanetRenderContext(properties, surfaceTexture, locationX,
				locationY, zLevel, size, alpha, shadowAngle, lightX, lightY,
				lightZ, properties != null && properties.hasAtmosphere(),
				sky, fog, rings,
				properties != null && properties.isGasGiant(),
				properties != null && properties.hasRings(), viewKind);
	}

	public static PlanetRenderContext legacy(ResourceLocation surfaceTexture,
			int locationX, int locationY, double zLevel, float size,
			float alpha, double shadowAngle, boolean hasAtmosphere,
			float[] skyColor, float[] ringColor, boolean gasGiant,
			boolean hasRings, ViewKind viewKind) {
		// The gasGiant argument remains for ABI compatibility only. Legacy
		// callers never supplied enough data to infer that rendering mode.
		float lightX = finite(shadowAngle)
				? (float)Math.cos(shadowAngle) : Float.NaN;
		float lightZ = finite(shadowAngle)
				? (float)Math.sin(shadowAngle) : Float.NaN;
		return new PlanetRenderContext(null, surfaceTexture, locationX,
				locationY, zLevel, size, alpha, shadowAngle, lightX, 0F,
				lightZ, hasAtmosphere, skyColor, skyColor, ringColor,
				false, hasRings, viewKind);
	}

	public PlanetRenderContext withLightDirection(float x, float y, float z) {
		return new PlanetRenderContext(properties, surfaceTexture, locationX,
				locationY, zLevel, size, alpha, legacyShadowAngle, x, y, z,
				legacyHasAtmosphere, legacySkyColor, legacyFogColor,
				ringColor, legacyGasGiant, hasRings, viewKind);
	}

	public DimensionProperties getProperties() {
		return properties;
	}

	public ResourceLocation getSurfaceTexture() {
		return surfaceTexture;
	}

	public int getLocationX() {
		return locationX;
	}

	public int getLocationY() {
		return locationY;
	}

	public double getZLevel() {
		return zLevel;
	}

	public float getSize() {
		return size;
	}

	public float getAlpha() {
		return alpha;
	}

	public double getLegacyShadowAngle() {
		return legacyShadowAngle;
	}

	public float getLightX() {
		return lightX;
	}

	public float getLightY() {
		return lightY;
	}

	public float getLightZ() {
		return lightZ;
	}

	public boolean hasLightDirection() {
		return lightDirectionValid;
	}

	public boolean hasAtmosphere() {
		return properties == null ? legacyHasAtmosphere
				: properties.isGasGiant() || properties.hasAtmosphere();
	}

	public float[] getSkyColor() {
		return legacySkyColor;
	}

	public float[] getFogColor() {
		return legacyFogColor;
	}

	public float[] getRingColor() {
		return ringColor;
	}

	public boolean isGasGiant() {
		return properties == null ? legacyGasGiant
				: properties.isGasGiant();
	}

	public boolean hasRings() {
		return properties == null ? hasRings : properties.hasRings();
	}

	public ViewKind getViewKind() {
		return viewKind;
	}

	private static float sanitizePositive(float value) {
		return finite(value) && value > 0F ? value : 0F;
	}

	private static float sanitizeAlpha(float value) {
		if(!finite(value))
			return 0F;
		return Math.max(0F, Math.min(1F, value));
	}

	private static float[] sanitizeColor(float[] input) {
		if(input == null || input.length < 3)
			return new float[] {1F, 1F, 1F};
		float[] output = new float[3];
		for(int index = 0; index < 3; index++) {
			float value = input[index];
			output[index] = finite(value)
					? Math.max(0F, Math.min(4F, value)) : 1F;
		}
		return output;
	}

	private static boolean finite(float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	private static boolean finite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}
}
