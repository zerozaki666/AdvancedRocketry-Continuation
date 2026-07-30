package zmaster587.advancedRocketry.client.render.atmosphere;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import zmaster587.advancedRocketry.api.AtmosphereRenderMode;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.client.render.planet.PlanetRenderContext;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties.CloudLayerMode;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties.Snapshot;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.util.AstronomicalBodyHelper;

/**
 * Client-only atmosphere path selection, LUT lifecycle and immediate drawing
 * entry points.  The renderer never takes over an unknown external GLSL
 * program; every mode safely skips while one is bound.
 */
public final class AtmosphereRenderManager
		implements IResourceManagerReloadListener {

	public enum Path {
		NO_ATMOSPHERE,
		NO_LIGHT,
		OFF,
		EXTERNAL_SAFE_SKIP,
		LEGACY,
		FAST,
		HIGH,
		POST_SUBMIT_FAILED,
		FAILED;

		public boolean renderedPhysicalSky() {
			return this == LEGACY || this == FAST || this == HIGH
					|| this == POST_SUBMIT_FAILED;
		}

		public boolean suppressesLegacyAtmosphere() {
			return renderedPhysicalSky() || this == OFF
					|| this == EXTERNAL_SAFE_SKIP || this == NO_LIGHT;
		}

		public boolean usedShader() {
			return this == FAST || this == HIGH;
		}
	}

	public static final AtmosphereRenderManager INSTANCE =
			new AtmosphereRenderManager();

	private static final ResourceLocation SURFACE_VERTEX =
			new ResourceLocation(
					"advancedrocketry:shaders/atmosphere_surface.vert");
	private static final ResourceLocation SURFACE_FAST_FRAGMENT =
			new ResourceLocation(
					"advancedrocketry:shaders/atmosphere_surface_fast.frag");
	private static final ResourceLocation SURFACE_HIGH_FRAGMENT =
			new ResourceLocation(
					"advancedrocketry:shaders/atmosphere_surface_high.frag");
	private static final ResourceLocation SPACE_VERTEX =
			new ResourceLocation(
					"advancedrocketry:shaders/atmosphere_space.vert");
	private static final ResourceLocation SPACE_FAST_FRAGMENT =
			new ResourceLocation(
					"advancedrocketry:shaders/atmosphere_space_fast.frag");
	private static final ResourceLocation SPACE_HIGH_FRAGMENT =
			new ResourceLocation(
					"advancedrocketry:shaders/atmosphere_space_high.frag");

	private static final int FALLBACK_COLUMNS = 32;
	private static final int FALLBACK_ROWS = 18;
	private static final int SPACE_PROFILE_CACHE_SIZE = 32;
	private static final float CAMERA_Q_EDGE = 1F/200F;

	private final AtmosphereProfileResolver profileResolver =
			new AtmosphereProfileResolver();
	private final ProfileLastHit surfaceProfileCache =
			new ProfileLastHit();
	private final ProfileFixedCache spaceProfileCache =
			new ProfileFixedCache(SPACE_PROFILE_CACHE_SIZE);
	private final StellarRadianceLastHit surfaceRadianceCache =
			new StellarRadianceLastHit();
	private final StellarRadianceLastHit spaceRadianceCache =
			new StellarRadianceLastHit();
	private final CelestialLightResolver lightResolver =
			new CelestialLightResolver();
	private final AtmosphereLightingContext lighting =
			new AtmosphereLightingContext();
	private final AtmosphereLutCache lutCache = new AtmosphereLutCache();
	private final AtmosphereGlState glState = new AtmosphereGlState();
	private final AtmosphereRenderCapabilities capabilities =
			new AtmosphereRenderCapabilities();
	private final AtmosphereShaderProgram surfaceFast =
			new AtmosphereShaderProgram(SURFACE_VERTEX,
					SURFACE_FAST_FRAGMENT, "surface atmosphere FAST");
	private final AtmosphereShaderProgram surfaceHigh =
			new AtmosphereShaderProgram(SURFACE_VERTEX,
					SURFACE_HIGH_FRAGMENT, "surface atmosphere HIGH");
	private final AtmosphereShaderProgram spaceFast =
			new AtmosphereShaderProgram(SPACE_VERTEX,
					SPACE_FAST_FRAGMENT, "space atmosphere FAST");
	private final AtmosphereShaderProgram spaceHigh =
			new AtmosphereShaderProgram(SPACE_VERTEX,
					SPACE_HIGH_FRAGMENT, "space atmosphere HIGH");

	private final FloatBuffer projectionBuffer =
			BufferUtils.createFloatBuffer(16);
	private final FloatBuffer modelViewBuffer =
			BufferUtils.createFloatBuffer(16);
	private final Matrix4f projection = new Matrix4f();
	private final Matrix4f inverseProjection = new Matrix4f();
	private final Matrix4f modelView = new Matrix4f();
	private final Matrix4f inverseModelView = new Matrix4f();
	private final Vector4f sourceVector = new Vector4f();
	private final Vector4f intermediateVector = new Vector4f();
	private final Vector4f resultVector = new Vector4f();
	private final float[] cornerRays = new float[12];
	private final float[] fallbackColor = new float[3];

	private WorldClient trackedWorld;
	private IReloadableResourceManager registeredResourceManager;
	private DimensionProperties lastSurfaceProperties;
	private AtmosphereVisualProfile lastSurfaceProfile;
	private boolean lastSurfaceHasPositiveRadiance;
	private boolean forgeEventsRegistered;
	private boolean surfacePostSubmitFailureLatched;
	private boolean spacePostSubmitFailureLatched;

	private AtmosphereRenderManager() {
	}

	public synchronized Path renderSurface(DimensionProperties properties,
			double cameraY, float lightDirectionX, float lightDirectionY,
			float lightDirectionZ, float rainStrength,
			long worldTime, float partialTicks) {
		if(properties == null)
			return Path.NO_ATMOSPHERE;
		AtmosphereVisualProfile profile;
		try {
			profile = resolveProfile(properties, surfaceProfileCache);
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce("surface-profile",
					"Could not resolve the surface atmosphere profile.",
					throwable);
			return Path.FAILED;
		}
		if(!profile.isEnabled()) {
			lastSurfaceProperties = null;
			lastSurfaceProfile = null;
			lastSurfaceHasPositiveRadiance = false;
			return Path.NO_ATMOSPHERE;
		}

		Minecraft minecraft = Minecraft.getMinecraft();
		ensureLifecycleHooks(minecraft);
		trackWorld(minecraft);
		lastSurfaceProperties = properties;
		lastSurfaceProfile = profile;
		AtmosphereRenderCapabilities frameCapabilities =
				capabilities.detect();
		Path requested = selectPath(frameCapabilities);
		if(requested.usedShader()
				&& surfacePostSubmitFailureLatched)
			requested = Path.LEGACY;
		if(requested == Path.OFF
				|| requested == Path.EXTERNAL_SAFE_SKIP
				|| requested == Path.FAILED)
			return requested;

		if(!resolveSurfaceLight(properties, profile, lightDirectionX,
				lightDirectionY, lightDirectionZ,
				rainStrength, worldTime, partialTicks)) {
			lastSurfaceHasPositiveRadiance = false;
			return Path.FAILED;
		}
		lastSurfaceHasPositiveRadiance =
				lighting.hasPositiveRadiance();
		if(!captureWorldCornerRays())
			return Path.FAILED;

		AtmosphereGlState state = null;
		boolean atmosphereSubmissionStarted = false;
		boolean shaderSubmissionStarted = false;
		try {
			state = glState.capture(frameCapabilities);
			Path effective = requested;
			AtmosphereLutCache.Binding lut = null;
			AtmosphereShaderProgram program = null;
			if(requested.usedShader()) {
				lut = lutCache.acquire(profile, lutWidth(), lutHeight());
				program = requested == Path.HIGH
						? surfaceHigh : surfaceFast;
				if(lut == null || !program.ensureLoaded(
						minecraft.getResourceManager())) {
					effective = Path.LEGACY;
					AtmosphereDiagnostics.warnOnce(
							"surface-lut-pending",
							"Surface optical-depth data is not ready; using the continuous fallback until it is available.");
				}
			}

			clearGlErrors();
			atmosphereSubmissionStarted = true;
			if(effective.usedShader()) {
				shaderSubmissionStarted = true;
				renderSurfaceShader(program, lut, properties, profile,
						cameraY, effective == Path.HIGH);
			}
			else
				renderSurfaceFallback(profile);
			int error = GL11.glGetError();
			if(error != GL11.GL_NO_ERROR) {
				if(effective.usedShader())
					surfacePostSubmitFailureLatched = true;
				AtmosphereDiagnostics.warnOnce(
						"surface-gl-" + error,
						"Surface atmosphere draw returned GL error 0x"
						+ Integer.toHexString(error)
						+ "; the compatibility sky will be suppressed"
						+ (effective.usedShader()
								? " for this frame and subsequent frames will use the continuous fallback."
								: " for this frame."));
				return Path.POST_SUBMIT_FAILED;
			}
			return effective;
		}
		catch(Throwable throwable) {
			if(shaderSubmissionStarted)
				surfacePostSubmitFailureLatched = true;
			AtmosphereDiagnostics.warnOnce("surface-pass",
					atmosphereSubmissionStarted
							? "Surface atmosphere rendering failed after submission; suppressing the compatibility sky for this frame."
							: "Surface atmosphere rendering failed before submission; retaining the compatibility sky for this frame.",
					throwable);
			return atmosphereSubmissionStarted
					? Path.POST_SUBMIT_FAILED : Path.FAILED;
		}
		finally {
			if(state != null)
				state.restore();
		}
	}

	/**
	 * Draws one analytic outer shell for the station/space view.  LEGACY is
	 * returned without drawing so the caller can use its single radial halo.
	 */
	public synchronized Path renderSpace(DimensionProperties properties,
			float displayGroundRadius, double centerY,
			PlanetRenderContext context, int unitSphereDisplayList,
			long worldTime, float partialTicks) {
		return renderSpace(properties, displayGroundRadius, 0D, centerY, 0D,
				context, unitSphereDisplayList, worldTime, partialTicks);
	}

	/**
	 * Full-center overload used by arbitrary free-space bodies.  Station
	 * callers retain the Y-only compatibility adapter above.
	 */
	public synchronized Path renderSpace(DimensionProperties properties,
			float displayGroundRadius, double centerX, double centerY,
			double centerZ, PlanetRenderContext context,
			int unitSphereDisplayList, long worldTime, float partialTicks) {
		if(context == null)
			return Path.NO_ATMOSPHERE;
		return renderSpace(properties, displayGroundRadius,
				centerX, centerY, centerZ,
				context.getLightX(), context.getLightY(),
				context.getLightZ(), context.getAlpha(),
				unitSphereDisplayList, worldTime, partialTicks);
	}

	/**
	 * Allocation-free full-center entry point used by free-space bodies.
	 * Direction normalization and alpha clamping remain inside the existing
	 * lighting/uniform paths.
	 */
	public synchronized Path renderSpace(DimensionProperties properties,
			float displayGroundRadius, double centerX, double centerY,
			double centerZ, float lightDirectionX, float lightDirectionY,
			float lightDirectionZ, float alpha, int unitSphereDisplayList,
			long worldTime, float partialTicks) {
		if(properties == null
				|| displayGroundRadius <= 0F
				|| !AtmosphereMath.isFinite(displayGroundRadius)
				|| !AtmosphereMath.isFinite(centerX)
				|| !AtmosphereMath.isFinite(centerY)
				|| !AtmosphereMath.isFinite(centerZ))
			return Path.NO_ATMOSPHERE;
		AtmosphereVisualProfile profile;
		try {
			profile = resolveSpaceProfile(properties);
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce("space-profile",
					"Could not resolve the space atmosphere profile; using the continuous fallback.",
					throwable);
			return Path.LEGACY;
		}
		if(!profile.isEnabled())
			return Path.NO_ATMOSPHERE;

		Minecraft minecraft = Minecraft.getMinecraft();
		ensureLifecycleHooks(minecraft);
		trackWorld(minecraft);
		AtmosphereRenderCapabilities frameCapabilities =
				capabilities.detect();
		Path requested = selectPath(frameCapabilities);
		if(requested.usedShader()
				&& (Configuration.atmosphereMaxShaderBodies <= 0
						|| spacePostSubmitFailureLatched))
			requested = Path.LEGACY;
		if(requested == Path.OFF
				|| requested == Path.EXTERNAL_SAFE_SKIP
				|| requested == Path.FAILED)
			return requested;
		if(!resolveSpaceLight(properties, profile,
				lightDirectionX, lightDirectionY, lightDirectionZ,
				worldTime, partialTicks))
			return Path.NO_LIGHT;
		if(!lighting.hasPositiveRadiance())
			return Path.NO_LIGHT;
		if(requested == Path.LEGACY)
			return Path.LEGACY;
		if(!isDisplayListUsable(unitSphereDisplayList)) {
			AtmosphereDiagnostics.warnOnce(
					"space-display-list",
					"Space atmosphere geometry is unavailable; using the continuous fallback.");
			return Path.LEGACY;
		}

		float[] lightEye = fallbackColor;
		if(!transformDirectionToEye(lightDirectionX,
				lightDirectionY, lightDirectionZ, lightEye))
			return Path.LEGACY;
		float displayAtmosphereRadius =
				(float)AtmosphereShellGeometry.physicalOuterRadius(
						displayGroundRadius,
						profile.getAtmosphereHeightKm()
								/profile.getGroundRadiusKm());
		double centerDistanceSquared = centerX*centerX
				+centerY*centerY+centerZ*centerZ;
		boolean cameraInsideAtmosphere = centerDistanceSquared
				<(double)displayAtmosphereRadius*displayAtmosphereRadius;
		float renderAlpha = AtmosphereMath.isFinite(alpha)
				? clamp(alpha) : 0F;

		AtmosphereGlState state = null;
		boolean submitted = false;
		try {
			state = glState.capture(frameCapabilities);
			AtmosphereLutCache.Binding lut =
					lutCache.acquire(profile, lutWidth(), lutHeight());
			AtmosphereShaderProgram program = requested == Path.HIGH
					? spaceHigh : spaceFast;
			if(lut == null || !program.ensureLoaded(
					minecraft.getResourceManager()))
				return Path.LEGACY;

			clearGlErrors();
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glCullFace(cameraInsideAtmosphere
					? GL11.GL_FRONT : GL11.GL_BACK);
			GL11.glFrontFace(GL11.GL_CCW);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_ONE,
					GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, lut.textureId);

			program.use();
			setCommonUniforms(program, lut, profile,
					requested == Path.HIGH, renderAlpha);
			program.set3f("uLightDirectionEye",
					lightEye[0], lightEye[1], lightEye[2]);
			program.set1f("uKmPerViewUnit",
					(float)(profile.getGroundRadiusKm()
							/displayGroundRadius));

			GL11.glPushMatrix();
			try {
				GL11.glTranslated(centerX, centerY, centerZ);
				GL11.glScalef(displayAtmosphereRadius,
						displayAtmosphereRadius,
						displayAtmosphereRadius);
				int preSubmitError = GL11.glGetError();
				if(preSubmitError != GL11.GL_NO_ERROR) {
					AtmosphereDiagnostics.warnOnce(
							"space-gl-pre-submit-" + preSubmitError,
							"Space atmosphere setup returned GL error 0x"
							+ Integer.toHexString(preSubmitError)
							+ " before geometry submission; using the continuous fallback.");
					return Path.LEGACY;
				}
				submitted = true;
				GL11.glCallList(unitSphereDisplayList);
			}
			finally {
				GL11.glPopMatrix();
			}
			int error = GL11.glGetError();
			if(error != GL11.GL_NO_ERROR) {
				spacePostSubmitFailureLatched = true;
				AtmosphereDiagnostics.warnOnce(
						"space-gl-" + error,
						"Space atmosphere draw returned GL error 0x"
						+ Integer.toHexString(error)
						+ "; the current partial result was retained and subsequent frames will use the continuous fallback.");
				return Path.FAILED;
			}
			return requested;
		}
		catch(Throwable throwable) {
			if(submitted) {
				spacePostSubmitFailureLatched = true;
				AtmosphereDiagnostics.warnOnce("space-pass-post-submit",
						"Space atmosphere rendering failed after submission; the current partial result was retained and subsequent frames will use the continuous fallback.",
						throwable);
				return Path.FAILED;
			}
			AtmosphereDiagnostics.warnOnce("space-pass-pre-submit",
					"Space atmosphere setup failed before submission; using the continuous fallback.",
					throwable);
			return Path.LEGACY;
		}
		finally {
			try {
				if(frameCapabilities.supportsShaderPrograms())
					GL13.glActiveTexture(GL13.GL_TEXTURE0);
			}
			catch(Throwable ignored) {
			}
			if(state != null)
				state.restore();
		}
	}

	public synchronized float getStarVisibility(
			DimensionProperties properties, double cameraY,
			float lightElevation) {
		if(properties == null)
			return 1F;
		AtmosphereVisualProfile profile =
				properties == lastSurfaceProperties
						? lastSurfaceProfile : null;
		if(profile == null) {
			try {
				profile = resolveProfile(properties, surfaceProfileCache);
			}
			catch(Throwable ignored) {
				return 1F;
			}
		}
		if(!profile.isEnabled()
				|| Configuration.atmosphereRenderMode
						== AtmosphereRenderMode.OFF)
			return 1F;
		if(properties == lastSurfaceProperties
				&& !lastSurfaceHasPositiveRadiance)
			return 1F;
		float daylight = smoothstep(-0.12F, 0.16F, lightElevation);
		double cameraHeight = resolveCameraHeight(properties, profile,
				cameraY);
		double remainingColumn = Math.exp(-AtmosphereMath.clamp(
				cameraHeight/Math.max(
						profile.getRayleighScaleHeightKm(), 0.000001D),
				0.0D, AtmosphereMath.MAX_OPTICAL_DEPTH));
		double localDensity = properties.getAtmosphereDensityAtHeight(
				cameraY);
		if(!AtmosphereMath.isFinite(localDensity)
				|| localDensity <= 0.0D)
			remainingColumn = 0.0D;
		remainingColumn = AtmosphereMath.clamp(
				remainingColumn, 0.0D, 1.0D);
		float extinction = (float)Math.exp(
				-0.30D*profile.getVisualPressure()*remainingColumn);
		return clamp((float)(1.0D-daylight*remainingColumn)*extinction);
	}

	/**
	 * Resolves top-of-atmosphere stellar radiance without touching GL state.
	 * Free-space LOD callers use this before choosing either shader or
	 * analytic geometry so missing and zero-luminosity stars fail dark in
	 * every path.
	 */
	public synchronized boolean hasPositiveSpaceLight(
			DimensionProperties properties) {
		if(properties == null)
			return false;
		try {
			AtmosphereVisualProfile profile =
					resolveSpaceProfile(properties);
			return profile.isEnabled()
					&& spaceRadianceCache.resolve(
							properties.getStar(),
							properties.getSolarOrbitalDistance(),
							profile.getSunIntensityMultiplier())
					&& spaceRadianceCache.hasPositiveRadiance();
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce(
					"space-light-visibility",
					"Could not resolve free-space stellar radiance; "
							+ "the atmosphere was left dark.",
					throwable);
			return false;
		}
	}

	/**
	 * Returns the largest concentric proxy that the station path can draw for
	 * this body.  Shader modes include the analytic fallback envelope because
	 * capability or LUT failure can change paths within the same session.
	 */
	public synchronized double resolveMaximumSpaceShellDisplayRadius(
			DimensionProperties properties, double displayGroundRadius) {
		if(properties == null || displayGroundRadius <= 0.0D
				|| !AtmosphereMath.isFinite(displayGroundRadius))
			return displayGroundRadius;
		try {
			AtmosphereVisualProfile profile =
					resolveSpaceProfile(properties);
			if(!profile.isEnabled())
				return displayGroundRadius;

			AtmosphereRenderMode mode = Configuration.atmosphereRenderMode;
			if(mode == null)
				mode = AtmosphereRenderMode.AUTO;
			boolean positiveRadiance =
					profile.getSunIntensityMultiplier() > 0.0D
					&& spaceRadianceCache.resolve(
							properties.getStar(),
							properties.getSolarOrbitalDistance(),
							profile.getSunIntensityMultiplier())
					&& spaceRadianceCache.hasPositiveRadiance();
			boolean fallbackEnabled =
					mode != AtmosphereRenderMode.OFF
					&& positiveRadiance;
			boolean shaderEnabled = fallbackEnabled
					&& mode != AtmosphereRenderMode.LEGACY
					&& Configuration.advancedVFX
					&& Configuration.atmosphereMaxShaderBodies > 0;
			boolean cloudEnabled =
					Configuration.atmosphereEnableCloudLayer
					&& profile.getCloudLayerMode()
							!= CloudLayerMode.DISABLED;
			double shellRatio = profile.getAtmosphereHeightKm()
					/profile.getGroundRadiusKm();
			return AtmosphereShellGeometry.maximumRenderedRadius(
					displayGroundRadius, shellRatio,
					profile.getVisualPressure(), shaderEnabled,
					fallbackEnabled, cloudEnabled,
					profile.isGasGiant());
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce(
					"station-space-envelope",
					"Could not resolve the station atmosphere envelope; "
							+ "using a conservative camera-safe radius.",
					throwable);
			if(!properties.isGasGiant()
					&& !properties.hasAtmosphere())
				return displayGroundRadius;
			return displayGroundRadius*1.8D;
		}
	}

	/**
	 * Returns continuous primary-sun transmittance without allocating.  The
	 * normal surface render order has already cached the resolved profile.
	 */
	public synchronized float getSunVisibility(
			DimensionProperties properties, double cameraY,
			float lightElevation) {
		if(properties == null
				|| Configuration.atmosphereRenderMode
						== AtmosphereRenderMode.OFF)
			return 1F;

		AtmosphereVisualProfile profile =
				properties == lastSurfaceProperties
						? lastSurfaceProfile : null;
		if(profile == null) {
			try {
				profile = resolveProfile(properties, surfaceProfileCache);
			}
			catch(Throwable ignored) {
				return 1F;
			}
		}
		if(!profile.isEnabled())
			return 1F;
		if(properties == lastSurfaceProperties
				&& !lastSurfaceHasPositiveRadiance)
			return 0F;
		double localDensity = properties.getAtmosphereDensityAtHeight(
				cameraY);
		if(!AtmosphereMath.isFinite(localDensity)
				|| localDensity <= 0.0D)
			return 1F;
		double cameraHeight = resolveCameraHeight(properties, profile,
				cameraY);
		if(cameraHeight >= profile.getAtmosphereHeightKm())
			return 1F;
		return sunVisibility(profile, lightElevation, cameraHeight);
	}

	public synchronized void registerResourceReloadListener() {
		ensureLifecycleHooks(Minecraft.getMinecraft());
	}

	private AtmosphereVisualProfile resolveProfile(
			DimensionProperties properties, ProfileLastHit cache) {
		Snapshot overrides =
				properties.getAtmosphereVisualPropertiesSnapshot();
		if(cache.matches(properties, overrides))
			return cache.profile;
		AtmosphereVisualProfile profile = profileResolver.resolve(
				properties.getId(), properties.hasAtmosphere(),
				properties.isGasGiant(),
				properties.getAtmosphereDensity(), properties.skyColor,
				properties.fogColor, overrides);
		cache.store(properties, overrides, profile);
		return profile;
	}

	private AtmosphereVisualProfile resolveSpaceProfile(
			DimensionProperties properties) {
		Snapshot overrides =
				properties.getAtmosphereVisualPropertiesSnapshot();
		AtmosphereVisualProfile profile =
				spaceProfileCache.find(properties, overrides);
		if(profile != null)
			return profile;
		profile = profileResolver.resolve(
				properties.getId(), properties.hasAtmosphere(),
				properties.isGasGiant(),
				properties.getAtmosphereDensity(), properties.skyColor,
				properties.fogColor, overrides);
		spaceProfileCache.store(properties, overrides, profile);
		return profile;
	}

	private Path selectPath(AtmosphereRenderCapabilities capabilities) {
		AtmosphereRenderMode mode = Configuration.atmosphereRenderMode;
		if(mode == null)
			mode = AtmosphereRenderMode.AUTO;
		if(mode == AtmosphereRenderMode.OFF)
			return Path.OFF;
		if(!capabilities.isContextAvailable())
			return Path.EXTERNAL_SAFE_SKIP;
		if(capabilities.supportsShaderPrograms()
				&& capabilities.getCurrentProgram() != 0) {
			AtmosphereDiagnostics.warnOnce("external-program",
					"An external GLSL program is active; the internal molecular-atmosphere pass was safely skipped.");
			return Path.EXTERNAL_SAFE_SKIP;
		}
		if(!Configuration.advancedVFX
				|| mode == AtmosphereRenderMode.LEGACY)
			return Path.LEGACY;
		if(!capabilities.supportsAtmosphereShaders(
				lutWidth(), lutHeight())) {
			if(mode == AtmosphereRenderMode.FAST
					|| mode == AtmosphereRenderMode.HIGH)
				AtmosphereDiagnostics.warnOnce("missing-glsl20",
						"The requested atmosphere shader path is unavailable; using the continuous fallback.");
			return Path.LEGACY;
		}
		return mode == AtmosphereRenderMode.HIGH
				? Path.HIGH : Path.FAST;
	}

	private boolean resolveSurfaceLight(DimensionProperties properties,
			AtmosphereVisualProfile profile, float directionX,
			float directionY, float directionZ, float rainStrength,
			long worldTime, float partialTicks) {
		float rainAttenuation = 1F-0.65F*clamp(rainStrength);
		if(resolveCachedLight(properties, profile, directionX, directionY,
				directionZ, rainAttenuation, worldTime, partialTicks,
				surfaceRadianceCache))
			return true;

		// The vanilla overworld may expose a visible sun without an AR
		// StellarBody.  Other missing/invalid stellar metadata fails dark.
		lighting.reset();
		lighting.setFrame(worldTime, partialTicks);
		lighting.setAttenuation(rainAttenuation, 1F);
		if(!lighting.setDirections(directionX, directionY, directionZ,
				directionX, directionY, directionZ))
			return false;
		double compatibilityRadiance =
				properties.getId() == 0
						? profile.getSunIntensityMultiplier() : 0.0D;
		lighting.setPrimaryLightRadiance(compatibilityRadiance,
				compatibilityRadiance, compatibilityRadiance);
		return lighting.isValid();
	}

	private boolean resolveSpaceLight(DimensionProperties properties,
			AtmosphereVisualProfile profile, float directionX,
			float directionY, float directionZ, long worldTime,
			float partialTicks) {
		return resolveCachedLight(properties, profile, directionX,
				directionY, directionZ, 1F,
				worldTime, partialTicks, spaceRadianceCache);
	}

	private boolean resolveCachedLight(DimensionProperties properties,
			AtmosphereVisualProfile profile, float directionX,
			float directionY, float directionZ, float attenuation,
			long worldTime, float partialTicks,
			StellarRadianceLastHit cache) {
		StellarBody star = properties == null ? null : properties.getStar();
		int orbitalDistance = properties == null ? 0
				: properties.getSolarOrbitalDistance();
		if(!cache.resolve(star, orbitalDistance,
				profile.getSunIntensityMultiplier()))
			return false;
		return lightResolver.resolvePrecomputedRadiance(
				cache.radianceRed, cache.radianceGreen,
				cache.radianceBlue, directionX, directionY, directionZ,
				directionX, directionY, directionZ, attenuation, 1F,
				worldTime, partialTicks, lighting);
	}

	private void renderSurfaceShader(AtmosphereShaderProgram program,
			AtmosphereLutCache.Binding lut, DimensionProperties properties,
			AtmosphereVisualProfile profile, double cameraY,
			boolean highQuality) {
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, lut.textureId);

		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();

		program.use();
		setCommonUniforms(program, lut, profile, highQuality, 1F);
		float cameraHeight = resolveCameraHeight(properties, profile,
				cameraY);
		program.set3f("uCameraPosition", 0F,
				(float)profile.getGroundRadiusKm()+cameraHeight, 0F);
		program.set3f("uLightDirection",
				lighting.getPrimaryLightDirectionLocalX(),
				lighting.getPrimaryLightDirectionLocalY(),
				lighting.getPrimaryLightDirectionLocalZ());

		GL11.glBegin(GL11.GL_QUADS);
		submitClipVertex(-1F, -1F, 0);
		submitClipVertex(1F, -1F, 1);
		submitClipVertex(1F, 1F, 2);
		submitClipVertex(-1F, 1F, 3);
		GL11.glEnd();
		program.stop();
	}

	private void renderSurfaceFallback(AtmosphereVisualProfile profile) {
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glShadeModel(GL11.GL_SMOOTH);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();

		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		for(int row = 0; row < FALLBACK_ROWS; row++) {
			float y0 = -1F+2F*row/FALLBACK_ROWS;
			float y1 = -1F+2F*(row+1)/FALLBACK_ROWS;
			for(int column = 0; column < FALLBACK_COLUMNS; column++) {
				float x0 = -1F+2F*column/FALLBACK_COLUMNS;
				float x1 = -1F+2F*(column+1)/FALLBACK_COLUMNS;
				submitFallbackVertex(tessellator, profile, x0, y0);
				submitFallbackVertex(tessellator, profile, x1, y0);
				submitFallbackVertex(tessellator, profile, x1, y1);
				submitFallbackVertex(tessellator, profile, x0, y1);
			}
		}
		tessellator.draw();
	}

	private void setCommonUniforms(AtmosphereShaderProgram program,
			AtmosphereLutCache.Binding lut,
			AtmosphereVisualProfile profile, boolean highQuality,
			float alpha) {
		program.set1i("uOpticalDepthLut", 0);
		program.set2f("uLutSize", lut.width, lut.height);
		program.set3f("uLutDecodeScale",
				lut.rayleighDecodeScale, lut.mieDecodeScale,
				lut.absorptionDecodeScale);
		program.set1f("uGroundRadius",
				(float)profile.getGroundRadiusKm());
		program.set1f("uAtmosphereRadius",
				(float)profile.getAtmosphereRadiusKm());
		program.set1f("uRayleighScaleHeight",
				(float)profile.getRayleighScaleHeightKm());
		program.set1f("uMieScaleHeight",
				(float)profile.getMieScaleHeightKm());
		program.set1f("uAbsorptionCenter",
				(float)profile.getAbsorptionCenterKm());
		program.set1f("uAbsorptionWidth",
				(float)profile.getAbsorptionWidthKm());
		program.set3f("uBetaRayleigh",
				(float)profile.getBetaRayleighScatteringRed(),
				(float)profile.getBetaRayleighScatteringGreen(),
				(float)profile.getBetaRayleighScatteringBlue());
		program.set3f("uBetaMieScattering",
				(float)profile.getBetaMieScatteringRed(),
				(float)profile.getBetaMieScatteringGreen(),
				(float)profile.getBetaMieScatteringBlue());
		program.set3f("uBetaMieExtinction",
				(float)profile.getBetaMieExtinctionRed(),
				(float)profile.getBetaMieExtinctionGreen(),
				(float)profile.getBetaMieExtinctionBlue());
		program.set3f("uBetaAbsorption",
				(float)profile.getBetaAbsorptionRed(),
				(float)profile.getBetaAbsorptionGreen(),
				(float)profile.getBetaAbsorptionBlue());
		program.set3f("uRayleighTint",
				(float)profile.getRayleighTintRed(),
				(float)profile.getRayleighTintGreen(),
				(float)profile.getRayleighTintBlue());
		float lightMultiplier = lighting.getEffectiveLightMultiplier();
		program.set3f("uLightRadiance",
				lighting.getPrimaryLightRadianceRed()*lightMultiplier,
				lighting.getPrimaryLightRadianceGreen()*lightMultiplier,
				lighting.getPrimaryLightRadianceBlue()*lightMultiplier);
		program.set1f("uMieAnisotropy",
				(float)profile.getMieAnisotropy());
		program.set1f("uMultipleScattering", highQuality
				? (float)profile.getMultipleScatteringStrength() : 0F);
		program.set1f("uExposure", (float)profile.getExposure());
		program.set1f("uAlpha", clamp(alpha));
		program.set1i("uDebugView",
				debugView(Configuration.atmosphereDebugView));
	}

	private float resolveCameraHeight(DimensionProperties properties,
			AtmosphereVisualProfile profile, double cameraY) {
		double base = Math.max(
				profile.getAtmosphereDensity()/100.0D, 0.000001D);
		double local = properties.getAtmosphereDensityAtHeight(cameraY);
		if(!AtmosphereMath.isFinite(local))
			local = 0.0D;
		local = Math.max(0.0D, local);
		double q = AtmosphereMath.clampFinite(local/base, 0.0D, 1.0D,
				0.0D);
		double qGeometry = AtmosphereMath.clamp(
				(q-CAMERA_Q_EDGE)/(1.0D-CAMERA_Q_EDGE),
				0.0D, 1.0D);
		if(qGeometry <= 0.0D)
			return (float)profile.getAtmosphereHeightKm()
					+(q <= 0.0D ? 0.001F : 0F);
		double floor = Math.exp(-profile.getAtmosphereHeightKm()
				/profile.getRayleighScaleHeightKm());
		return (float)AtmosphereMath.clamp(
				-profile.getRayleighScaleHeightKm()
						*Math.log(Math.max(qGeometry, floor)),
				0.0D, profile.getAtmosphereHeightKm());
	}

	private boolean captureWorldCornerRays() {
		try {
			projectionBuffer.clear();
			GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
			projectionBuffer.rewind();
			projection.load(projectionBuffer);
			modelViewBuffer.clear();
			GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
			modelViewBuffer.rewind();
			modelView.load(modelViewBuffer);
			if(Matrix4f.invert(projection, inverseProjection) == null
					|| Matrix4f.invert(modelView, inverseModelView) == null)
				return false;
			return captureCorner(-1F, -1F, 0)
					&& captureCorner(1F, -1F, 1)
					&& captureCorner(1F, 1F, 2)
					&& captureCorner(-1F, 1F, 3);
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce("view-rays",
					"Could not reconstruct surface atmosphere view rays.",
					throwable);
			return false;
		}
	}

	private boolean captureCorner(float clipX, float clipY, int index) {
		sourceVector.set(clipX, clipY, 1F, 1F);
		Matrix4f.transform(inverseProjection, sourceVector,
				intermediateVector);
		if(Math.abs(intermediateVector.w) < 0.000001F)
			return false;
		intermediateVector.x /= intermediateVector.w;
		intermediateVector.y /= intermediateVector.w;
		intermediateVector.z /= intermediateVector.w;
		intermediateVector.w = 0F;
		Matrix4f.transform(inverseModelView, intermediateVector,
				resultVector);
		float length = (float)Math.sqrt(resultVector.x*resultVector.x
				+resultVector.y*resultVector.y
				+resultVector.z*resultVector.z);
		if(length < 0.000001F || Float.isNaN(length)
				|| Float.isInfinite(length))
			return false;
		int offset = index*3;
		cornerRays[offset] = resultVector.x/length;
		cornerRays[offset+1] = resultVector.y/length;
		cornerRays[offset+2] = resultVector.z/length;
		return true;
	}

	private boolean transformDirectionToEye(float x, float y, float z,
			float[] output) {
		try {
			modelViewBuffer.clear();
			GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
			modelViewBuffer.rewind();
			modelView.load(modelViewBuffer);
			sourceVector.set(x, y, z, 0F);
			Matrix4f.transform(modelView, sourceVector, resultVector);
			float length = (float)Math.sqrt(resultVector.x*resultVector.x
					+resultVector.y*resultVector.y
					+resultVector.z*resultVector.z);
			if(length < 0.000001F || Float.isNaN(length)
					|| Float.isInfinite(length))
				return false;
			output[0] = resultVector.x/length;
			output[1] = resultVector.y/length;
			output[2] = resultVector.z/length;
			return true;
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce("light-transform",
					"Could not transform the atmosphere light direction.",
					throwable);
			return false;
		}
	}

	private static float sunVisibility(AtmosphereVisualProfile profile,
			float lightElevation, double cameraHeight) {
		double absorptionColumn = absorptionVerticalColumn(profile,
				cameraHeight);
		double absorptionHeight = Math.max(0.1D, Math.min(
				profile.getAtmosphereHeightKm(),
				profile.getAbsorptionCenterKm()
						+0.5D*profile.getAbsorptionWidthKm()));
		return sunVisibility(lightElevation, profile.getGroundRadiusKm(),
				cameraHeight,
				profile.getBetaRayleighExtinctionRed(),
				profile.getBetaRayleighExtinctionGreen(),
				profile.getBetaRayleighExtinctionBlue(),
				profile.getRayleighScaleHeightKm(),
				profile.getBetaMieExtinctionRed(),
				profile.getBetaMieExtinctionGreen(),
				profile.getBetaMieExtinctionBlue(),
				profile.getMieScaleHeightKm(),
				profile.getBetaAbsorptionRed(),
				profile.getBetaAbsorptionGreen(),
				profile.getBetaAbsorptionBlue(),
				absorptionColumn, absorptionHeight);
	}

	private static float sunVisibility(float lightElevation,
			double groundRadius, double cameraHeight,
			double betaRayleighRed,
			double betaRayleighGreen, double betaRayleighBlue,
			double rayleighHeight, double betaMieRed,
			double betaMieGreen, double betaMieBlue, double mieHeight,
			double betaAbsorptionRed, double betaAbsorptionGreen,
			double betaAbsorptionBlue, double absorptionColumn,
			double absorptionHeight) {
		double mu = AtmosphereMath.clampFinite(lightElevation,
				-1.0D, 1.0D, -1.0D);
		double cameraRadius = Math.max(groundRadius,
				groundRadius+Math.max(0.0D, cameraHeight));
		double groundRatio = AtmosphereMath.clamp(
				groundRadius/Math.max(cameraRadius, 0.000001D),
				0.0D, 1.0D);
		double horizonMu = -Math.sqrt(Math.max(
				0.0D, 1.0D-groundRatio*groundRatio));
		if(mu <= horizonMu-0.02D)
			return 0F;

		double positiveMu = Math.max(0.0D, mu-horizonMu);
		double rayleighColumn = rayleighHeight*Math.exp(
				-Math.max(0.0D, cameraHeight)
						/Math.max(rayleighHeight, 0.000001D))
				*airMass(positiveMu, rayleighHeight, cameraRadius);
		double mieColumn = mieHeight*Math.exp(
				-Math.max(0.0D, cameraHeight)
						/Math.max(mieHeight, 0.000001D))
				*airMass(positiveMu, mieHeight, cameraRadius);
		double absorptionSlantColumn = absorptionColumn*airMass(
				positiveMu, absorptionHeight, cameraRadius);
		double transmittanceRed = Math.exp(-AtmosphereMath.clamp(
				betaRayleighRed*rayleighColumn
						+betaMieRed*mieColumn
						+betaAbsorptionRed*absorptionSlantColumn,
				0.0D, AtmosphereMath.MAX_OPTICAL_DEPTH));
		double transmittanceGreen = Math.exp(-AtmosphereMath.clamp(
				betaRayleighGreen*rayleighColumn
						+betaMieGreen*mieColumn
						+betaAbsorptionGreen*absorptionSlantColumn,
				0.0D, AtmosphereMath.MAX_OPTICAL_DEPTH));
		double transmittanceBlue = Math.exp(-AtmosphereMath.clamp(
				betaRayleighBlue*rayleighColumn
						+betaMieBlue*mieColumn
						+betaAbsorptionBlue*absorptionSlantColumn,
				0.0D, AtmosphereMath.MAX_OPTICAL_DEPTH));
		double horizonVisibility = smoothstepDouble(
				horizonMu-0.02D, horizonMu+0.005D, mu);
		return (float)AtmosphereMath.clamp(horizonVisibility
				*AtmosphereMath.luminance(transmittanceRed,
						transmittanceGreen, transmittanceBlue),
				0.0D, 1.0D);
	}

	private static double airMass(double positiveLightElevation,
			double scaleHeight, double groundRadius) {
		double safeHeight = Math.max(scaleHeight, 0.000001D);
		double safeRadius = Math.max(groundRadius, safeHeight);
		double curvature = 2.0D*safeHeight/(Math.PI*safeRadius);
		return Math.min(128.0D, 1.0D/Math.sqrt(
				positiveLightElevation*positiveLightElevation
						+curvature));
	}

	private static double absorptionVerticalColumn(
			AtmosphereVisualProfile profile, double cameraHeight) {
		double center = profile.getAbsorptionCenterKm();
		double width = Math.max(profile.getAbsorptionWidthKm(),
				0.000001D);
		double left = Math.max(Math.max(0.0D, cameraHeight),
				center-width);
		double right = Math.min(profile.getAtmosphereHeightKm(),
				center+width);
		if(right <= left)
			return 0.0D;

		double column = 0.0D;
		double risingRight = Math.min(center, right);
		if(risingRight > left) {
			double start = left-(center-width);
			double end = risingRight-(center-width);
			column += (end*end-start*start)/(2.0D*width);
		}
		double fallingLeft = Math.max(center, left);
		if(right > fallingLeft) {
			double top = center+width;
			column += (top*right-0.5D*right*right
					-(top*fallingLeft
							-0.5D*fallingLeft*fallingLeft))/width;
		}
		return Math.max(0.0D, column);
	}

	private static double smoothstepDouble(double edge0, double edge1,
			double value) {
		double x = AtmosphereMath.clamp(
				(value-edge0)/(edge1-edge0), 0.0D, 1.0D);
		return x*x*(3.0D-2.0D*x);
	}

	private void submitClipVertex(float clipX, float clipY, int corner) {
		int offset = corner*3;
		GL11.glTexCoord3f(cornerRays[offset], cornerRays[offset+1],
				cornerRays[offset+2]);
		GL11.glVertex4f(clipX, clipY, 0F, 1F);
	}

	private void submitFallbackVertex(Tessellator tessellator,
			AtmosphereVisualProfile profile, float clipX, float clipY) {
		float u = (clipX+1F)*0.5F;
		float v = (clipY+1F)*0.5F;
		float bottomX = mix(cornerRays[0], cornerRays[3], u);
		float bottomY = mix(cornerRays[1], cornerRays[4], u);
		float bottomZ = mix(cornerRays[2], cornerRays[5], u);
		float topX = mix(cornerRays[9], cornerRays[6], u);
		float topY = mix(cornerRays[10], cornerRays[7], u);
		float topZ = mix(cornerRays[11], cornerRays[8], u);
		float rayX = mix(bottomX, topX, v);
		float rayY = mix(bottomY, topY, v);
		float rayZ = mix(bottomZ, topZ, v);
		float length = (float)Math.sqrt(rayX*rayX+rayY*rayY+rayZ*rayZ);
		if(length > 0.000001F) {
			rayX /= length;
			rayY /= length;
			rayZ /= length;
		}
		computeFallbackColor(profile, rayX, rayY, rayZ, fallbackColor);
		tessellator.setColorRGBA_F(fallbackColor[0], fallbackColor[1],
				fallbackColor[2], 1F);
		tessellator.addVertex(clipX, clipY, 0D);
	}

	private void computeFallbackColor(AtmosphereVisualProfile profile,
			float rayX, float rayY, float rayZ, float[] output) {
		float lightX = lighting.getPrimaryLightDirectionLocalX();
		float lightY = lighting.getPrimaryLightDirectionLocalY();
		float lightZ = lighting.getPrimaryLightDirectionLocalZ();
		float day = smoothstep(-0.16F, 0.12F, lightY);
		float twilight = smoothstep(-0.34F, 0.02F, lightY)
				*(1F-smoothstep(0.08F, 0.45F, lightY));
		float horizon = (float)Math.pow(
				Math.max(0F, 1F-Math.abs(rayY)), 2.2D);
		float forward = (float)Math.pow(Math.max(0F,
				rayX*lightX+rayY*lightY+rayZ*lightZ), 18D);
		float pressure = (float)Math.min(4D,
				profile.getVisualPressure());
		float density = 1F-(float)Math.exp(-0.55F*pressure);
		float radiance = clamp(
				0.2126F*lighting.getPrimaryLightRadianceRed()
				+0.7152F*lighting.getPrimaryLightRadianceGreen()
				+0.0722F*lighting.getPrimaryLightRadianceBlue());
		float direct =
				lighting.getEffectiveLightMultiplier()*radiance;

		float rayleighRed = 0.175F
				*(float)profile.getRayleighTintRed();
		float rayleighGreen = 0.410F
				*(float)profile.getRayleighTintGreen();
		float rayleighBlue = (float)profile.getRayleighTintBlue();
		float zenith = density*day*direct
				*(0.24F+0.76F*Math.max(rayY, 0F));
		float haze = density*(0.12F+0.52F*day)*horizon*direct;
		float warm = density*twilight*forward*direct
				*(0.35F+0.65F*horizon);
		float night = (float)profile.getMultipleScatteringStrength()
				*(1F-day)*0.10F*radiance;

		float red = rayleighRed*zenith
				+(float)profile.getMieTintRed()*haze
				+1.35F*warm+rayleighRed*night;
		float green = rayleighGreen*zenith
				+(float)profile.getMieTintGreen()*haze
				+0.38F*warm+rayleighGreen*night;
		float blue = rayleighBlue*zenith
				+(float)profile.getMieTintBlue()*haze
				+0.07F*warm+rayleighBlue*night;
		float exposure = (float)profile.getExposure();
		output[0] = gammaEncode(1F-(float)Math.exp(-exposure*red));
		output[1] = gammaEncode(1F-(float)Math.exp(-exposure*green));
		output[2] = gammaEncode(1F-(float)Math.exp(-exposure*blue));
	}

	private static int debugView(String value) {
		if(value == null)
			return 0;
		if("TRANSMITTANCE".equalsIgnoreCase(value))
			return 1;
		if("RAYLEIGH".equalsIgnoreCase(value))
			return 2;
		if("MIE".equalsIgnoreCase(value))
			return 3;
		if("OPTICAL_DEPTH".equalsIgnoreCase(value))
			return 4;
		if("MULTIPLE_SCATTER".equalsIgnoreCase(value))
			return 5;
		return 0;
	}

	private int lutWidth() {
		return Math.max(16, Math.min(512,
				Configuration.atmosphereOpticalDepthLutWidth));
	}

	private int lutHeight() {
		return Math.max(8, Math.min(256,
				Configuration.atmosphereOpticalDepthLutHeight));
	}

	private void ensureLifecycleHooks(Minecraft minecraft) {
		if(!forgeEventsRegistered) {
			MinecraftForge.EVENT_BUS.register(this);
			forgeEventsRegistered = true;
		}
		IResourceManager resourceManager = minecraft.getResourceManager();
		if(resourceManager instanceof IReloadableResourceManager
				&& resourceManager != registeredResourceManager) {
			registeredResourceManager =
					(IReloadableResourceManager)resourceManager;
			registeredResourceManager.registerReloadListener(this);
		}
	}

	private void trackWorld(Minecraft minecraft) {
		if(trackedWorld != minecraft.theWorld) {
			disposeGlObjects();
			trackedWorld = minecraft.theWorld;
			lastSurfaceProperties = null;
			lastSurfaceProfile = null;
			lastSurfaceHasPositiveRadiance = false;
			surfaceProfileCache.clear();
			spaceProfileCache.clear();
			surfaceRadianceCache.clear();
			spaceRadianceCache.clear();
			surfacePostSubmitFailureLatched = false;
			spacePostSubmitFailureLatched = false;
		}
	}

	@Override
	public synchronized void onResourceManagerReload(
			IResourceManager resourceManager) {
		disposeGlObjects();
		lastSurfaceProperties = null;
		lastSurfaceProfile = null;
		lastSurfaceHasPositiveRadiance = false;
		surfaceProfileCache.clear();
		spaceProfileCache.clear();
		surfaceRadianceCache.clear();
		spaceRadianceCache.clear();
		surfacePostSubmitFailureLatched = false;
		spacePostSubmitFailureLatched = false;
		AtmosphereDiagnostics.resetResourceWarnings();
	}

	@SubscribeEvent
	public synchronized void onWorldUnload(WorldEvent.Unload event) {
		if(event.world != null && event.world.isRemote
				&& event.world == trackedWorld) {
			disposeGlObjects();
			trackedWorld = null;
			lastSurfaceProperties = null;
			lastSurfaceProfile = null;
			lastSurfaceHasPositiveRadiance = false;
			surfaceProfileCache.clear();
			spaceProfileCache.clear();
			surfaceRadianceCache.clear();
			spaceRadianceCache.clear();
			surfacePostSubmitFailureLatched = false;
			spacePostSubmitFailureLatched = false;
		}
	}

	public synchronized void dispose() {
		disposeGlObjects();
		trackedWorld = null;
		lastSurfaceProperties = null;
		lastSurfaceProfile = null;
		lastSurfaceHasPositiveRadiance = false;
		surfaceProfileCache.clear();
		spaceProfileCache.clear();
		surfaceRadianceCache.clear();
		spaceRadianceCache.clear();
		surfacePostSubmitFailureLatched = false;
		spacePostSubmitFailureLatched = false;
	}

	private void disposeGlObjects() {
		if(!Display.isCreated()) {
			surfaceFast.abandon();
			surfaceHigh.abandon();
			spaceFast.abandon();
			spaceHigh.abandon();
			lutCache.abandon();
			return;
		}
		surfaceFast.dispose();
		surfaceHigh.dispose();
		spaceFast.dispose();
		spaceHigh.dispose();
		lutCache.clear();
	}

	/**
	 * Two instances (surface and space) avoid alternating-view cache thrash.
	 * {@link StellarBody#getColorRGB8()} reuses the star's cached color and
	 * therefore avoids the array allocation performed by getColor().
	 */
	private static final class StellarRadianceLastHit {
		private StellarBody star;
		private int orbitalDistance;
		private int packedColor;
		private long brightnessBits;
		private long intensityBits;
		private double radianceRed;
		private double radianceGreen;
		private double radianceBlue;
		private boolean valid;

		private boolean resolve(StellarBody candidate, int candidateDistance,
				double sunIntensityMultiplier) {
			if(!AtmosphereMath.isFinite(sunIntensityMultiplier)
					|| sunIntensityMultiplier < 0.0D) {
				clear();
				return false;
			}
			if(sunIntensityMultiplier > 0.0D
					&& (candidate == null || candidateDistance <= 0)) {
				clear();
				return false;
			}
			double brightness = sunIntensityMultiplier == 0.0D
					? 0.0D : AstronomicalBodyHelper
							.getStellarBrightness(
									candidate, candidateDistance);
			if(!AtmosphereMath.isFinite(brightness)
					|| brightness <= 0.0D)
				brightness = 0.0D;
			int color = candidate == null ? 0
					: candidate.getColorRGB8();
			long candidateBrightnessBits =
					Double.doubleToLongBits(brightness);
			long candidateIntensityBits =
					Double.doubleToLongBits(sunIntensityMultiplier);
			if(valid && star == candidate
					&& orbitalDistance == candidateDistance
					&& packedColor == color
					&& brightnessBits == candidateBrightnessBits
					&& intensityBits == candidateIntensityBits)
				return true;

			double scaledBrightness = Math.min(
					CelestialLightResolver.MAX_PRIMARY_LIGHT_RADIANCE,
					brightness*sunIntensityMultiplier);
			double red = AtmosphereMath.srgbToLinear(
					(color & 0xFF)/255.0D);
			double green = AtmosphereMath.srgbToLinear(
					((color >>> 8) & 0xFF)/255.0D);
			double blue = AtmosphereMath.srgbToLinear(
					((color >>> 16) & 0xFF)/255.0D);
			star = candidate;
			orbitalDistance = candidateDistance;
			packedColor = color;
			brightnessBits = candidateBrightnessBits;
			intensityBits = candidateIntensityBits;
			radianceRed = Math.min(
					CelestialLightResolver.MAX_PRIMARY_LIGHT_RADIANCE,
					scaledBrightness*red);
			radianceGreen = Math.min(
					CelestialLightResolver.MAX_PRIMARY_LIGHT_RADIANCE,
					scaledBrightness*green);
			radianceBlue = Math.min(
					CelestialLightResolver.MAX_PRIMARY_LIGHT_RADIANCE,
					scaledBrightness*blue);
			valid = true;
			return true;
		}

		private boolean hasPositiveRadiance() {
			return valid && (radianceRed > 0.0D
					|| radianceGreen > 0.0D
					|| radianceBlue > 0.0D);
		}

		private void clear() {
			star = null;
			valid = false;
			radianceRed = 0.0D;
			radianceGreen = 0.0D;
			radianceBlue = 0.0D;
		}
	}

	/**
	 * Fixed-capacity multi-body cache.  All entries are constructed with the
	 * manager, so cycling among the free-space shader budget performs only
	 * identity/content checks in steady state.
	 */
	private static final class ProfileFixedCache {
		private final ProfileLastHit[] entries;
		private int lastHit = -1;
		private int nextReplacement;

		private ProfileFixedCache(int capacity) {
			if(capacity < 16)
				throw new IllegalArgumentException(
						"Space profile cache must hold at least 16 bodies");
			entries = new ProfileLastHit[capacity];
			for(int index = 0; index < entries.length; index++)
				entries[index] = new ProfileLastHit();
		}

		private AtmosphereVisualProfile find(
				DimensionProperties properties, Snapshot overrides) {
			if(lastHit >= 0
					&& entries[lastHit].matches(properties, overrides))
				return entries[lastHit].profile;
			for(int index = 0; index < entries.length; index++) {
				if(index != lastHit
						&& entries[index].matches(properties, overrides)) {
					lastHit = index;
					return entries[index].profile;
				}
			}
			return null;
		}

		private void store(DimensionProperties properties,
				Snapshot overrides, AtmosphereVisualProfile profile) {
			int target = -1;
			for(int index = 0; index < entries.length; index++) {
				if(entries[index].belongsTo(properties)) {
					target = index;
					break;
				}
				if(target < 0 && entries[index].isEmpty())
					target = index;
			}
			if(target < 0) {
				target = nextReplacement;
				nextReplacement = (nextReplacement+1)%entries.length;
			}
			entries[target].store(properties, overrides, profile);
			lastHit = target;
		}

		private void clear() {
			for(ProfileLastHit entry : entries)
				entry.clear();
			lastHit = -1;
			nextReplacement = 0;
		}
	}

	private static final class ProfileLastHit {
		private DimensionProperties properties;
		private Snapshot overrides;
		private AtmosphereVisualProfile profile;
		private float[] skyColor;
		private float[] fogColor;
		private int dimensionId;
		private int atmosphereDensity;
		private boolean gasGiant;
		private boolean hasAtmosphere;
		private int skyLength;
		private int skyRedBits;
		private int skyGreenBits;
		private int skyBlueBits;
		private int fogLength;
		private int fogRedBits;
		private int fogGreenBits;
		private int fogBlueBits;
		private int overrideHash;
		private int signature;

		private boolean matches(DimensionProperties candidate,
				Snapshot candidateOverrides) {
			if(properties != candidate || profile == null
					|| overrides != candidateOverrides)
				return false;
			float[] candidateSky = candidate.skyColor;
			float[] candidateFog = candidate.fogColor;
			int candidateSignature = contentSignature(candidate,
					candidateOverrides);
			return signature == candidateSignature
					&& dimensionId == candidate.getId()
					&& atmosphereDensity
							== candidate.getAtmosphereDensity()
					&& gasGiant == candidate.isGasGiant()
					&& hasAtmosphere == candidate.hasAtmosphere()
					&& skyColor == candidateSky
					&& fogColor == candidateFog
					&& skyLength == colorLength(candidateSky)
					&& skyRedBits == colorBits(candidateSky, 0)
					&& skyGreenBits == colorBits(candidateSky, 1)
					&& skyBlueBits == colorBits(candidateSky, 2)
					&& fogLength == colorLength(candidateFog)
					&& fogRedBits == colorBits(candidateFog, 0)
					&& fogGreenBits == colorBits(candidateFog, 1)
					&& fogBlueBits == colorBits(candidateFog, 2)
					&& overrideHash == snapshotHash(candidateOverrides);
		}

		private void store(DimensionProperties properties,
				Snapshot overrides, AtmosphereVisualProfile profile) {
			this.properties = properties;
			this.overrides = overrides;
			this.profile = profile;
			skyColor = properties.skyColor;
			fogColor = properties.fogColor;
			dimensionId = properties.getId();
			atmosphereDensity = properties.getAtmosphereDensity();
			gasGiant = properties.isGasGiant();
			hasAtmosphere = properties.hasAtmosphere();
			skyLength = colorLength(skyColor);
			skyRedBits = colorBits(skyColor, 0);
			skyGreenBits = colorBits(skyColor, 1);
			skyBlueBits = colorBits(skyColor, 2);
			fogLength = colorLength(fogColor);
			fogRedBits = colorBits(fogColor, 0);
			fogGreenBits = colorBits(fogColor, 1);
			fogBlueBits = colorBits(fogColor, 2);
			overrideHash = snapshotHash(overrides);
			signature = contentSignature(properties, overrides);
		}

		private void clear() {
			properties = null;
			overrides = null;
			profile = null;
			skyColor = null;
			fogColor = null;
		}

		private boolean belongsTo(DimensionProperties candidate) {
			return properties == candidate;
		}

		private boolean isEmpty() {
			return profile == null;
		}

		private static int contentSignature(
				DimensionProperties properties, Snapshot overrides) {
			int result = properties.getId();
			result = 31*result+properties.getAtmosphereDensity();
			result = 31*result+(properties.isGasGiant() ? 1231 : 1237);
			result = 31*result+(properties.hasAtmosphere()
					? 1231 : 1237);
			result = 31*result+colorSignature(properties.skyColor);
			result = 31*result+colorSignature(properties.fogColor);
			result = 31*result+snapshotHash(overrides);
			return result;
		}

		private static int colorSignature(float[] color) {
			int result = colorLength(color);
			result = 31*result+colorBits(color, 0);
			result = 31*result+colorBits(color, 1);
			result = 31*result+colorBits(color, 2);
			return result;
		}

		private static int colorLength(float[] color) {
			return color == null ? -1 : color.length;
		}

		private static int colorBits(float[] color, int component) {
			return color != null && color.length == 3
					? Float.floatToIntBits(color[component]) : 0;
		}

		private static int snapshotHash(Snapshot overrides) {
			return overrides == null ? 0 : overrides.getContentHash();
		}
	}

	private static float gammaEncode(float value) {
		return (float)Math.pow(clamp(value), 1D/2.2D);
	}

	private static boolean isDisplayListUsable(int displayList) {
		if(displayList <= 0 || !Display.isCreated())
			return false;
		try {
			return GL11.glIsList(displayList);
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce(
					"space-display-list-query",
					"Could not validate space atmosphere geometry.",
					throwable);
			return false;
		}
	}

	private static void clearGlErrors() {
		for(int index = 0; index < 32; index++) {
			if(GL11.glGetError() == GL11.GL_NO_ERROR)
				return;
		}
	}

	private static float mix(float first, float second, float amount) {
		return first+(second-first)*amount;
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		float x = clamp((value-edge0)/(edge1-edge0));
		return x*x*(3F-2F*x);
	}

	private static float clamp(float value) {
		return Math.max(0F, Math.min(1F, value));
	}
}
