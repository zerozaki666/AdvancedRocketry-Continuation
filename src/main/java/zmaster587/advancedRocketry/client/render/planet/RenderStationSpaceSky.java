package zmaster587.advancedRocketry.client.render.planet;

import org.lwjgl.opengl.GL11;

import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.util.AstronomicalBodyHelper;
import zmaster587.libVulpes.util.Vector3F;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

public class RenderStationSpaceSky extends RenderPlanetarySky {

	private static final int SPHERE_LONGITUDE_SEGMENTS = 64;
	private static final int SPHERE_LATITUDE_SEGMENTS = 32;
	private static final double SURFACE_ROTATION_PERIOD_MS = 1000000D;
	private static final double ATMOSPHERE_ROTATION_PERIOD_MS = 100000D;
	private static final float PLANET_SURFACE_DISTANCE = 10F;

	private final int stationPlanetSphereGlList;

	//Mostly vanilla code
	//TODO: make usable on other planets
	public RenderStationSpaceSky() {
		super();
		stationPlanetSphereGlList = GLAllocation.generateDisplayLists(1);
		GL11.glNewList(stationPlanetSphereGlList, GL11.GL_COMPILE);
		compileUnitSphere();
		GL11.glEndList();
	}

	Minecraft mc = Minecraft.getMinecraft();

	@Override
	protected void renderPlanet2(Tessellator tessellator1, ResourceLocation icon, int locationX, int locationY, double zLevel, float planetOrbitalDistance, float alphaMultiplier, double angle, boolean hasAtmosphere, float[] atmColor, float[] ringColor, boolean isGasgiant, boolean hasRings)  {

		ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords((int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);

		if(object == null)
			return;

		planetOrbitalDistance = object.getOrbitalDistance();
		if(planetOrbitalDistance <= 0F)
			return;

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
		double surfaceRotation = getRotationDegrees(
				SURFACE_ROTATION_PERIOD_MS, rotationSpeed);
		float atmosphereThickness = Math.min(4F, radius*0.05F);

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

			GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
			bindSphereTexture(icon);
			GL11.glColor4f(1F, 1F, 1F, alphaMultiplier);
			renderSphere(radius, centerY, surfaceRotation);

			if(isGasgiant) {
				renderGasGiantAtmosphere(radius, centerY,
						atmosphereThickness, rotationSpeed);
				renderAtmosphereTint(radius, centerY,
						atmosphereThickness, 0.5F, 0.5F, 1F);
			}
			else if(hasAtmosphere) {
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				bindSphereTexture(
						DimensionProperties.getAtmosphereLEOResource());
				GL11.glColor4f(1F, 1F, 1F, 0.8F);
				renderSphere(radius + atmosphereThickness*0.35F,
						centerY, getRotationDegrees(
								ATMOSPHERE_ROTATION_PERIOD_MS,
								rotationSpeed));
				renderAtmosphereTint(radius, centerY,
						atmosphereThickness, atmColor[0], atmColor[1],
						atmColor[2]);
			}
		}
		finally {
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	private void renderGasGiantAtmosphere(float radius, double centerY,
			float atmosphereThickness, double rotationSpeed) {
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		bindSphereTexture(DimensionProperties.getAtmosphereLEOResource());

		int layerCount = 6;
		for(int i = 0; i < layerCount; i++) {
			GL11.glColor4f(0.05F*(layerCount-i/6F),
					0.4F*(i/6F), 1F, 0.4F);
			double layerSpeed = rotationSpeed*(i-layerCount/4D);
			renderSphere(radius + atmosphereThickness
					*(i + 1)/(layerCount*2F), centerY,
					getRotationDegrees(ATMOSPHERE_ROTATION_PERIOD_MS,
							layerSpeed));
		}
	}

	private void renderAtmosphereTint(float radius, double centerY,
			float atmosphereThickness, float red, float green, float blue) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
				GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(red, green, blue, 0.08F);

		int layerCount = 5;
		for(int i = 0; i < layerCount; i++) {
			renderSphere(radius + atmosphereThickness
					*(i + 1)/layerCount, centerY, 0D);
		}
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	private void bindSphereTexture(ResourceLocation texture) {
		mc.renderEngine.bindTexture(texture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
	}

	private void renderSphere(float radius, double centerY,
			double rotationDegrees) {
		GL11.glPushMatrix();
		GL11.glTranslated(0D, centerY, 0D);
		// Keep the UV poles out of the center of the view.
		GL11.glRotated(90D, 1D, 0D, 0D);
		GL11.glRotated(rotationDegrees, 0D, 1D, 0D);
		GL11.glScalef(radius, radius, radius);
		GL11.glCallList(stationPlanetSphereGlList);
		GL11.glPopMatrix();
	}

	private static double getRotationDegrees(double periodMs,
			double speedMultiplier) {
		if(speedMultiplier == 0D || Double.isNaN(speedMultiplier)
				|| Double.isInfinite(speedMultiplier))
			return 0D;

		double cycles = Minecraft.getSystemTime()*speedMultiplier/periodMs;
		return (cycles - Math.floor(cycles))*360D;
	}

	private static void compileUnitSphere() {
		GL11.glBegin(GL11.GL_QUADS);
		for(int latitude = 0;
				latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
			double latitude0 = -Math.PI/2D
					+ Math.PI*latitude/SPHERE_LATITUDE_SEGMENTS;
			double latitude1 = -Math.PI/2D
					+ Math.PI*(latitude + 1)/SPHERE_LATITUDE_SEGMENTS;
			double v0 = (double)latitude/SPHERE_LATITUDE_SEGMENTS;
			double v1 = (double)(latitude + 1)
					/SPHERE_LATITUDE_SEGMENTS;

			for(int longitude = 0;
					longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
				double longitude0 = -Math.PI
						+ Math.PI*2D*longitude
								/SPHERE_LONGITUDE_SEGMENTS;
				double longitude1 = -Math.PI
						+ Math.PI*2D*(longitude + 1)
								/SPHERE_LONGITUDE_SEGMENTS;
				double u0 = (double)longitude
						/SPHERE_LONGITUDE_SEGMENTS;
				double u1 = (double)(longitude + 1)
						/SPHERE_LONGITUDE_SEGMENTS;

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
		Vector3F<Float> axis = getRotateAxis();
		//GL11.glRotatef(90f, axis.x, axis.y, axis.z);
		ISpaceObject obj = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords((int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);
		if(obj != null)
		{
		GL11.glRotated(obj.getRotation(ForgeDirection.UP)*360, 0, 1, 0);
		GL11.glRotated(obj.getRotation(ForgeDirection.EAST)*360, 1, 0, 0);
		}

		//GL11.glRotated(360, obj.getRotation(EnumFacing.EAST), obj.getRotation(EnumFacing.UP), obj.getRotation(EnumFacing.NORTH));

	}

	@Override
	protected ResourceLocation getTextureForPlanet(DimensionProperties properties) {
		return properties.getPlanetIconLEO();
	}
}
