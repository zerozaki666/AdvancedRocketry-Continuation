package zmaster587.advancedRocketry.client.render.planet;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
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
	private static final ResourceLocation FAR_STAR =
			new ResourceLocation("advancedrocketry:textures/env/sunLodFar.png");

	@Override
	public void render(float partialTicks, WorldClient world, Minecraft minecraft) {
		EntityPlayer player = minecraft.thePlayer;
		if(player == null)
			return;

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
				double renderSize = Math.max(0.035D, Math.min(
						12D, body.getSize()*PROJECTION_DISTANCE/distance));

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
					float[] color = star == null
							? new float[] {1F, 1F, 1F}
							: star.getColor();
					GL11.glColor4f(color[0], color[1], color[2], 1F);
					minecraft.renderEngine.bindTexture(
							renderSize < 0.08D ? FAR_STAR : TextureResources.locationSunNew);
				}
				else {
					DimensionProperties properties = DimensionManager
							.getInstance().getDimensionPropertiesExact(
									body.getDimensionId());
					if(properties == null)
						continue;
					GL11.glColor4f(1F, 1F, 1F, 1F);
					minecraft.renderEngine.bindTexture(properties.getPlanetIcon());
				}

				drawBillboard(tessellator, renderX, renderY, renderZ, renderSize);
			}
			BlackHoleRenderManager.INSTANCE.renderQueued();
		}
		finally {
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private static double interpolate(double previous, double current,
			float partialTicks) {
		return previous + (current-previous)*partialTicks;
	}

	private static StellarBody getAdvancedRocketryStar(String bodyId) {
		if(bodyId == null || !bodyId.startsWith("star:"))
			return null;
		try {
			return DimensionManager.getInstance().getStar(
					Integer.parseInt(bodyId.substring("star:".length())));
		}
		catch(NumberFormatException exception) {
			return null;
		}
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
