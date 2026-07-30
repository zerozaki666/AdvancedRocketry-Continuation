package zmaster587.advancedRocketry.client.render.planet;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.client.render.atmosphere.AnalyticAtmosphereRenderer;
import zmaster587.advancedRocketry.client.render.atmosphere.AtmosphereRenderManager;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationTarget;
import zmaster587.advancedRocketry.stations.StationTargetResolver;
import zmaster587.advancedRocketry.util.AstronomicalBodyHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

public class RenderStationSpaceSky extends RenderPlanetarySky {

	private static final int SPHERE_LONGITUDE_SEGMENTS = 64;
	private static final int SPHERE_LATITUDE_SEGMENTS = 32;
	private static final double SURFACE_ROTATION_PERIOD_TICKS = 50000D;
	private static final double ATMOSPHERE_ROTATION_PERIOD_TICKS = 5000D;
	private static final float PLANET_SURFACE_DISTANCE = 10F;
	private static final double PLANET_TEXTURE_BASE_TILING = 2D;

	private static int stationPlanetSurfaceSphereGlList;
	private static int stationPlanetAtmosphereSphereGlList;
	private static int compiledTextureRepeat = -1;
	private static boolean sphereListFailureLogged;
	private static ContextCapabilities sphereListContext;
	private final FloatBuffer lightPosition = BufferUtils.createFloatBuffer(4);
	private final FloatBuffer lightAmbient = BufferUtils.createFloatBuffer(4);
	private final FloatBuffer lightDiffuse = BufferUtils.createFloatBuffer(4);
	private final int[] savedTextureParameters = new int[4];
	private ISpaceObject frameRotationObject;
	private double frameUpRotationCycles;
	private double frameEastRotationCycles;
	private float framePartialTicks;

	//Mostly vanilla code
	//TODO: make usable on other planets
	public RenderStationSpaceSky() {
		super();
		ensureStationSphereDisplayLists();
	}

	@Override
	public void render(float partialTicks, WorldClient world,
			Minecraft minecraft) {
		framePartialTicks = Float.isNaN(partialTicks)
				|| Float.isInfinite(partialTicks)
						? 0F : Math.max(0F, Math.min(1F, partialTicks));
		super.render(framePartialTicks, world, minecraft);
	}

	private static int getPlanetTextureRepeatCount() {
		double multiplier =
				Configuration.stationPlanetTextureTilingMultiplier;
		if(Double.isNaN(multiplier) || Double.isInfinite(multiplier))
			multiplier = 1D;

		multiplier = Math.max(0.5D, Math.min(16D, multiplier));
		return Math.max(1,
				(int)Math.round(PLANET_TEXTURE_BASE_TILING*multiplier));
	}

	private static boolean ensureStationSphereDisplayLists() {
		if(!Display.isCreated())
			return false;
		int textureRepeat = getPlanetTextureRepeatCount();
		try {
			ContextCapabilities currentContext =
					GLContext.getCapabilities();
			if(sphereListContext != currentContext) {
				stationPlanetSurfaceSphereGlList = 0;
				stationPlanetAtmosphereSphereGlList = 0;
				compiledTextureRepeat = -1;
				sphereListContext = currentContext;
				sphereListFailureLogged = false;
			}
			boolean surfaceValid =
					stationPlanetSurfaceSphereGlList > 0
					&& GL11.glIsList(
							stationPlanetSurfaceSphereGlList);
			boolean atmosphereValid =
					stationPlanetAtmosphereSphereGlList > 0
					&& GL11.glIsList(
							stationPlanetAtmosphereSphereGlList);
			if(surfaceValid && atmosphereValid
					&& compiledTextureRepeat == textureRepeat)
				return true;

			if(surfaceValid)
				GL11.glDeleteLists(
						stationPlanetSurfaceSphereGlList, 1);
			if(atmosphereValid)
				GL11.glDeleteLists(
						stationPlanetAtmosphereSphereGlList, 1);
			stationPlanetSurfaceSphereGlList = 0;
			stationPlanetAtmosphereSphereGlList = 0;
			compiledTextureRepeat = -1;

			int displayList = GLAllocation.generateDisplayLists(2);
			if(displayList <= 0)
				throw new IllegalStateException(
						"glGenLists returned no station sphere lists");

			GL11.glNewList(displayList, GL11.GL_COMPILE);
			compileUnitSphere(textureRepeat);
			GL11.glEndList();
			GL11.glNewList(displayList+1, GL11.GL_COMPILE);
			compileUnitSphere(1);
			GL11.glEndList();
			if(!GL11.glIsList(displayList)
					|| !GL11.glIsList(displayList+1)) {
				GL11.glDeleteLists(displayList, 2);
				throw new IllegalStateException(
						"compiled station sphere lists are invalid");
			}

			stationPlanetSurfaceSphereGlList = displayList;
			stationPlanetAtmosphereSphereGlList = displayList+1;
			compiledTextureRepeat = textureRepeat;
			sphereListFailureLogged = false;
			return true;
		}
		catch(Throwable throwable) {
			stationPlanetSurfaceSphereGlList = 0;
			stationPlanetAtmosphereSphereGlList = 0;
			compiledTextureRepeat = -1;
			if(!sphereListFailureLogged) {
				sphereListFailureLogged = true;
				AdvancedRocketry.logger.warn(
						"Could not create station planet sphere geometry.",
						throwable);
			}
			return false;
		}
	}

	Minecraft mc = Minecraft.getMinecraft();

	@Override
	protected void renderPlanet2(Tessellator tessellator1, ResourceLocation icon, int locationX, int locationY, double zLevel, float planetOrbitalDistance, float alphaMultiplier, double angle, boolean hasAtmosphere, float[] atmColor, float[] ringColor, boolean isGasgiant, boolean hasRings)  {
		renderPlanet2(tessellator1, PlanetRenderContext.legacy(icon,
				locationX, locationY, zLevel, planetOrbitalDistance,
				alphaMultiplier, angle, hasAtmosphere, atmColor, ringColor,
				isGasgiant, hasRings,
				PlanetRenderContext.ViewKind.STATION));
	}

	@Override
	protected void renderPlanet2(Tessellator tessellator1,
			PlanetRenderContext submittedContext) {
		ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords((int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);

		if(object == null || submittedContext == null)
			return;
		ensureStationSphereDisplayLists();

		float planetOrbitalDistance = object.getOrbitalDistance();
		if(planetOrbitalDistance <= 0F)
			return;

		StationTarget target = StationTargetResolver.getInstance().resolve(
				object.getOrbitingPlanetId());
		DimensionProperties targetProperties =
				target.getKind() == StationTarget.Kind.DIMENSION
						? target.getDimensionProperties() : null;
		PlanetRenderContext context = submittedContext;
		if(targetProperties != null) {
			context = PlanetRenderContext.fromProperties(targetProperties,
					submittedContext.getSurfaceTexture(),
					submittedContext.getLocationX(),
					submittedContext.getLocationY(),
					submittedContext.getZLevel(),
					submittedContext.getSize(),
					submittedContext.getAlpha(),
					submittedContext.getLegacyShadowAngle(),
					PlanetRenderContext.ViewKind.STATION)
					.withLightDirection(submittedContext.getLightX(),
							submittedContext.getLightY(),
							submittedContext.getLightZ());
		}

		float radius = (float)(66F
				*AstronomicalBodyHelper.getBodySizeMultiplier(
						planetOrbitalDistance)
				*Configuration.stationPlanetSphereScaleMultiplier);
		if(radius <= 0F || Float.isInfinite(radius) || Float.isNaN(radius))
			return;

		// Keep the nearest surface at the legacy plane's Y position while
		// ensuring the camera remains outside the sphere at every scale.
		double centerY = -PLANET_SURFACE_DISTANCE - radius;
		double rotationSpeed =
				Configuration.stationPlanetRotationSpeedMultiplier;
		long worldTime = mc.theWorld == null ? 0L
				: mc.theWorld.getTotalWorldTime();
		float partialTicks = framePartialTicks;
		double surfaceRotation = getRotationDegrees(worldTime, partialTicks,
				SURFACE_ROTATION_PERIOD_TICKS, rotationSpeed);
		ResourceLocation surfaceTexture = context.getSurfaceTexture();
		if(surfaceTexture == null)
			return;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		try {
			GL11.glDisable(GL11.GL_FOG);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glCullFace(GL11.GL_BACK);
			GL11.glFrontFace(GL11.GL_CCW);

			GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
					GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glColor4f(1F, 1F, 1F, context.getAlpha());
			enableSurfaceLighting(context);
			renderTexturedSphere(surfaceTexture, radius, centerY,
					surfaceRotation, stationPlanetSurfaceSphereGlList);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_LIGHT0);

			// atmosphereleo.png is retained only as an independent cloud/haze
			// layer.  Molecular scattering is rendered separately.
			if(targetProperties != null
					&& Configuration.atmosphereEnableCloudLayer
					&& isCloudLayerEnabled(targetProperties)
					&& (targetProperties.isGasGiant()
							|| targetProperties.hasAtmosphere())) {
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
						GL11.GL_ONE_MINUS_SRC_ALPHA);
				float[] fog = targetProperties.fogColor;
				float red = colorComponent(fog, 0, 1F);
				float green = colorComponent(fog, 1, 1F);
				float blue = colorComponent(fog, 2, 1F);
				GL11.glColor4f(red, green, blue,
						context.getAlpha()*(targetProperties.isGasGiant()
								? 0.48F : 0.32F));
				enableSurfaceLighting(context);
				renderTexturedSphere(
						DimensionProperties.getAtmosphereLEOResource(),
						radius*(targetProperties.isGasGiant()
										? 1.012F : 1.006F),
						centerY, getRotationDegrees(worldTime, partialTicks,
								ATMOSPHERE_ROTATION_PERIOD_TICKS,
								rotationSpeed),
						stationPlanetAtmosphereSphereGlList);
				GL11.glDisable(GL11.GL_LIGHTING);
				GL11.glDisable(GL11.GL_LIGHT0);
			}

			if(targetProperties != null) {
				AtmosphereRenderManager.Path atmospherePath =
						AtmosphereRenderManager.INSTANCE.renderSpace(
								targetProperties, radius, centerY, context,
								stationPlanetAtmosphereSphereGlList,
								worldTime, partialTicks);
				if(atmospherePath
						== AtmosphereRenderManager.Path.LEGACY)
					AnalyticAtmosphereRenderer.renderStationHalo(
							tessellator1, targetProperties, radius, centerY,
							context.getLightX(), context.getLightY(),
							context.getLightZ(),
							context.getAlpha());
			}
		}
		finally {
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	private void renderTexturedSphere(ResourceLocation texture, float radius,
			double centerY, double rotationDegrees, int sphereGlList) {
		if(texture == null || sphereGlList <= 0
				|| !Display.isCreated()
				|| !GL11.glIsList(sphereGlList))
			return;
		mc.renderEngine.bindTexture(texture);
		savedTextureParameters[0] = GL11.glGetTexParameteri(
				GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
		savedTextureParameters[1] = GL11.glGetTexParameteri(
				GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
		savedTextureParameters[2] = GL11.glGetTexParameteri(
				GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S);
		savedTextureParameters[3] = GL11.glGetTexParameteri(
				GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T);
		try {
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
			renderSphere(radius, centerY, rotationDegrees, sphereGlList);
		}
		finally {
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MAG_FILTER,
					savedTextureParameters[0]);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MIN_FILTER,
					savedTextureParameters[1]);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_S,
					savedTextureParameters[2]);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_T,
					savedTextureParameters[3]);
		}
	}

	private void renderSphere(float radius, double centerY,
			double rotationDegrees, int sphereGlList) {
		GL11.glPushMatrix();
		GL11.glTranslated(0D, centerY, 0D);
		// Keep the UV poles out of the center of the view.
		GL11.glRotated(90D, 1D, 0D, 0D);
		GL11.glRotated(rotationDegrees, 0D, 1D, 0D);
		GL11.glScalef(radius, radius, radius);
		GL11.glCallList(sphereGlList);
		GL11.glPopMatrix();
	}

	private static double getRotationDegrees(long worldTime,
			float partialTicks, double periodTicks, double speedMultiplier) {
		if(speedMultiplier == 0D || Double.isNaN(speedMultiplier)
				|| Double.isInfinite(speedMultiplier))
			return 0D;

		double cycles = (worldTime+Math.max(0F, Math.min(1F, partialTicks)))
				*speedMultiplier/periodTicks;
		return (cycles - Math.floor(cycles))*360D;
	}

	private void enableSurfaceLighting(PlanetRenderContext context) {
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_LIGHT0);
		GL11.glEnable(GL11.GL_NORMALIZE);
		GL11.glEnable(GL11.GL_COLOR_MATERIAL);
		GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK,
				GL11.GL_AMBIENT_AND_DIFFUSE);
		put(lightPosition, context.getLightX(), context.getLightY(),
				context.getLightZ(), 0F);
		put(lightAmbient, 0.12F, 0.12F, 0.14F, 1F);
		put(lightDiffuse, 0.88F, 0.88F, 0.86F, 1F);
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, lightPosition);
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, lightAmbient);
		GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, lightDiffuse);
	}

	private static void put(FloatBuffer buffer, float first, float second,
			float third, float fourth) {
		buffer.clear();
		buffer.put(first).put(second).put(third).put(fourth);
		buffer.flip();
	}

	private static float colorComponent(float[] color, int index,
			float fallback) {
		if(color == null || color.length <= index
				|| Float.isNaN(color[index])
				|| Float.isInfinite(color[index]))
			return fallback;
		return Math.max(0F, Math.min(4F, color[index]));
	}

	private static boolean isCloudLayerEnabled(
			DimensionProperties properties) {
		AtmosphereVisualProperties.Snapshot snapshot =
				properties.getAtmosphereVisualPropertiesSnapshot();
		return !snapshot.hasCloudLayerMode()
				|| snapshot.getCloudLayerMode()
						!= AtmosphereVisualProperties.CloudLayerMode.DISABLED;
	}

	private static void compileUnitSphere(int textureRepeats) {
		GL11.glBegin(GL11.GL_QUADS);
		for(int latitude = 0;
				latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
			double latitude0 = -Math.PI/2D
					+ Math.PI*latitude/SPHERE_LATITUDE_SEGMENTS;
			double latitude1 = -Math.PI/2D
					+ Math.PI*(latitude + 1)/SPHERE_LATITUDE_SEGMENTS;
			double v0 = (double)textureRepeats*latitude
					/SPHERE_LATITUDE_SEGMENTS;
			double v1 = (double)(latitude + 1)
					*textureRepeats/SPHERE_LATITUDE_SEGMENTS;

			for(int longitude = 0;
					longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
				double longitude0 = -Math.PI
						+ Math.PI*2D*longitude
								/SPHERE_LONGITUDE_SEGMENTS;
				double longitude1 = -Math.PI
						+ Math.PI*2D*(longitude + 1)
								/SPHERE_LONGITUDE_SEGMENTS;
				double u0 = (double)textureRepeats*longitude
						/SPHERE_LONGITUDE_SEGMENTS;
				double u1 = (double)(longitude + 1)
						*textureRepeats/SPHERE_LONGITUDE_SEGMENTS;

				addSphereVertex(latitude0, longitude0, u0, v0);
				addSphereVertex(latitude1, longitude0, u0, v1);
				addSphereVertex(latitude1, longitude1, u1, v1);
				addSphereVertex(latitude0, longitude1, u1, v0);
			}
		}
		GL11.glEnd();
	}

	private static void addSphereVertex(double latitude, double longitude,
			double u, double v) {
		double cosLatitude = Math.cos(latitude);
		double x = cosLatitude*Math.cos(longitude);
		double y = Math.sin(latitude);
		double z = cosLatitude*Math.sin(longitude);

		GL11.glNormal3d(x, y, z);
		GL11.glTexCoord2d(u, v);
		GL11.glVertex3d(x, y, z);
	}

	@Override
	protected ForgeDirection getRotationAxis(DimensionProperties properties,
			int x, int z) {
		try {
			return SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(x,z).getForwardDirection().getRotation(ForgeDirection.UP);
		} catch(Exception e) {
			return ForgeDirection.EAST;
		}
	}

	@Override
	protected void rotateAroundAxis() {
		ISpaceObject obj = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords((int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);
		if(obj != frameRotationObject) {
			frameRotationObject = obj;
			frameUpRotationCycles = obj == null ? 0D
					: obj.getRotation(ForgeDirection.UP);
			frameEastRotationCycles = obj == null ? 0D
					: obj.getRotation(ForgeDirection.EAST);
		}
		if(obj != null) {
			GL11.glRotated(frameUpRotationCycles*360D, 0D, 1D, 0D);
			GL11.glRotated(frameEastRotationCycles*360D, 1D, 0D, 0D);
		}

		//GL11.glRotated(360, obj.getRotation(EnumFacing.EAST), obj.getRotation(EnumFacing.UP), obj.getRotation(EnumFacing.NORTH));

	}

	@Override
	protected boolean resolvePrimaryLightDirection(
			double rotationalPhiDegrees, float[] output) {
		ISpaceObject object = SpaceObjectManager.getSpaceManager()
				.getSpaceStationFromBlockCoords((int)mc.thePlayer.posX,
						(int)mc.thePlayer.posZ);
		frameRotationObject = object;
		frameUpRotationCycles = object == null ? 0D
				: object.getRotation(ForgeDirection.UP);
		frameEastRotationCycles = object == null ? 0D
				: object.getRotation(ForgeDirection.EAST);
		return CelestialRenderDirection.resolveStation(
				rotationalPhiDegrees, frameUpRotationCycles,
				frameEastRotationCycles, output);
	}

	@Override
	protected ResourceLocation getTextureForPlanet(DimensionProperties properties) {
		return properties.getPlanetIconLEO();
	}
}
