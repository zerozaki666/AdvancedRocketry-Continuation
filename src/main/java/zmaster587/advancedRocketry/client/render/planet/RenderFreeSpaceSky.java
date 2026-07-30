package zmaster587.advancedRocketry.client.render.planet;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.client.render.atmosphere.AnalyticAtmosphereRenderer;
import zmaster587.advancedRocketry.client.render.atmosphere.AtmosphereRenderManager;
import zmaster587.advancedRocketry.client.render.blackhole.BlackHoleRenderManager;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.dimension.sim.SimBodySnapshot;
import zmaster587.advancedRocketry.dimension.sim.SimBodyType;
import zmaster587.advancedRocketry.dimension.sim.SimUniverse;
import zmaster587.advancedRocketry.inventory.TextureResources;

/**
 * Free-space sky with distance-based celestial-body rendering.
 */
public class RenderFreeSpaceSky extends RenderPlanetarySky {

	private static final double PROJECTION_DISTANCE = 80D;
	private static final int SPHERE_LONGITUDE_SEGMENTS = 64;
	private static final int SPHERE_LATITUDE_SEGMENTS = 32;
	private static final int MAX_SHADER_BODY_BUDGET = 16;
	private static final float MIN_SHADER_RADIUS_PIXELS = 6F;
	private static final ResourceLocation FAR_STAR =
			new ResourceLocation("advancedrocketry:textures/env/sunLodFar.png");
	private static int atmosphereSphereGlList;
	private static boolean atmosphereSphereFailureLogged;
	private static ContextCapabilities atmosphereSphereContext;
	private final SimBodySnapshot[] shaderBodies =
			new SimBodySnapshot[MAX_SHADER_BODY_BUDGET];
	private final float[] shaderBodyAreas =
			new float[MAX_SHADER_BODY_BUDGET];

	public RenderFreeSpaceSky() {
		super();
		ensureAtmosphereSphereDisplayList();
	}

	@Override
	public void render(float partialTicks, WorldClient world, Minecraft minecraft) {
		EntityPlayer player = minecraft.thePlayer;
		if(player == null)
			return;
		ensureAtmosphereSphereDisplayList();

		// In an integrated server this singleton is shared with the logical
		// server, which owns simulation time. The client must never rewind it.
		if(!AdvancedRocketry.proxy.isIntegratedServerRunning())
			SimUniverse.getInstance().tick(world.getTotalWorldTime());
		BlackHoleRenderManager.INSTANCE.beginFrame(
				world.getTotalWorldTime(), partialTicks);

		double playerX = player.prevPosX + (player.posX - player.prevPosX)*partialTicks;
		double playerY = player.prevPosY + (player.posY - player.prevPosY)*partialTicks;
		double playerZ = player.prevPosZ + (player.posZ - player.prevPosZ)*partialTicks;

		List<SimBodySnapshot> bodies =
				SimUniverse.getInstance().getBodySnapshots();
		Collections.sort(bodies, new BodyDistanceComparator(
				playerX, playerY, playerZ, partialTicks));

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();
		try {
			GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_FOG);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glColor4f(1F, 1F, 1F, 1F);
			GL11.glCallList(starGLCallList);
			GL11.glEnable(GL11.GL_TEXTURE_2D);

			Tessellator tessellator = Tessellator.instance;
			int shaderBodyCount = selectShaderBodies(bodies, minecraft,
					playerX, playerY, playerZ, partialTicks);
			boolean analyticAtmosphereSafe =
					AnalyticAtmosphereRenderer
							.isFixedFunctionAtmosphereSafe();
			for(SimBodySnapshot body : bodies) {
				double bodyX = interpolate(body.getPreviousX(), body.getX(),
						partialTicks);
				double bodyY = interpolate(body.getPreviousY(), body.getY(),
						partialTicks);
				double bodyZ = interpolate(body.getPreviousZ(), body.getZ(),
						partialTicks);
				double dx = bodyX - playerX;
				double dy = bodyY - playerY;
				double dz = bodyZ - playerZ;
				double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
				if(distance < 0.001D)
					continue;

				double renderX = dx/distance*PROJECTION_DISTANCE;
				double renderY = dy/distance*PROJECTION_DISTANCE;
				double renderZ = dz/distance*PROJECTION_DISTANCE;
				double renderSize = getRenderSize(body, distance);
				if(renderSize <= 0D)
					continue;
				DimensionProperties planetProperties = null;

				if(body.getBodyType() == SimBodyType.STAR
						|| body.getBodyType()
								== SimBodyType.BLACK_HOLE) {
					StellarBody star = getAdvancedRocketryStar(body.getId());
					if(body.getBodyType() == SimBodyType.BLACK_HOLE
							&& star != null && star.isBlackHole()) {
						BlackHoleRenderManager.INSTANCE.queueBillboard(
								star, renderX, renderY, renderZ, renderSize, 1F);
						continue;
					}
					if(star == null)
						GL11.glColor4f(1F, 1F, 1F, 1F);
					else {
						int color = star.getColorRGB8();
						GL11.glColor4f((color & 0xFF)/255F,
								((color >>> 8) & 0xFF)/255F,
								((color >>> 16) & 0xFF)/255F, 1F);
					}
					minecraft.renderEngine.bindTexture(
							renderSize < 0.08D ? FAR_STAR : TextureResources.locationSunNew);
				}
				else {
					planetProperties = DimensionManager
							.getInstance().getDimensionPropertiesExact(
									body.getDimensionId());
					if(planetProperties == null)
						continue;
					GL11.glColor4f(1F, 1F, 1F, 1F);
					minecraft.renderEngine.bindTexture(
							planetProperties.getPlanetIcon());
				}

				drawBillboard(tessellator, renderX, renderY, renderZ, renderSize);
				if(planetProperties == null)
					continue;

				float projectedRadiusPixels = getProjectedRadiusPixels(
						minecraft, renderSize);
				if(projectedRadiusPixels < 2F)
					continue;

				SimBodySnapshot primaryStar = findPrimaryStarSnapshot(
						bodies, planetProperties.getStarId());
				if(primaryStar == null)
					continue;
				double starX = interpolate(primaryStar.getPreviousX(),
						primaryStar.getX(), partialTicks);
				double starY = interpolate(primaryStar.getPreviousY(),
						primaryStar.getY(), partialTicks);
				double starZ = interpolate(primaryStar.getPreviousZ(),
						primaryStar.getZ(), partialTicks);
				double lightX = starX-bodyX;
				double lightY = starY-bodyY;
				double lightZ = starZ-bodyZ;
				double lightLength = Math.sqrt(lightX*lightX
						+lightY*lightY+lightZ*lightZ);
				if(!finite(lightLength) || lightLength < 0.000001D)
					continue;
				lightX /= lightLength;
				lightY /= lightLength;
				lightZ /= lightLength;

				minecraft.renderEngine.bindTexture(
						DimensionProperties.getShadowResource());
				GL11.glColor4f(1F, 1F, 1F, 0.92F);
				drawBillboardShadow(tessellator, renderX, renderY,
						renderZ, renderSize, lightX, lightY, lightZ);

				if(!planetProperties.hasAtmosphere()
						&& !planetProperties.isGasGiant())
					continue;
				if(!AtmosphereRenderManager.INSTANCE
						.hasPositiveSpaceLight(planetProperties))
					continue;

				boolean shaderCandidate = isShaderBody(body,
						shaderBodyCount);
				if(shaderCandidate) {
					AtmosphereRenderManager.Path path =
							AtmosphereRenderManager.INSTANCE.renderSpace(
									planetProperties, (float)renderSize,
									renderX, renderY, renderZ,
									(float)lightX, (float)lightY,
									(float)lightZ, 1F,
									atmosphereSphereGlList,
									world.getTotalWorldTime(), partialTicks);
					if(path != AtmosphereRenderManager.Path.LEGACY)
						continue;
				}

				if(analyticAtmosphereSafe)
					AnalyticAtmosphereRenderer.renderFreeSpaceHalo(
							tessellator, planetProperties,
							renderX, renderY, renderZ, (float)renderSize,
							projectedRadiusPixels, (float)lightX,
							(float)lightY, (float)lightZ, 1F);
			}
			BlackHoleRenderManager.INSTANCE.renderQueued();
		}
		finally {
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private int selectShaderBodies(List<SimBodySnapshot> bodies,
			Minecraft minecraft, double playerX, double playerY,
			double playerZ, float partialTicks) {
		for(int index = 0; index < shaderBodies.length; index++) {
			shaderBodies[index] = null;
			shaderBodyAreas[index] = 0F;
		}

		int budget = Math.max(0, Math.min(MAX_SHADER_BODY_BUDGET,
				Configuration.atmosphereMaxShaderBodies));
		if(budget == 0)
			return 0;
		float minimumRadius = Math.max(MIN_SHADER_RADIUS_PIXELS,
				Configuration.atmosphereMinShaderRadiusPixels);
		int count = 0;
		for(SimBodySnapshot body : bodies) {
			if(body.getBodyType() == SimBodyType.STAR
					|| body.getBodyType() == SimBodyType.BLACK_HOLE)
				continue;
			DimensionProperties properties = DimensionManager.getInstance()
					.getDimensionPropertiesExact(body.getDimensionId());
			if(properties == null || (!properties.hasAtmosphere()
					&& !properties.isGasGiant()))
				continue;

			double bodyX = interpolate(body.getPreviousX(), body.getX(),
					partialTicks);
			double bodyY = interpolate(body.getPreviousY(), body.getY(),
					partialTicks);
			double bodyZ = interpolate(body.getPreviousZ(), body.getZ(),
					partialTicks);
			double dx = bodyX-playerX;
			double dy = bodyY-playerY;
			double dz = bodyZ-playerZ;
			double distance = Math.sqrt(dx*dx+dy*dy+dz*dz);
			if(!finite(distance) || distance < 0.001D)
				continue;
			double renderSize = getRenderSize(body, distance);
			float projectedRadius = getProjectedRadiusPixels(
					minecraft, renderSize);
			if(projectedRadius < minimumRadius
					|| !hasValidPrimaryLight(bodies, properties,
							bodyX, bodyY, bodyZ, partialTicks))
				continue;
			count = insertShaderBody(body,
					projectedRadius*projectedRadius, count, budget);
		}
		return count;
	}

	private int insertShaderBody(SimBodySnapshot body, float area,
			int count, int budget) {
		if(count < budget) {
			int insertion = count;
			while(insertion > 0
					&& area > shaderBodyAreas[insertion-1]) {
				shaderBodies[insertion] =
						shaderBodies[insertion-1];
				shaderBodyAreas[insertion] =
						shaderBodyAreas[insertion-1];
				insertion--;
			}
			shaderBodies[insertion] = body;
			shaderBodyAreas[insertion] = area;
			return count+1;
		}
		if(area <= shaderBodyAreas[budget-1])
			return count;

		int insertion = budget-1;
		while(insertion > 0
				&& area > shaderBodyAreas[insertion-1]) {
			shaderBodies[insertion] = shaderBodies[insertion-1];
			shaderBodyAreas[insertion] =
					shaderBodyAreas[insertion-1];
			insertion--;
		}
		shaderBodies[insertion] = body;
		shaderBodyAreas[insertion] = area;
		return count;
	}

	private boolean isShaderBody(SimBodySnapshot body, int count) {
		for(int index = 0; index < count; index++)
			if(shaderBodies[index] == body)
				return true;
		return false;
	}

	private static boolean hasValidPrimaryLight(
			List<SimBodySnapshot> bodies, DimensionProperties properties,
			double bodyX, double bodyY, double bodyZ,
			float partialTicks) {
		SimBodySnapshot primaryStar = findPrimaryStarSnapshot(
				bodies, properties.getStarId());
		if(primaryStar == null)
			return false;
		double lightX = interpolate(primaryStar.getPreviousX(),
				primaryStar.getX(), partialTicks)-bodyX;
		double lightY = interpolate(primaryStar.getPreviousY(),
				primaryStar.getY(), partialTicks)-bodyY;
		double lightZ = interpolate(primaryStar.getPreviousZ(),
				primaryStar.getZ(), partialTicks)-bodyZ;
		double length = Math.sqrt(lightX*lightX+lightY*lightY
				+lightZ*lightZ);
		return finite(length) && length >= 0.000001D;
	}

	private static double interpolate(double previous, double current,
			float partialTicks) {
		return previous + (current-previous)*partialTicks;
	}

	private static double getRenderSize(SimBodySnapshot body,
			double distance) {
		if(body == null || !finite(distance) || distance <= 0D
				|| !finite(body.getSize()) || body.getSize() <= 0D)
			return 0D;
		double size = body.getSize()*PROJECTION_DISTANCE/distance;
		return finite(size)
				? Math.max(0.035D, Math.min(12D, size)) : 0D;
	}

	private static StellarBody getAdvancedRocketryStar(String bodyId) {
		int starId = getAdvancedRocketryStarId(bodyId);
		if(starId < 0)
			return null;
		return DimensionManager.getInstance().getStar(starId);
	}

	private static SimBodySnapshot findPrimaryStarSnapshot(
			List<SimBodySnapshot> bodies, int starId) {
		if(starId < 0)
			return null;
		for(SimBodySnapshot candidate : bodies) {
			if((candidate.getBodyType() == SimBodyType.STAR
					|| candidate.getBodyType()
							== SimBodyType.BLACK_HOLE)
					&& getAdvancedRocketryStarId(candidate.getId())
							== starId)
				return candidate;
		}
		return null;
	}

	private static int getAdvancedRocketryStarId(String bodyId) {
		if(bodyId == null || !bodyId.startsWith("star:"))
			return -1;
		try {
			return Integer.parseInt(
					bodyId.substring("star:".length()));
		}
		catch(NumberFormatException exception) {
			return -1;
		}
	}

	private static float getProjectedRadiusPixels(Minecraft minecraft,
			double renderSize) {
		if(minecraft == null || minecraft.displayHeight <= 0
				|| !finite(renderSize) || renderSize <= 0D)
			return 0F;
		double fieldOfView = minecraft.gameSettings == null
				? 70D : minecraft.gameSettings.fovSetting;
		if(!finite(fieldOfView) || fieldOfView <= 1D)
			fieldOfView = 70D;
		double angularRadius = Math.atan(renderSize/PROJECTION_DISTANCE);
		double pixels = angularRadius*minecraft.displayHeight
				/Math.toRadians(fieldOfView);
		return !finite(pixels) || pixels <= 0D
				? 0F : (float)Math.min(Float.MAX_VALUE, pixels);
	}

	private static boolean finite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static void compileUnitSphere() {
		GL11.glBegin(GL11.GL_QUADS);
		for(int latitude = 0;
				latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
			double latitude0 = -Math.PI/2D
					+Math.PI*latitude/SPHERE_LATITUDE_SEGMENTS;
			double latitude1 = -Math.PI/2D
					+Math.PI*(latitude+1)/SPHERE_LATITUDE_SEGMENTS;
			for(int longitude = 0;
					longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
				double longitude0 = -Math.PI
						+2D*Math.PI*longitude
								/SPHERE_LONGITUDE_SEGMENTS;
				double longitude1 = -Math.PI
						+2D*Math.PI*(longitude+1)
								/SPHERE_LONGITUDE_SEGMENTS;
				addSphereVertex(latitude0, longitude0);
				addSphereVertex(latitude1, longitude0);
				addSphereVertex(latitude1, longitude1);
				addSphereVertex(latitude0, longitude1);
			}
		}
		GL11.glEnd();
	}

	private static boolean ensureAtmosphereSphereDisplayList() {
		if(!Display.isCreated())
			return false;
		try {
			ContextCapabilities currentContext =
					GLContext.getCapabilities();
			if(atmosphereSphereContext != currentContext) {
				atmosphereSphereGlList = 0;
				atmosphereSphereContext = currentContext;
				atmosphereSphereFailureLogged = false;
			}
			if(atmosphereSphereGlList > 0
					&& GL11.glIsList(atmosphereSphereGlList))
				return true;
			int displayList = GLAllocation.generateDisplayLists(1);
			if(displayList <= 0)
				throw new IllegalStateException(
						"glGenLists returned no atmosphere display list");
			GL11.glNewList(displayList, GL11.GL_COMPILE);
			compileUnitSphere();
			GL11.glEndList();
			if(!GL11.glIsList(displayList)) {
				GL11.glDeleteLists(displayList, 1);
				throw new IllegalStateException(
						"compiled atmosphere display list is invalid");
			}
			atmosphereSphereGlList = displayList;
			atmosphereSphereFailureLogged = false;
			return true;
		}
		catch(Throwable throwable) {
			atmosphereSphereGlList = 0;
			if(!atmosphereSphereFailureLogged) {
				atmosphereSphereFailureLogged = true;
				AdvancedRocketry.logger.warn(
						"Could not create free-space atmosphere geometry; "
								+ "using the continuous fallback.",
						throwable);
			}
			return false;
		}
	}

	private static void addSphereVertex(double latitude,
			double longitude) {
		double cosLatitude = Math.cos(latitude);
		double x = cosLatitude*Math.cos(longitude);
		double y = Math.sin(latitude);
		double z = cosLatitude*Math.sin(longitude);
		GL11.glNormal3d(x, y, z);
		GL11.glVertex3d(x, y, z);
	}

	private static void drawBillboard(
			Tessellator tessellator, double x, double y, double z, double size) {
		double length = Math.sqrt(x*x + y*y + z*z);
		if(length < 0.001D)
			return;

		double viewX = x/length;
		double viewY = y/length;
		double viewZ = z/length;
		double rightX;
		double rightZ;
		if(Math.abs(viewX) < 0.001D && Math.abs(viewZ) < 0.001D) {
			rightX = 1D;
			rightZ = 0D;
		}
		else {
			double rightLength = Math.sqrt(viewZ*viewZ + viewX*viewX);
			rightX = viewZ/rightLength;
			rightZ = -viewX/rightLength;
		}

		double upX = -rightZ*viewY;
		double upY = rightZ*viewX - rightX*viewZ;
		double upZ = rightX*viewY;
		double rx = rightX*size;
		double rz = rightZ*size;
		double ux = upX*size;
		double uy = upY*size;
		double uz = upZ*size;

		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(x-rx-ux, y-uy, z-rz-uz, 0, 1);
		tessellator.addVertexWithUV(x+rx-ux, y-uy, z+rz-uz, 1, 1);
		tessellator.addVertexWithUV(x+rx+ux, y+uy, z+rz+uz, 1, 0);
		tessellator.addVertexWithUV(x-rx+ux, y+uy, z-rz+uz, 0, 0);
		tessellator.draw();
	}

	private static void drawBillboardShadow(Tessellator tessellator,
			double x, double y, double z, double size,
			double lightX, double lightY, double lightZ) {
		double length = Math.sqrt(x*x+y*y+z*z);
		if(length < 0.001D)
			return;
		double viewX = x/length;
		double viewY = y/length;
		double viewZ = z/length;
		double rightX;
		double rightZ;
		if(Math.abs(viewX) < 0.001D && Math.abs(viewZ) < 0.001D) {
			rightX = 1D;
			rightZ = 0D;
		}
		else {
			double rightLength = Math.sqrt(viewZ*viewZ+viewX*viewX);
			rightX = viewZ/rightLength;
			rightZ = -viewX/rightLength;
		}
		double upX = -rightZ*viewY;
		double upY = rightZ*viewX-rightX*viewZ;
		double upZ = rightX*viewY;

		double projectedRight = lightX*rightX+lightZ*rightZ;
		double projectedUp = lightX*upX+lightY*upY+lightZ*upZ;
		double projectedLength = Math.sqrt(projectedRight*projectedRight
				+projectedUp*projectedUp);
		if(projectedLength > 0.000001D) {
			projectedRight /= projectedLength;
			projectedUp /= projectedLength;
		}
		else {
			projectedRight = 0D;
			projectedUp = 1D;
		}

		double orientedRightX =
				rightX*projectedUp-upX*projectedRight;
		double orientedRightY = -upY*projectedRight;
		double orientedRightZ =
				rightZ*projectedUp-upZ*projectedRight;
		double orientedUpX =
				rightX*projectedRight+upX*projectedUp;
		double orientedUpY = upY*projectedUp;
		double orientedUpZ =
				rightZ*projectedRight+upZ*projectedUp;

		double rx = orientedRightX*size;
		double ry = orientedRightY*size;
		double rz = orientedRightZ*size;
		double ux = orientedUpX*size;
		double uy = orientedUpY*size;
		double uz = orientedUpZ*size;
		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(x-rx-ux, y-ry-uy, z-rz-uz, 0, 1);
		tessellator.addVertexWithUV(x+rx-ux, y+ry-uy, z+rz-uz, 1, 1);
		tessellator.addVertexWithUV(x+rx+ux, y+ry+uy, z+rz+uz, 1, 0);
		tessellator.addVertexWithUV(x-rx+ux, y-ry+uy, z-rz+uz, 0, 0);
		tessellator.draw();
	}

	private static final class BodyDistanceComparator
			implements Comparator<SimBodySnapshot> {
		private final double x;
		private final double y;
		private final double z;
		private final float partialTicks;

		private BodyDistanceComparator(double x, double y, double z,
				float partialTicks) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.partialTicks = partialTicks;
		}

		@Override
		public int compare(SimBodySnapshot first, SimBodySnapshot second) {
			return Double.compare(distanceSquared(second), distanceSquared(first));
		}

		private double distanceSquared(SimBodySnapshot body) {
			double dx = interpolate(body.getPreviousX(), body.getX(),
					partialTicks)-x;
			double dy = interpolate(body.getPreviousY(), body.getY(),
					partialTicks)-y;
			double dz = interpolate(body.getPreviousZ(), body.getZ(),
					partialTicks)-z;
			return dx*dx + dy*dy + dz*dz;
		}
	}
}
