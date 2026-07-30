package zmaster587.advancedRocketry.client.render.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelFormatException;
import net.minecraftforge.client.model.obj.WavefrontObject;
import org.lwjgl.opengl.GL11;
import zmaster587.advancedRocketry.client.render.multiblocks.RendererWarpCore;
import zmaster587.advancedRocketry.client.render.atmosphere.AnalyticAtmosphereRenderer;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.entity.EntityUIPlanet;
import zmaster587.libVulpes.render.RenderHelper;

public class RenderPlanetUIEntity extends Render {

	private static final int ATMOSPHERE_LONGITUDE_SEGMENTS = 96;
	private static final int ATMOSPHERE_LATITUDE_SEGMENTS = 48;
	private static final double VIEW_DIRECTION_EPSILON = 0.000001D;
	private static WavefrontObject sphere;
	public static ResourceLocation planetUIBG = new ResourceLocation("advancedrocketry:textures/gui/planetUIOverlay.png");
	public static ResourceLocation planetUIFG = new ResourceLocation("advancedrocketry:textures/gui/planetUIOverlayFG.png");

	static {
		try {
			sphere = new WavefrontObject(new ResourceLocation("advancedrocketry:models/atmosphere.obj"));
		} catch(ModelFormatException e) {
			sphere = null;
			e.printStackTrace();
		}
	}

	@Override
	public void doRender(Entity entity, double x, double y, double z,
			float entityYaw, float partialTicks) {

		DimensionProperties properties = ((EntityUIPlanet)entity).getProperties();
		if(properties == null || sphere == null)
			return;

		float sizeScale = Math.max(properties.gravitationalMultiplier*properties.gravitationalMultiplier*((EntityUIPlanet)entity).getScale(), .5f);

		GL11.glPushMatrix();
		GL11.glTranslatef((float)x, (float)y + sizeScale*0.03f, (float)z);
		//Max because moon was too small to be visible

		GL11.glScalef(.1f*sizeScale, .1f*sizeScale, .1f*sizeScale);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDepthMask(false);
		Minecraft.getMinecraft().renderEngine.bindTexture(properties.getPlanetIconLEO());
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_SRC_ALPHA, 0, 0);
		GL11.glAlphaFunc(GL11.GL_GREATER, 0);

		GL11.glColor4f(1f, 1, 1f, .5f);

		GL11.glPushMatrix();
		GL11.glRotatef(entity.worldObj.getTotalWorldTime() & 0xFF, 0, 1, 0);
		sphere.renderAll();
		GL11.glPopMatrix();


		//Render shadow
		GL11.glPushMatrix();
		GL11.glScalef(1.1f, 1.1f, 1.1f);
		GL11.glRotatef(90, 0, 0, 1);
		GL11.glRotated( -(properties.orbitTheta * 180/Math.PI), 1, 0, 0);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 0, 0);
		Minecraft.getMinecraft().renderEngine.bindTexture(DimensionProperties.shadow3);
		GL11.glColor4f(.1f, .1f, .1f,0.75f);
		sphere.renderAll();

		Tessellator tess = Tessellator.instance;

		if(properties.hasRings) {
			//Rotate for rings
			GL11.glRotatef(90, 1, 0, 0);
			GL11.glRotatef(-90, 0, 0, 1);
			
			//Draw ring
			GL11.glColor4f(properties.ringColor[0], properties.ringColor[1], properties.ringColor[2],0.5f);
			Minecraft.getMinecraft().renderEngine.bindTexture(DimensionProperties.planetRings);
			tess.startDrawingQuads();
			RenderHelper.renderTopFaceWithUV(tess, 0, -1, -1, 1, 1, 0, 1, 0, 1);
			RenderHelper.renderBottomFaceWithUV(tess, 0, -1, -1, 1, 1, 0, 1, 0, 1);
			tess.draw();

			//Draw ring shadow
			Minecraft.getMinecraft().renderEngine.bindTexture(DimensionProperties.planetRingShadow);
			GL11.glColor4f(1,1,1,0.5f);
			tess.startDrawingQuads();
			RenderHelper.renderTopFaceWithUV(tess, 0, -1, -1, 1, 1, 0, 1, 0, 1);
			RenderHelper.renderBottomFaceWithUV(tess, 0, -1, -1, 1, 1, 0, 1, 0, 1);
			tess.draw();
		}

		GL11.glPopMatrix();

		//Render ATM
		if((properties.hasAtmosphere() || properties.isGasGiant())
				&& AnalyticAtmosphereRenderer
						.isFixedFunctionAtmosphereSafe()) {
			renderAtmosphereShell(tess, properties,
					-x, -(y+sizeScale*0.03F), -z);
		}




		//Render hololines
		GL11.glPushMatrix();
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 0, 0);
		
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		float myTime = ((entity.worldObj.getTotalWorldTime() & 0xF)/16f);

		for(int i = 0; i < 4; i++ ) {
			myTime = ((i*4 + entity.worldObj.getTotalWorldTime() & 0xF)/16f);

			GL11.glColor4f(0, 1f, 1f, .2f*(1-myTime));
			tess.startDrawingQuads();
			RenderHelper.renderTopFace(tess, myTime - 0.5, -.5f, -.5f, .5f, .5f);
			RenderHelper.renderBottomFace(tess, myTime - 0.5, -.5f, -.5f, .5f, .5f);
			tess.draw();
		}
		
		GL11.glAlphaFunc(GL11.GL_GREATER, .1f);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();

		GL11.glDepthMask(true);

		//RenderSelection
		if(((EntityUIPlanet)entity).isSelected()) {
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			double speedRotate = 0.025d;
			GL11.glColor4f(0.4f, 0.4f, 1f, 0.6f);
			GL11.glTranslated(0, -1.25, 0);
			GL11.glPushMatrix();
			GL11.glRotated(speedRotate*System.currentTimeMillis() % 360, 0f, 1f, 0f);
			RendererWarpCore.model.renderOnly("Rotate1");
			GL11.glPopMatrix();

			GL11.glPushMatrix();
			GL11.glRotated(180 + speedRotate*System.currentTimeMillis() % 360, 0f, 1f, 0f);
			RendererWarpCore.model.renderOnly("Rotate1");
			GL11.glPopMatrix();
			GL11.glEnable(GL11.GL_TEXTURE_2D);
		}

		GL11.glPopMatrix();

		MovingObjectPosition hitObj = Minecraft.getMinecraft().objectMouseOver;
		if(hitObj != null && hitObj.entityHit == entity) {

			GL11.glPushMatrix();
			GL11.glColor3f(1, 1, 1);
			GL11.glTranslated(x, y + sizeScale*0.03f, z);
			sizeScale = .1f*sizeScale;
			GL11.glScaled(sizeScale,sizeScale,sizeScale);

			//Render atmosphere UI/planet info

			//GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_DEPTH_TEST);

			RenderHelper.setupPlayerFacingMatrix(Minecraft.getMinecraft().thePlayer.getDistanceSq(hitObj.hitVec.xCoord, hitObj.hitVec.yCoord, hitObj.hitVec.zCoord), 0, 0, 0);
			

			//Draw Mass indicator
			Minecraft.getMinecraft().renderEngine.bindTexture(planetUIFG);
			GL11.glColor4f(1, 1, 1,0.8f);
			renderMassIndicator(tess, properties.gravitationalMultiplier/2f);

			//Draw background
			GL11.glColor4f(1, 1, 1,1);
			Minecraft.getMinecraft().renderEngine.bindTexture(planetUIBG);
			tess.startDrawingQuads();
			RenderHelper.renderNorthFaceWithUV(tess, 1, -40, -25, 40, 55, 1, 0, 1, 0);
			tess.draw();


			//Render ATM
			Minecraft.getMinecraft().renderEngine.bindTexture(planetUIFG);
			renderATMIndicator(tess, properties.getAtmosphereDensity()/200f);
			//Render Temp
			renderTemperatureIndicator(tess, properties.averageTemperature/200f);

			//Render planet name
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			//GL11.glDepthMask(true);
			RenderHelper.cleanupPlayerFacingMatrix();
			RenderHelper.renderTag(Minecraft.getMinecraft().thePlayer.getDistanceSq(hitObj.hitVec.xCoord, hitObj.hitVec.yCoord, hitObj.hitVec.zCoord), properties.getName(), 0, .9, 0, 5);
			RenderHelper.renderTag(Minecraft.getMinecraft().thePlayer.getDistanceSq(hitObj.hitVec.xCoord, hitObj.hitVec.yCoord, hitObj.hitVec.zCoord), "NumMoons: " + properties.getChildPlanets().size(), 0, .6, 0, 5);

			GL11.glPopMatrix();
		}

		//Clean up and make player not transparent
		GL11.glColor3f(1, 1, 1);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LIGHTING);
		OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 0, 0);
	}

	private static void renderAtmosphereShell(
			Tessellator tessellator, DimensionProperties properties,
			double viewX, double viewY, double viewZ) {
		double viewLength = Math.sqrt(
				viewX*viewX+viewY*viewY+viewZ*viewZ);
		if(!finite(viewLength)
				|| viewLength <= VIEW_DIRECTION_EPSILON)
			return;
		viewX /= viewLength;
		viewY /= viewLength;
		viewZ /= viewLength;

		float pressure = AnalyticAtmosphereRenderer.visualPressure(
				properties, properties.isGasGiant());
		if(!finite(pressure))
			pressure = 1F;
		AtmosphereVisualProperties.Snapshot overrides =
				properties.getAtmosphereVisualPropertiesSnapshot();
		if(overrides.hasSunIntensityMultiplier()
				&& overrides.getSunIntensityMultiplier() <= 0D)
			return;
		float red = overrides.hasRayleighColor()
				? sanitizeColor(overrides.getRayleighColorComponent(0),
						0.45F)
				: colorComponent(properties.skyColor, 0, 0.45F);
		float green = overrides.hasRayleighColor()
				? sanitizeColor(overrides.getRayleighColorComponent(1),
						0.65F)
				: colorComponent(properties.skyColor, 1, 0.65F);
		float blue = overrides.hasRayleighColor()
				? sanitizeColor(overrides.getRayleighColorComponent(2),
						1F)
				: colorComponent(properties.skyColor, 2, 1F);
		float maximumAlpha = Math.min(
				properties.isGasGiant() ? 0.42F : 0.34F,
				0.08F+0.05F*pressure);
		double planetRadiusKm = overrides.hasPlanetRadiusKm()
				? overrides.getPlanetRadiusKm() : 6360D;
		double atmosphereHeightKm = overrides.hasAtmosphereHeightKm()
				? overrides.getAtmosphereHeightKm()
				: properties.isGasGiant() ? 200D : 100D;
		float shellRatio = (float)Math.max(0.02D, Math.min(0.15D,
				atmosphereHeightKm/planetRadiusKm));
		float shellRadius = 1F+shellRatio;

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();
		boolean clientAttributesPushed = false;
		try {
			GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
			clientAttributesPushed = true;
			GL11.glScalef(
					shellRadius, shellRadius, shellRadius);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glCullFace(GL11.GL_BACK);
			GL11.glFrontFace(GL11.GL_CCW);
			GL11.glEnable(GL11.GL_BLEND);
			OpenGlHelper.glBlendFunc(
					GL11.GL_SRC_ALPHA,
					GL11.GL_ONE_MINUS_SRC_ALPHA, 0, 0);
			GL11.glShadeModel(GL11.GL_SMOOTH);

			tessellator.startDrawing(GL11.GL_QUADS);
			for(int latitude = 0;
					latitude < ATMOSPHERE_LATITUDE_SEGMENTS;
					latitude++) {
				double latitude0 = -Math.PI*0.5D
						+Math.PI*latitude
								/ATMOSPHERE_LATITUDE_SEGMENTS;
				double latitude1 = -Math.PI*0.5D
						+Math.PI*(latitude+1)
								/ATMOSPHERE_LATITUDE_SEGMENTS;
				for(int longitude = 0;
						longitude
								< ATMOSPHERE_LONGITUDE_SEGMENTS;
						longitude++) {
					double longitude0 = Math.PI*2D*longitude
							/ATMOSPHERE_LONGITUDE_SEGMENTS;
					double longitude1 = Math.PI*2D*(longitude+1)
							/ATMOSPHERE_LONGITUDE_SEGMENTS;
					addAtmosphereVertex(
							tessellator, latitude0, longitude0,
							viewX, viewY, viewZ,
							red, green, blue, maximumAlpha);
					addAtmosphereVertex(
							tessellator, latitude1, longitude0,
							viewX, viewY, viewZ,
							red, green, blue, maximumAlpha);
					addAtmosphereVertex(
							tessellator, latitude1, longitude1,
							viewX, viewY, viewZ,
							red, green, blue, maximumAlpha);
					addAtmosphereVertex(
							tessellator, latitude0, longitude1,
							viewX, viewY, viewZ,
							red, green, blue, maximumAlpha);
				}
			}
			tessellator.draw();
		}
		finally {
			if(clientAttributesPushed)
				GL11.glPopClientAttrib();
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private static void addAtmosphereVertex(
			Tessellator tessellator,
			double latitude, double longitude,
			double viewX, double viewY, double viewZ,
			float red, float green, float blue,
			float maximumAlpha) {
		double latitudeRadius = Math.cos(latitude);
		double normalX = latitudeRadius*Math.cos(longitude);
		double normalY = Math.sin(latitude);
		double normalZ = latitudeRadius*Math.sin(longitude);
		double viewDot = normalX*viewX
				+normalY*viewY+normalZ*viewZ;
		double limb = Math.max(
				0D, Math.min(1D, 1D-Math.abs(viewDot)));
		double outerFade = smoothstep(
				0D, 0.12D, Math.abs(viewDot));
		float alpha = (float)(maximumAlpha
				*Math.pow(limb, 1.35D)*outerFade);
		tessellator.setColorRGBA_F(
				red, green, blue, alpha);
		tessellator.addVertex(normalX, normalY, normalZ);
	}

	private static float colorComponent(float[] color, int index,
			float fallback) {
		if(color == null || color.length <= index
				|| Float.isNaN(color[index])
				|| Float.isInfinite(color[index]))
			return fallback;
		return Math.max(0F, Math.min(4F, color[index]));
	}

	private static float sanitizeColor(float value, float fallback) {
		if(Float.isNaN(value) || Float.isInfinite(value))
			return fallback;
		return Math.max(0F, Math.min(4F, value));
	}

	private static double smoothstep(
			double edge0, double edge1, double value) {
		double amount = Math.max(0D, Math.min(
				1D, (value-edge0)/(edge1-edge0)));
		return amount*amount*(3D-2D*amount);
	}

	private static boolean finite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static boolean finite(float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	protected void renderMassIndicator(Tessellator tess, float percent) {
		tess.startDrawingQuads();

		float maxUV = (1-percent)*0.5f;

		RenderHelper.renderNorthFaceWithUV(tess, 0, -20, -5 + 41*(1-percent), 20, 36, .5f, 0f, .5, maxUV);
		tess.draw();
	}

	protected void renderATMIndicator(Tessellator tess, float percent) {
		tess.startDrawingQuads();

		float maxUV = (1-percent)*0.406f + .578f;
		//Offset by 15 for Y
		RenderHelper.renderNorthFaceWithUV(tess, 0, 6, 20 + (1-percent)*33, 39, 53, .5624f, .984f, .984f, maxUV);
		tess.draw();
	}

	protected void renderTemperatureIndicator(Tessellator tess, float percent) {
		tess.startDrawingQuads();

		float maxUV = (1-percent)*0.406f + .578f;
		//Offset by 15 for Y
		RenderHelper.renderNorthFaceWithUV(tess, 0, -38, 21.4f + (1-percent)*33, -4, 53, .016f, .4376f, .984f, maxUV);
		tess.draw();
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity p_110775_1_) {
		return null;
	}
}
