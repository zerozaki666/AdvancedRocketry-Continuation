package zmaster587.advancedRocketry.client.render.atmosphere;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.Tessellator;
import zmaster587.advancedRocketry.api.AtmosphereRenderMode;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.client.render.planet.PlanetRenderContext;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties.Snapshot;
import zmaster587.advancedRocketry.dimension.DimensionProperties;

/**
 * Deterministic single-pass halo used by GUI, small-body and compatibility
 * paths.  It deliberately has one continuous outer fade and never recreates
 * the legacy concentric-shell overdraw.
 */
public final class AnalyticAtmosphereRenderer {

	private static final int HALO_SEGMENTS = 96;
	private static final int SHELL_LONGITUDE_SEGMENTS = 96;
	private static final int SHELL_LATITUDE_SEGMENTS = 48;
	private static final float DETAILED_SHELL_MIN_RADIUS_PIXELS = 6F;
	private static final float[] SHELL_NORMALS = new float[
			(SHELL_LATITUDE_SEGMENTS+1)
					*(SHELL_LONGITUDE_SEGMENTS+1)*3];
	private static final AtmosphereRenderCapabilities CAPABILITIES =
			new AtmosphereRenderCapabilities();

	static {
		for(int latitude = 0;
				latitude <= SHELL_LATITUDE_SEGMENTS; latitude++) {
			double latitudeAngle = -Math.PI/2D
					+Math.PI*latitude/SHELL_LATITUDE_SEGMENTS;
			double cosLatitude = Math.cos(latitudeAngle);
			for(int longitude = 0;
					longitude <= SHELL_LONGITUDE_SEGMENTS; longitude++) {
				double longitudeAngle = -Math.PI
						+2D*Math.PI*longitude
								/SHELL_LONGITUDE_SEGMENTS;
				int offset = shellNormalOffset(latitude, longitude);
				SHELL_NORMALS[offset] =
						(float)(cosLatitude*Math.cos(longitudeAngle));
				SHELL_NORMALS[offset+1] =
						(float)Math.sin(latitudeAngle);
				SHELL_NORMALS[offset+2] =
						(float)(cosLatitude*Math.sin(longitudeAngle));
			}
		}
	}

	private AnalyticAtmosphereRenderer() {
	}

	public static boolean isFixedFunctionAtmosphereSafe() {
		if(Configuration.atmosphereRenderMode == AtmosphereRenderMode.OFF)
			return false;
		AtmosphereRenderCapabilities capabilities = CAPABILITIES.detect();
		return capabilities.isContextAvailable()
				&& (!capabilities.supportsShaderPrograms()
						|| capabilities.getCurrentProgram() == 0);
	}

	public static boolean renderBillboardHalo(Tessellator tessellator,
			PlanetRenderContext context) {
		if(context == null || !context.hasLightDirection()
				|| !context.hasAtmosphere()
				|| context.getSize() <= 0F
				|| (context.getViewKind()
						== PlanetRenderContext.ViewKind.GUI
						&& context.getSize() < 2F)
				|| Configuration.atmosphereRenderMode
						== AtmosphereRenderMode.OFF)
			return false;
		if(!isFixedFunctionAtmosphereSafe())
			return false;

		float size = context.getSize();
		DimensionProperties properties = context.getProperties();
		Snapshot overrides = properties == null ? null
				: properties.getAtmosphereVisualPropertiesSnapshot();
		if(!hasVisualLight(overrides))
			return false;
		float thickness = Math.max(1F, size*resolveShellRatio(
				properties, overrides, context.isGasGiant(),
				context.isGasGiant() ? 0.14F : 0.075F));
		float innerRadius = Math.max(0F, size-Math.min(1F, size*0.04F));
		float outerRadius = size+thickness;
		float pressure = visualPressure(context.getProperties(),
				context.isGasGiant());
		float baseAlpha = clamp(context.getAlpha()
				*(context.isGasGiant() ? 0.72F : 0.50F)
				*(0.35F+0.65F*(1F-(float)Math.exp(-pressure))));
		float[] sky = context.getSkyColor();
		float[] fog = context.getFogColor();
		float skyRed = resolveRayleighComponent(
				properties, overrides, sky, 0, 0.45F);
		float skyGreen = resolveRayleighComponent(
				properties, overrides, sky, 1, 0.65F);
		float skyBlue = resolveRayleighComponent(
				properties, overrides, sky, 2, 1F);
		float fogRed = resolveMieComponent(
				properties, overrides, fog, 0, 0.75F);
		float fogGreen = resolveMieComponent(
				properties, overrides, fog, 1, 0.82F);
		float fogBlue = resolveMieComponent(
				properties, overrides, fog, 2, 0.90F);
		double plane = context.getZLevel()+0.02D;

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		boolean clientAttributesPushed = false;
		try {
			GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
			clientAttributesPushed = true;
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
					GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glShadeModel(GL11.GL_SMOOTH);

			tessellator.startDrawing(GL11.GL_TRIANGLE_STRIP);
			for(int segment = 0; segment <= HALO_SEGMENTS; segment++) {
				double angle = Math.PI*2D*segment/HALO_SEGMENTS;
				float x = (float)Math.cos(angle);
				float y = (float)Math.sin(angle);
				float illumination = clamp(0.12F
						+0.73F*Math.max(0F,
								x*context.getLightX()
										+y*context.getLightZ())
						+0.15F*Math.max(0F, context.getLightY()));
				float tangentBoost = 0.55F+0.45F*illumination;
				float red = mix(fogRed, skyRed, 0.72F);
				float green = mix(fogGreen, skyGreen, 0.72F);
				float blue = mix(fogBlue, skyBlue, 0.72F);
				tessellator.setColorRGBA_F(red, green, blue,
						baseAlpha*tangentBoost);
				tessellator.addVertex(x*innerRadius, plane,
						y*innerRadius);
				tessellator.setColorRGBA_F(red, green, blue, 0F);
				tessellator.addVertex(x*outerRadius, plane,
						y*outerRadius);
			}
			tessellator.draw();
			return true;
		}
		finally {
			if(clientAttributesPushed)
				GL11.glPopClientAttrib();
			GL11.glPopAttrib();
		}
	}

	/**
	 * Continuous fallback for a planet billboard at an arbitrary position.
	 * The basis is reconstructed exactly like {@code RenderFreeSpaceSky}'s
	 * surface billboard so the atmosphere remains camera-facing without
	 * assuming that the body lies on one of the world axes.
	 */
	public static boolean renderFreeSpaceHalo(Tessellator tessellator,
			DimensionProperties properties, double centerX, double centerY,
			double centerZ, float groundRadius, float projectedRadiusPixels,
			float lightX, float lightY, float lightZ, float alpha) {
		if(tessellator == null || properties == null
				|| (!properties.isGasGiant()
						&& !properties.hasAtmosphere())
				|| !finite(centerX) || !finite(centerY)
				|| !finite(centerZ) || !finite(groundRadius)
				|| groundRadius <= 0F
				|| !finite(projectedRadiusPixels)
				|| projectedRadiusPixels < 2F
				|| Configuration.atmosphereRenderMode
						== AtmosphereRenderMode.OFF
				|| !isFixedFunctionAtmosphereSafe())
			return false;
		Snapshot overrides =
				properties.getAtmosphereVisualPropertiesSnapshot();
		if(!hasVisualLight(overrides))
			return false;

		double centerLength = Math.sqrt(centerX*centerX
				+centerY*centerY+centerZ*centerZ);
		if(!finite(centerLength) || centerLength < 0.000001D)
			return false;
		double viewX = centerX/centerLength;
		double viewY = centerY/centerLength;
		double viewZ = centerZ/centerLength;

		double rightX;
		double rightZ;
		if(Math.abs(viewX) < 0.001D && Math.abs(viewZ) < 0.001D) {
			rightX = 1D;
			rightZ = 0D;
		}
		else {
			double rightLength = Math.sqrt(viewZ*viewZ+viewX*viewX);
			if(!finite(rightLength) || rightLength < 0.000001D)
				return false;
			rightX = viewZ/rightLength;
			rightZ = -viewX/rightLength;
		}
		double upX = -rightZ*viewY;
		double upY = rightZ*viewX-rightX*viewZ;
		double upZ = rightX*viewY;

		double lightLength = Math.sqrt(lightX*lightX+lightY*lightY
				+lightZ*lightZ);
		if(!finite(lightLength) || lightLength < 0.000001D)
			return false;
		lightX /= lightLength;
		lightY /= lightLength;
		lightZ /= lightLength;
		float lightRight = (float)(lightX*rightX+lightZ*rightZ);
		float lightUp = (float)(lightX*upX+lightY*upY+lightZ*upZ);
		float forwardScattering = (float)Math.max(0D,
				lightX*viewX+lightY*viewY+lightZ*viewZ);

		double planetRadiusKm = overrides.hasPlanetRadiusKm()
				? overrides.getPlanetRadiusKm() : 6360D;
		double atmosphereHeightKm = overrides.hasAtmosphereHeightKm()
				? overrides.getAtmosphereHeightKm()
				: properties.isGasGiant() ? 200D : 100D;
		float physicalThickness = groundRadius*(float)Math.max(0.0001D,
				Math.min(0.5D, atmosphereHeightKm/planetRadiusKm));
		float onePixel = groundRadius/Math.max(projectedRadiusPixels, 1F);
		float outerRadius = groundRadius
				+Math.max(physicalThickness, onePixel);
		float overlap = Math.min(groundRadius*0.5F,
				Math.max(groundRadius*0.005F, onePixel*0.75F));
		float innerRadius = Math.max(0F, groundRadius-overlap);

		float pressure = visualPressure(properties,
				properties.isGasGiant());
		float baseAlpha = clamp(alpha*(properties.isGasGiant()
				? 0.72F : 0.50F)
				*(0.35F+0.65F*(1F-(float)Math.exp(-pressure))));
		float skyRed = overrides.hasRayleighColor()
				? sanitize(overrides.getRayleighColorComponent(0))
				: sanitizeComponent(properties.skyColor, 0, 0.45F);
		float skyGreen = overrides.hasRayleighColor()
				? sanitize(overrides.getRayleighColorComponent(1))
				: sanitizeComponent(properties.skyColor, 1, 0.65F);
		float skyBlue = overrides.hasRayleighColor()
				? sanitize(overrides.getRayleighColorComponent(2))
				: sanitizeComponent(properties.skyColor, 2, 1F);
		float fogRed = overrides.hasMieColor()
				? sanitize(overrides.getMieColorComponent(0))
				: sanitizeComponent(properties.fogColor, 0, 0.75F);
		float fogGreen = overrides.hasMieColor()
				? sanitize(overrides.getMieColorComponent(1))
				: sanitizeComponent(properties.fogColor, 1, 0.82F);
		float fogBlue = overrides.hasMieColor()
				? sanitize(overrides.getMieColorComponent(2))
				: sanitizeComponent(properties.fogColor, 2, 0.90F);
		float red = mix(fogRed, skyRed, 0.72F);
		float green = mix(fogGreen, skyGreen, 0.72F);
		float blue = mix(fogBlue, skyBlue, 0.72F);

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		boolean clientAttributesPushed = false;
		try {
			GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
			clientAttributesPushed = true;
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
					GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glShadeModel(GL11.GL_SMOOTH);

			if(projectedRadiusPixels
					>= DETAILED_SHELL_MIN_RADIUS_PIXELS) {
				GL11.glEnable(GL11.GL_CULL_FACE);
				GL11.glFrontFace(GL11.GL_CCW);
				GL11.glCullFace(centerLength < outerRadius
						? GL11.GL_FRONT : GL11.GL_BACK);
				renderDetailedShell(tessellator,
						centerX, centerY, centerZ, outerRadius,
						lightX, lightY, lightZ,
						red, green, blue, baseAlpha);
			}
			else {
				GL11.glDisable(GL11.GL_CULL_FACE);
				tessellator.startDrawing(GL11.GL_TRIANGLE_STRIP);
				for(int segment = 0;
						segment <= HALO_SEGMENTS; segment++) {
					double angle = Math.PI*2D*segment/HALO_SEGMENTS;
					float radialRight = (float)Math.cos(angle);
					float radialUp = (float)Math.sin(angle);
					double radialX =
							rightX*radialRight+upX*radialUp;
					double radialY = upY*radialUp;
					double radialZ =
							rightZ*radialRight+upZ*radialUp;
					float illumination = clamp(0.10F
							+0.72F*Math.max(0F,
									radialRight*lightRight
											+radialUp*lightUp)
							+0.18F*forwardScattering);
					tessellator.setColorRGBA_F(red, green, blue,
							baseAlpha*(0.5F+0.5F*illumination));
					tessellator.addVertex(
							centerX+radialX*innerRadius,
							centerY+radialY*innerRadius,
							centerZ+radialZ*innerRadius);
					tessellator.setColorRGBA_F(red, green, blue, 0F);
					tessellator.addVertex(
							centerX+radialX*outerRadius,
							centerY+radialY*outerRadius,
							centerZ+radialZ*outerRadius);
				}
				tessellator.draw();
			}
			return true;
		}
		finally {
			if(clientAttributesPushed)
				GL11.glPopClientAttrib();
			GL11.glPopAttrib();
		}
	}

	public static boolean renderStationHalo(Tessellator tessellator,
			DimensionProperties properties, float groundRadius,
			double centerY, float lightX, float lightY, float lightZ,
			float alpha) {
		if(properties == null || groundRadius <= 0F
				|| (!properties.isGasGiant()
						&& !properties.hasAtmosphere())
				|| Configuration.atmosphereRenderMode
						== AtmosphereRenderMode.OFF)
			return false;
		if(!isFixedFunctionAtmosphereSafe())
			return false;

		float pressure = visualPressure(properties,
				properties.isGasGiant());
		Snapshot overrides =
				properties.getAtmosphereVisualPropertiesSnapshot();
		if(!hasVisualLight(overrides))
			return false;
		float shellRatio = resolveShellRatio(properties, overrides,
				properties.isGasGiant(),
				properties.isGasGiant() ? 0.032F : 0.016F);
		float outerRadius = groundRadius*(1F+shellRatio
				*(0.8F+0.2F*Math.min(pressure, 4F)));
		float baseAlpha = clamp(alpha*(properties.isGasGiant()
				? 0.72F : 0.52F)
				*(0.35F+0.65F*(1F-(float)Math.exp(-pressure))));
		float skyRed = resolveRayleighComponent(properties, overrides,
				properties.skyColor, 0, 0.45F);
		float skyGreen = resolveRayleighComponent(properties, overrides,
				properties.skyColor, 1, 0.65F);
		float skyBlue = resolveRayleighComponent(properties, overrides,
				properties.skyColor, 2, 1F);
		float fogRed = resolveMieComponent(properties, overrides,
				properties.fogColor, 0, 0.75F);
		float fogGreen = resolveMieComponent(properties, overrides,
				properties.fogColor, 1, 0.82F);
		float fogBlue = resolveMieComponent(properties, overrides,
				properties.fogColor, 2, 0.90F);
		float red = mix(fogRed, skyRed, 0.72F);
		float green = mix(fogGreen, skyGreen, 0.72F);
		float blue = mix(fogBlue, skyBlue, 0.72F);
		float lightLength = (float)Math.sqrt(lightX*lightX
				+lightY*lightY+lightZ*lightZ);
		if(Float.isNaN(lightLength)
				|| Float.isInfinite(lightLength)
				|| lightLength <= 0.00001F)
			return false;
		lightX /= lightLength;
		lightY /= lightLength;
		lightZ /= lightLength;

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		boolean clientAttributesPushed = false;
		try {
			GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
			clientAttributesPushed = true;
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glFrontFace(GL11.GL_CCW);
			GL11.glCullFace(Math.abs(centerY) < outerRadius
					? GL11.GL_FRONT : GL11.GL_BACK);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
					GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glShadeModel(GL11.GL_SMOOTH);
			renderDetailedShell(tessellator,
					0D, centerY, 0D, outerRadius,
					lightX, lightY, lightZ,
					red, green, blue, baseAlpha);
			return true;
		}
		finally {
			if(clientAttributesPushed)
				GL11.glPopClientAttrib();
			GL11.glPopAttrib();
		}
	}

	/**
	 * One outward-wound 96x48 shell.  Per-vertex opacity combines the apparent
	 * limb from the current camera position with the same three-dimensional
	 * primary-light direction used by the surface and shader paths.
	 */
	private static void renderDetailedShell(Tessellator tessellator,
			double centerX, double centerY, double centerZ, float radius,
			float lightX, float lightY, float lightZ,
			float red, float green, float blue, float baseAlpha) {
		tessellator.startDrawingQuads();
		for(int latitude = 0;
				latitude < SHELL_LATITUDE_SEGMENTS; latitude++) {
			for(int longitude = 0;
					longitude < SHELL_LONGITUDE_SEGMENTS; longitude++) {
				submitShellVertex(tessellator, centerX, centerY, centerZ,
						radius, shellNormalOffset(latitude, longitude),
						lightX, lightY, lightZ,
						red, green, blue, baseAlpha);
				submitShellVertex(tessellator, centerX, centerY, centerZ,
						radius, shellNormalOffset(latitude+1, longitude),
						lightX, lightY, lightZ,
						red, green, blue, baseAlpha);
				submitShellVertex(tessellator, centerX, centerY, centerZ,
						radius,
						shellNormalOffset(latitude+1, longitude+1),
						lightX, lightY, lightZ,
						red, green, blue, baseAlpha);
				submitShellVertex(tessellator, centerX, centerY, centerZ,
						radius, shellNormalOffset(latitude, longitude+1),
						lightX, lightY, lightZ,
						red, green, blue, baseAlpha);
			}
		}
		tessellator.draw();
	}

	private static void submitShellVertex(Tessellator tessellator,
			double centerX, double centerY, double centerZ, float radius,
			int normalOffset,
			float lightX, float lightY, float lightZ,
			float red, float green, float blue, float baseAlpha) {
		float normalX = SHELL_NORMALS[normalOffset];
		float normalY = SHELL_NORMALS[normalOffset+1];
		float normalZ = SHELL_NORMALS[normalOffset+2];
		double positionX = centerX+normalX*radius;
		double positionY = centerY+normalY*radius;
		double positionZ = centerZ+normalZ*radius;
		double viewLength = Math.sqrt(positionX*positionX
				+positionY*positionY+positionZ*positionZ);
		double viewX = 0D;
		double viewY = 0D;
		double viewZ = 0D;
		if(finite(viewLength) && viewLength > 0.000001D) {
			viewX = -positionX/viewLength;
			viewY = -positionY/viewLength;
			viewZ = -positionZ/viewLength;
		}
		float viewAlignment = clamp((float)Math.abs(
				normalX*viewX+normalY*viewY+normalZ*viewZ));
		float limb = (float)Math.pow(1F-viewAlignment, 0.65D);
		float outerFade = smoothstep(0F, 0.12F, viewAlignment);
		float directLight = Math.max(0F, (float)(
				normalX*lightX+normalY*lightY+normalZ*lightZ));
		float forwardLight = Math.max(0F, (float)(
				viewX*lightX+viewY*lightY+viewZ*lightZ));
		float illumination = clamp(0.10F+0.74F*directLight
				+0.16F*forwardLight);
		float vertexAlpha = clamp(baseAlpha*limb*outerFade
				*(0.42F+0.58F*illumination));
		tessellator.setColorRGBA_F(red, green, blue, vertexAlpha);
		tessellator.addVertex(positionX, positionY, positionZ);
	}

	private static int shellNormalOffset(int latitude, int longitude) {
		return (latitude*(SHELL_LONGITUDE_SEGMENTS+1)+longitude)*3;
	}

	public static float visualPressure(DimensionProperties properties,
			boolean gasGiant) {
		float pressure = 1F;
		if(properties != null)
			pressure = clamp(properties.getAtmosphereDensity()/100F, 0F, 16F);
		if(pressure > 1F)
			pressure = Math.min(4F, 1F+0.75F
					*(float)(Math.log(pressure)/Math.log(2D)));
		if(gasGiant)
			pressure = Math.max(2F, pressure);
		return Math.max(0F, pressure);
	}

	private static float sanitizeComponent(float[] input, int component,
			float fallback) {
		return input == null || input.length <= component
				? fallback : sanitize(input[component]);
	}

	private static float resolveShellRatio(DimensionProperties properties,
			Snapshot overrides, boolean gasGiant, float fallback) {
		if(properties == null || overrides == null)
			return fallback;
		double planetRadiusKm = overrides.hasPlanetRadiusKm()
				? overrides.getPlanetRadiusKm() : 6360D;
		double atmosphereHeightKm = overrides.hasAtmosphereHeightKm()
				? overrides.getAtmosphereHeightKm()
				: gasGiant ? 200D : 100D;
		if(!finite(planetRadiusKm) || !finite(atmosphereHeightKm)
				|| planetRadiusKm <= 0D || atmosphereHeightKm <= 0D)
			return fallback;
		return (float)Math.max(0.0001D,
				Math.min(0.5D, atmosphereHeightKm/planetRadiusKm));
	}

	private static boolean hasVisualLight(Snapshot overrides) {
		return overrides == null
				|| !overrides.hasSunIntensityMultiplier()
				|| overrides.getSunIntensityMultiplier() > 0.0D;
	}

	private static float resolveRayleighComponent(
			DimensionProperties properties, Snapshot overrides,
			float[] legacyColor, int component, float fallback) {
		if(properties != null && overrides != null
				&& overrides.hasRayleighColor())
			return sanitize(overrides.getRayleighColorComponent(component));
		return sanitizeComponent(legacyColor, component, fallback);
	}

	private static float resolveMieComponent(
			DimensionProperties properties, Snapshot overrides,
			float[] legacyColor, int component, float fallback) {
		if(properties != null && overrides != null
				&& overrides.hasMieColor())
			return sanitize(overrides.getMieColorComponent(component));
		return sanitizeComponent(legacyColor, component, fallback);
	}

	private static float sanitize(float value) {
		if(Float.isNaN(value) || Float.isInfinite(value))
			return 1F;
		return clamp(value, 0F, 4F);
	}

	private static float mix(float first, float second, float amount) {
		return first+(second-first)*amount;
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		float amount = clamp(
				(value-edge0)/(edge1-edge0));
		return amount*amount*(3F-2F*amount);
	}

	private static float clamp(float value) {
		return clamp(value, 0F, 1F);
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static boolean finite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}
}
