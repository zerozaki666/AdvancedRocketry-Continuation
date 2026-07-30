package zmaster587.advancedRocketry.client.render.planet;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.lwjgl.opengl.GL11;

import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.IPlanetaryProvider;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.client.render.atmosphere.AnalyticAtmosphereRenderer;
import zmaster587.advancedRocketry.client.render.atmosphere.AtmosphereRenderManager;
import zmaster587.advancedRocketry.client.render.blackhole.BlackHoleRenderManager;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.event.RocketEventHandler;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.stations.SpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.util.AstronomicalBodyHelper;
import zmaster587.libVulpes.util.Vector3F;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.common.util.ForgeDirection;

public class RenderPlanetarySky extends IRenderHandler {


	protected int starGLCallList;
	private int glSkyList;
	private int glSkyList2;
	float celestialAngle;
	Vector3F<Float> axis;
	private final float[] primaryLightDirection = new float[3];
	private final float[] bodyLocalLightDirection = new float[3];

	//Mostly vanilla code
	//TODO: make usable on other planets
	public RenderPlanetarySky() {
		axis = new Vector3F<Float>(1f, 0f, 0f);

		this.starGLCallList = GLAllocation.generateDisplayLists(3);
		GL11.glPushMatrix();
		GL11.glNewList(this.starGLCallList, GL11.GL_COMPILE);
		this.renderStars();
		GL11.glEndList();
		GL11.glPopMatrix();
		Tessellator tessellator = Tessellator.instance;
		this.glSkyList = this.starGLCallList + 1;
		GL11.glNewList(this.glSkyList, GL11.GL_COMPILE);
		byte b2 = 64;
		int i = 256 / b2 + 2;
		float f = 16.0F;
		int j;
		int k;

		for (j = -b2 * i; j <= b2 * i; j += b2)
		{
			for (k = -b2 * i; k <= b2 * i; k += b2)
			{
				tessellator.startDrawingQuads();
				tessellator.addVertex((double)(j + 0), (double)f, (double)(k + 0));
				tessellator.addVertex((double)(j + b2), (double)f, (double)(k + 0));
				tessellator.addVertex((double)(j + b2), (double)f, (double)(k + b2));
				tessellator.addVertex((double)(j + 0), (double)f, (double)(k + b2));
				tessellator.draw();
			}
		}

		GL11.glEndList();
		this.glSkyList2 = this.starGLCallList + 2;
		GL11.glNewList(this.glSkyList2, GL11.GL_COMPILE);
		f = -16.0F;
		tessellator.startDrawingQuads();

		for (j = -b2 * i; j <= b2 * i; j += b2)
		{
			for (k = -b2 * i; k <= b2 * i; k += b2)
			{
				tessellator.addVertex((double)(j + b2), (double)f, (double)(k + 0));
				tessellator.addVertex((double)(j + 0), (double)f, (double)(k + 0));
				tessellator.addVertex((double)(j + 0), (double)f, (double)(k + b2));
				tessellator.addVertex((double)(j + b2), (double)f, (double)(k + b2));
			}
		}

		tessellator.draw();
		GL11.glEndList();
	}

	Minecraft mc = Minecraft.getMinecraft();

	private void renderStars()
	{
		Random random = new Random(10842L);
		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();

		for (int i = 0; i < 2000; ++i)
		{
			double d0 = (double)(random.nextFloat() * 2.0F - 1.0F);
			double d1 = (double)(random.nextFloat() * 2.0F - 1.0F);
			double d2 = (double)(random.nextFloat() * 2.0F - 1.0F);
			double d3 = (double)(0.15F + random.nextFloat() * 0.1F);
			double d4 = d0 * d0 + d1 * d1 + d2 * d2;

			if (d4 < 1.0D && d4 > 0.01D)
			{
				d4 = 1.0D / Math.sqrt(d4);
				d0 *= d4;
				d1 *= d4;
				d2 *= d4;
				double d5 = d0 * 100.0D;
				double d6 = d1 * 100.0D;
				double d7 = d2 * 100.0D;
				double d8 = Math.atan2(d0, d2);
				double d9 = Math.sin(d8);
				double d10 = Math.cos(d8);
				double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
				double d12 = Math.sin(d11);
				double d13 = Math.cos(d11);
				double d14 = random.nextDouble() * Math.PI * 2.0D;
				double d15 = Math.sin(d14);
				double d16 = Math.cos(d14);

				for (int j = 0; j < 4; ++j)
				{
					double d17 = 0.0D;
					double d18 = (double)((j & 2) - 1) * d3;
					double d19 = (double)((j + 1 & 2) - 1) * d3;
					double d20 = d18 * d16 - d19 * d15;
					double d21 = d19 * d16 + d18 * d15;
					double d22 = d20 * d12 + d17 * d13;
					double d23 = d17 * d12 - d20 * d13;
					double d24 = d23 * d9 - d21 * d10;
					double d25 = d21 * d9 + d23 * d10;
					tessellator.addVertex(d5 + d24, d6 + d22, d7 + d25);
				}
			}
		}

		tessellator.draw();
	}

	@Override
	public void render(float partialTicks, WorldClient world, Minecraft mc) {

		//TODO: properly handle this
		float atmosphere;
		int solarOrbitalDistance, planetOrbitalDistance = 0;
		double myPhi = 0, myTheta = 0, myPrevOrbitalTheta = 0, myRotationalPhi = 0;;
		boolean hasAtmosphere = false, isMoon;
		float parentAtmColor[] = new float[]{1f,1f,1f};
		float ringColor[] = {0f,0f,0f};
		float parentRingColor[] = {0f,0f,0f};
		float sunSize = 1.0f;
		float starSeperation = 0f;
		boolean isWarp = false;
		boolean isGasGiant = false;
		boolean hasRings = false;
		boolean parentHasRings = false;
		ForgeDirection travelDirection = null;
		ResourceLocation parentPlanetIcon = null;
		List<DimensionProperties> children;
		List<StellarBody> subStars = new LinkedList<StellarBody>();
		StellarBody primaryStar = null;
		DimensionProperties currentProperties = null;
		DimensionProperties parentPropertiesForRender = null;
		celestialAngle = mc.theWorld.getCelestialAngle(partialTicks);
		BlackHoleRenderManager.INSTANCE.beginFrame(
				world.getTotalWorldTime(), partialTicks);

		Vec3 sunColor;
		if(mc.theWorld.provider instanceof IPlanetaryProvider) {
			IPlanetaryProvider planetaryProvider = (IPlanetaryProvider)mc.theWorld.provider;

			DimensionProperties properties = (DimensionProperties)planetaryProvider.getDimensionProperties((int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);
			currentProperties = properties;

			atmosphere = planetaryProvider.getAtmosphereDensityFromHeight(mc.renderViewEntity.posY, (int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);

			ForgeDirection dir = getRotationAxis(properties, (int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);
			axis.x = (float) dir.offsetX;
			axis.y = (float) dir.offsetY;
			axis.z = (float) dir.offsetZ;

			myPhi = properties.orbitalPhi;
			myTheta = properties.orbitTheta;
			myRotationalPhi = properties.rotationalPhi;
			myPrevOrbitalTheta = properties.prevOrbitalTheta;
			hasRings = properties.hasRings();
			ringColor = properties.ringColor;

			children = new LinkedList<DimensionProperties>();
			for (Integer i : properties.getChildPlanets()) {
				children.add(DimensionManager.getInstance().getDimensionProperties(i));
			}

			solarOrbitalDistance = properties.getSolarOrbitalDistance();


			if(isMoon = properties.isMoon()) {
				DimensionProperties parentProperties = properties.getParentProperties();
				parentPropertiesForRender = parentProperties;
				isGasGiant = parentProperties.isGasGiant();
				hasAtmosphere = parentProperties.hasAtmosphere();
				planetOrbitalDistance = properties.getParentOrbitalDistance();
				parentAtmColor = parentProperties.skyColor;
				parentPlanetIcon = getTextureForPlanet(parentProperties);
				parentHasRings = parentProperties.hasRings;
				parentRingColor = parentProperties.ringColor;

			}

			sunColor = planetaryProvider.getSunColor((int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);
			primaryStar = properties.getStar();
			if(primaryStar != null) {
				sunSize = primaryStar.getSize();
				subStars = primaryStar.getSubStars();
				starSeperation = primaryStar.getStarSeperation();
			}
			else {
				// A station may intentionally preserve an invalid synthetic
				// target for diagnosis.  Render an empty sky until that target
				// is repaired instead of dereferencing a missing star.
				sunSize = 0F;
			}
			if(world.provider.dimensionId == Configuration.spaceDimId) {
				isWarp = properties.getParentPlanet() == SpaceObjectManager.WARPDIMID;
				if(isWarp) {
					SpaceObject station = (SpaceObject) SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords((int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);
					travelDirection = station.getForwardDirection();
				}
			}
		}
		else if(DimensionManager.getInstance().isDimensionCreated(mc.theWorld.provider.dimensionId)) {

			DimensionProperties properties = DimensionManager.getInstance().getDimensionProperties(mc.theWorld.provider.dimensionId);
			currentProperties = properties;

			atmosphere = properties.getAtmosphereDensityAtHeight(mc.renderViewEntity.posY);//planetaryProvider.getAtmosphereDensityFromHeight(mc.getRenderViewEntity().posY, mc.player.getPosition());
			ForgeDirection dir = getRotationAxis(properties, (int)mc.thePlayer.posX, (int)mc.thePlayer.posZ);
			axis.x = (float) dir.offsetX;
			axis.y = (float) dir.offsetY;
			axis.z = (float) dir.offsetZ;

			myPhi = properties.orbitalPhi;
			myTheta = properties.orbitTheta;
			myRotationalPhi = properties.rotationalPhi;
			myPrevOrbitalTheta = properties.prevOrbitalTheta;
			hasRings = properties.hasRings();
			ringColor = properties.ringColor;

			children = new LinkedList<DimensionProperties>();
			for (Integer i : properties.getChildPlanets()) {
				children.add(DimensionManager.getInstance().getDimensionProperties(i));
			}

			solarOrbitalDistance = properties.getSolarOrbitalDistance();


			if(isMoon = properties.isMoon()) {
				DimensionProperties parentProperties = properties.getParentProperties();
				parentPropertiesForRender = parentProperties;
				isGasGiant = parentProperties.isGasGiant();
				hasAtmosphere = parentProperties.hasAtmosphere();
				planetOrbitalDistance = properties.getParentOrbitalDistance();
				parentAtmColor = parentProperties.skyColor;
				parentPlanetIcon = getTextureForPlanet(parentProperties);
				parentHasRings = parentProperties.hasRings;
				parentRingColor = parentProperties.ringColor;
			}

			float sunColorFloat[] = properties.getSunColor();
			
			sunColor = Vec3.createVectorHelper(sunColorFloat[0], sunColorFloat[1], sunColorFloat[2]);//planetaryProvider.getSunColor(mc.player.getPosition());
			primaryStar = properties.getStar();
			if(primaryStar != null) {
				sunSize = primaryStar.getSize();
				subStars = primaryStar.getSubStars();
				starSeperation = primaryStar.getStarSeperation();
			}
			else
				sunSize = 0F;
		}
		else {
			currentProperties = DimensionManager.overworldProperties;
			children = new LinkedList<DimensionProperties>();
			isMoon = false;
			hasAtmosphere = DimensionManager.overworldProperties.hasAtmosphere();
			atmosphere = DimensionManager.overworldProperties.getAtmosphereDensityAtHeight(mc.renderViewEntity.posY);
			solarOrbitalDistance = DimensionManager.overworldProperties.orbitalDist;
			sunColor = Vec3.createVectorHelper(1, 1, 1);
		}

		atmosphere = sanitizeAtmosphereDensity(atmosphere);
		float rainStrength = clamp01(mc.theWorld.getRainStrength(partialTicks));
		boolean primaryLightDirectionValid = resolvePrimaryLightDirection(
				myRotationalPhi, primaryLightDirection);
		AtmosphereRenderManager.Path atmospherePath =
				AtmosphereRenderManager.INSTANCE.renderSurface(
						currentProperties, mc.renderViewEntity.posY,
						primaryLightDirection[0], primaryLightDirection[1],
						primaryLightDirection[2],
						rainStrength, world.getTotalWorldTime(), partialTicks);
		boolean physicalAtmosphere =
				atmospherePath.renderedPhysicalSky();
		boolean suppressLegacyAtmosphere =
				atmospherePath.suppressesLegacyAtmosphere();
		float legacyAtmosphere = suppressLegacyAtmosphere
				? 0F : clamp01(atmosphere);
		float atmosphereLightElevation = primaryLightDirectionValid
				? primaryLightDirection[1] : 0F;

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		Vec3 vec3 = Minecraft.getMinecraft().theWorld.getSkyColor(this.mc.renderViewEntity, partialTicks);
		float f1 = (float)vec3.xCoord;
		float f2 = (float)vec3.yCoord;
		float f3 = (float)vec3.zCoord;
		float f6;

		if (this.mc.gameSettings.anaglyph)
		{
			float f4 = (f1 * 30.0F + f2 * 59.0F + f3 * 11.0F) / 100.0F;
			float f5 = (f1 * 30.0F + f2 * 70.0F) / 100.0F;
			f6 = (f1 * 30.0F + f3 * 70.0F) / 100.0F;
			f1 = f4;
			f2 = f5;
			f3 = f6;
		}

		//Simulate atmospheric thickness
		f1 *= legacyAtmosphere;
		f2 *= legacyAtmosphere;
		f3 *= legacyAtmosphere;

		GL11.glColor3f(f1, f2, f3);
		Tessellator tessellator1 = Tessellator.instance;
		GL11.glDepthMask(false);

		if(!physicalAtmosphere
				&& atmospherePath
						!= AtmosphereRenderManager.Path.EXTERNAL_SAFE_SKIP) {
			GL11.glEnable(GL11.GL_FOG);
			GL11.glColor3f(f1, f2, f3);
			GL11.glCallList(this.glSkyList);
			GL11.glDisable(GL11.GL_FOG);
		}
		else {
			GL11.glDisable(GL11.GL_FOG);
		}
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);
		RenderHelper.disableStandardItemLighting();
		float[] afloat = mc.theWorld.provider.calcSunriseSunsetColors(celestialAngle, partialTicks);
		float f7;
		float f8;
		float f9;
		float f10;

		if (afloat != null && !suppressLegacyAtmosphere)
		{
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glShadeModel(GL11.GL_SMOOTH);
			GL11.glPushMatrix();
			GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
			GL11.glRotatef(MathHelper.sin(mc.theWorld.getCelestialAngleRadians(partialTicks)) < 0.0F ? 180.0F : 0.0F, 0.0F, 0.0F, 1.0F);
			GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);

			//Sim atmospheric thickness
			f6 = afloat[0];
			f7 = afloat[1];
			f8 = afloat[2];
			float f11;

			if (this.mc.gameSettings.anaglyph)
			{
				f9 = (f6 * 30.0F + f7 * 59.0F + f8 * 11.0F) / 100.0F;
				f10 = (f6 * 30.0F + f7 * 70.0F) / 100.0F;
				f11 = (f6 * 30.0F + f8 * 70.0F) / 100.0F;
				f6 = f9;
				f7 = f10;
				f8 = f11;
			}

			tessellator1.startDrawing(6);
			tessellator1.setColorRGBA_F(f6, f7, f8,
					afloat[3] * legacyAtmosphere);
			tessellator1.addVertex(0.0D, 100.0D, 0.0D);
			byte b0 = 16;
			tessellator1.setColorRGBA_F(afloat[0], afloat[1], afloat[2], 0.0F);

			for (int j = 0; j <= b0; ++j)
			{
				f11 = (float)j * (float)Math.PI * 2.0F / (float)b0;
				float f12 = MathHelper.sin(f11);
				float f13 = MathHelper.cos(f11);
				tessellator1.addVertex((double)(f12 * 120.0F), (double)(f13 * 120.0F), (double)(-f13 * 40.0F * afloat[3]));
			}

			tessellator1.draw();
			GL11.glPopMatrix();
			GL11.glShadeModel(GL11.GL_FLAT);
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		OpenGlHelper.glBlendFunc(770, 1, 1, 0);
		GL11.glPushMatrix();


		f6 = 1F-rainStrength;

		f7 = 0.0F;
		f8 = 0.0F;
		f9 = 0.0F;
		GL11.glColor4f(1.0F, 1.0F, 1.0F, f6);
		GL11.glTranslatef(f7, f8, f9);
		GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);

		GL11.glRotatef((float)myRotationalPhi, 0f, 1f, 0f);
		
		float multiplier;
		if(suppressLegacyAtmosphere)
			multiplier = 1F-rainStrength;
		else
			multiplier = clamp01((2F-atmosphere)/2F)
					*(1F-rainStrength);
		float primarySunMultiplier = multiplier;
		if(physicalAtmosphere)
			primarySunMultiplier *=
					AtmosphereRenderManager.INSTANCE.getSunVisibility(
							currentProperties, mc.renderViewEntity.posY,
							atmosphereLightElevation);


		//Draw Rings
		if(hasRings) {
			GL11.glPushMatrix();
			GL11.glRotatef(90f, 0f, 1f, 0f);

			f10 = 100;
			double ringDist = 0;
			mc.renderEngine.bindTexture(DimensionProperties.planetRings);

			GL11.glRotated(70, 1, 0, 0);
			GL11.glTranslated(0, -10, 50);
			GL11.glColor4f(ringColor[0], ringColor[1], ringColor[2],multiplier);
			tessellator1.startDrawing(GL11.GL_QUADS);
			tessellator1.addVertexWithUV((double)f10, ringDist, (double)(-f10),1.0D, 0.0D);
			tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)(-f10), 0.0D, 0.0D);
			tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)f10, 0.0D, 1.0D);
			tessellator1.addVertexWithUV((double)f10, ringDist, (double)f10, 1.0D, 1.0D);
			tessellator1.draw();
			GL11.glPopMatrix();

			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glPushMatrix();

			GL11.glRotatef(90f, 0f, 1f, 0f);
			GL11.glRotated(70, 1, 0, 0);
			GL11.glTranslated(0, -10, 50);
			
			mc.renderEngine.bindTexture(DimensionProperties.planetRingShadow);
			GL11.glColor4f(0f, 0f, 0f,1);
			tessellator1.startDrawing(GL11.GL_QUADS);
			tessellator1.addVertexWithUV((double)f10, ringDist, (double)(-f10),1.0D, 0.0D);
			tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)(-f10), 0.0D, 0.0D);
			tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)f10, 0.0D, 1.0D);
			tessellator1.addVertexWithUV((double)f10, ringDist, (double)f10, 1.0D, 1.0D);
			tessellator1.draw();
			GL11.glPopMatrix();

			OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
		}
		if(!isWarp)
			rotateAroundAxis();
		
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		float f18;
		if(physicalAtmosphere) {
			f18 = AtmosphereRenderManager.INSTANCE.getStarVisibility(
					currentProperties, mc.renderViewEntity.posY,
					atmosphereLightElevation);
		}
		else if(atmospherePath == AtmosphereRenderManager.Path.OFF) {
			f18 = 1F;
		}
		else {
			float normalizedAtmosphere = clamp01(atmosphere);
			f18 = (1F-normalizedAtmosphere)
					+normalizedAtmosphere
							*clamp01(mc.theWorld.getStarBrightness(
									partialTicks));
		}
		f18 = clamp01(f18*(1F-rainStrength));

		if (f18 > 0.0F)
		{
			setStarFieldColor(f18, physicalAtmosphere);
			GL11.glPushMatrix();
			if(isWarp) {
				for(int i = -3; i < 5; i++) {
					GL11.glPushMatrix();
					double magnitude = i*-100 + (((System.currentTimeMillis()) + 50) % 2000)/20f;
					GL11.glTranslated(-travelDirection.offsetZ*magnitude, 0, travelDirection.offsetX*magnitude);
					GL11.glCallList(this.starGLCallList);
					GL11.glPopMatrix();
				}
				//GL11.glTranslated(((System.currentTimeMillis()/10) + 50) % 100, 0, 0);
			}
			else {
				GL11.glCallList(this.starGLCallList);
				// The compatibility path keeps the old extra star fields, but
				// the physical paths use one continuously attenuated field.
				if(!suppressLegacyAtmosphere && atmosphere < 0.5F) {
					GL11.glColor4f(f18, f18, f18, f18/2f);
					GL11.glPushMatrix();
					GL11.glRotatef(-90, 0, 1, 0);
					GL11.glCallList(this.starGLCallList);
					GL11.glPopMatrix();
				}
				if(!suppressLegacyAtmosphere && atmosphere < 0.25F) {
					GL11.glColor4f(f18, f18, f18, f18/4f);
					GL11.glPushMatrix();
					GL11.glRotatef(90, 0, 1, 0);
					GL11.glCallList(this.starGLCallList);
					GL11.glPopMatrix();
				}
				setStarFieldColor(f18, physicalAtmosphere);
			}
			GL11.glPopMatrix();
		}
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		mc.renderEngine.bindTexture(TextureResources.locationSunPng);

		//--------------------------- Draw the suns --------------------
		if(!isWarp) {
			//Set sun color and distance
			drawStarOrBlackHole(tessellator1, primaryStar, solarOrbitalDistance,
					sunSize, (float)sunColor.xCoord, (float)sunColor.yCoord,
					(float)sunColor.zCoord, primarySunMultiplier,
					physicalAtmosphere);

			if(subStars != null && !subStars.isEmpty()) {
				GL11.glPushMatrix();
				float phaseInc = 360f/subStars.size();
				
				for(StellarBody subStar : subStars) {
					GL11.glRotatef(phaseInc, 0, 1, 0);
					GL11.glPushMatrix();
					
					GL11.glRotatef(subStar.getStarSeperation()*AstronomicalBodyHelper.getBodySizeMultiplier(solarOrbitalDistance), 1, 0, 0);
					float color[] = subStar.getColor();
					drawStarOrBlackHole(tessellator1, subStar,
							solarOrbitalDistance, subStar.getSize(), color[0],
							color[1], color[2], physicalAtmosphere
									? primarySunMultiplier : multiplier,
							physicalAtmosphere);
					GL11.glPopMatrix();
				}
				GL11.glPopMatrix();
			}
		}
		f10 = 20.0F;


		if(isMoon) {
			GL11.glPushMatrix();

			GL11.glRotatef((float)myPhi, 0f, 0f, 1f);
			double parentRenderTheta = partialTicks*myTheta
					+(1-partialTicks)*myPrevOrbitalTheta;
			GL11.glRotatef((float)(parentRenderTheta*180F/Math.PI),
					1f, 0f, 0f);

			float phiAngle = (float)((myPhi) * Math.PI/180f);

			//Close enough approximation, I missed something but seems to off by no more than 30*
			//Nobody will look
			double x = MathHelper.sin(phiAngle)*MathHelper.cos((float)myTheta);
			double y = -MathHelper.sin((float)myTheta);
			double rotation = -Math.PI/2f + Math.atan2(x, y) - (myTheta - Math.PI )*MathHelper.sin(phiAngle);

			//Draw Rings
			if(parentHasRings) {
				GL11.glPushMatrix();
				GL11.glRotatef(90f, 0f, 1f, 0f);

				f10 = 100;
				double ringDist = 0;
				mc.renderEngine.bindTexture(DimensionProperties.planetRings);

				GL11.glRotated(70, 1, 0, 0);
				GL11.glTranslated(0, -10, 50);
				
				GL11.glColor4f(parentRingColor[0], parentRingColor[1], parentRingColor[2],multiplier);
				tessellator1.startDrawing(GL11.GL_QUADS);
				tessellator1.addVertexWithUV((double)f10, ringDist, (double)(-f10),1.0D, 0.0D);
				tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)(-f10), 0.0D, 0.0D);
				tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)f10, 0.0D, 1.0D);
				tessellator1.addVertexWithUV((double)f10, ringDist, (double)f10, 1.0D, 1.0D);
				tessellator1.draw();
				GL11.glPopMatrix();

				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GL11.glPushMatrix();

				GL11.glRotatef(90f, 0f, 1f, 0f);
				GL11.glRotated(70, 1, 0, 0);
				GL11.glTranslated(0, -10, 50);

				mc.renderEngine.bindTexture(DimensionProperties.planetRingShadow);
				GL11.glColor4f(0f, 0f, 0f,1);
				tessellator1.startDrawing(GL11.GL_QUADS);
				tessellator1.addVertexWithUV((double)f10, ringDist, (double)(-f10),1.0D, 0.0D);
				tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)(-f10), 0.0D, 0.0D);
				tessellator1.addVertexWithUV((double)(-f10), ringDist, (double)f10, 0.0D, 1.0D);
				tessellator1.addVertexWithUV((double)f10, ringDist, (double)f10, 1.0D, 1.0D);
				tessellator1.draw();
				GL11.glPopMatrix();

				OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
			}
			PlanetRenderContext.ViewKind viewKind =
					world.provider.dimensionId == Configuration.spaceDimId
							? PlanetRenderContext.ViewKind.STATION
							: PlanetRenderContext.ViewKind.SKY;
			PlanetRenderContext parentContext =
					PlanetRenderContext.fromProperties(
							parentPropertiesForRender, parentPlanetIcon,
							0, 0, -100,
							66f*AstronomicalBodyHelper
									.getBodySizeMultiplier(
											planetOrbitalDistance),
							Math.max(0F, Math.min(1F, multiplier)),
							rotation, viewKind);
			if(CelestialRenderDirection.resolveBodyLocal(myPhi,
					parentRenderTheta, bodyLocalLightDirection))
				parentContext = parentContext.withLightDirection(
						bodyLocalLightDirection[0],
						bodyLocalLightDirection[1],
						bodyLocalLightDirection[2]);
			renderPlanet2(tessellator1, parentContext);
			GL11.glPopMatrix();
		}

		for(DimensionProperties moons : children) {
			GL11.glPushMatrix();

			moons.orbitalPhi = 10;
			double moonRenderTheta = partialTicks*moons.orbitTheta
					+(1-partialTicks)*moons.prevOrbitalTheta;
			double rot = moonRenderTheta*180F/Math.PI;

			GL11.glRotatef((float)moons.orbitalPhi, 0f, 0f, 1f);
			GL11.glRotated(rot, 1f, 0f, 0f);


			//Close enough approximation, I missed something but seems to off by no more than 30*
			//Nobody will look
			float phiAngle = (float)((moons.orbitalPhi) * Math.PI/180f);
			double x = -MathHelper.sin(phiAngle)*MathHelper.cos((float)moons.orbitTheta);
			double y = MathHelper.sin((float)moons.orbitTheta);
			double rotation = -Math.PI/2f + Math.atan2(x, y) - (moons.orbitTheta - Math.PI)*MathHelper.sin(phiAngle);


			//double x = -MathHelper.sin(phiAngle)*MathHelper.cos((float)moons.orbitTheta);
			//double y = MathHelper.sin((float)moons.orbitTheta);


			//double x = -MathHelper.cos((float)moons.orbitTheta)*MathHelper.sin(phiAngle);
			//double y = -MathHelper.sin((float)moons.orbitTheta);
			//double z = MathHelper.cos(phiAngle)*MathHelper.cos((float)moons.orbitTheta) - 1;



			//double hyp = Math.sqrt(x*x +  y*y);
			//System.out.println(x*x + z*z + y*y);
			//double rotation = Math.atan2(z,hyp );// - MathHelper.sin((float)moons.orbitTheta);//-Math.PI/2f + Math.atan2(x, y) - (moons.orbitTheta - Math.PI)*MathHelper.sin(phiAngle)*hyp;

			//renderPlanet(tessellator1, moons.getPlanetIcon(), (1/(float)moons.getParentOrbitalDistance())*moons.gravitationalMultiplier, multiplier, rotation, moons.hasAtmosphere(), moons.skyColor, moons.ringColor, isGasGiant, moons.hasRings());
			PlanetRenderContext moonContext =
					PlanetRenderContext.fromProperties(moons,
							moons.getPlanetIcon(), 0, 0, -100,
							10f*AstronomicalBodyHelper
									.getBodySizeMultiplier(
											moons.getParentOrbitalDistance()),
							Math.max(0F, Math.min(1F, multiplier)),
							rotation,
							PlanetRenderContext.ViewKind.SKY);
			if(CelestialRenderDirection.resolveBodyLocal(
					moons.orbitalPhi, moonRenderTheta,
					bodyLocalLightDirection))
				moonContext = moonContext.withLightDirection(
						bodyLocalLightDirection[0],
						bodyLocalLightDirection[1],
						bodyLocalLightDirection[2]);
			renderPlanet(tessellator1, moonContext);
			GL11.glPopMatrix();
		}

		GL11.glEnable(GL11.GL_FOG);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);

		GL11.glPopMatrix();
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glColor3f(0.0F, 0.0F, 0.0F);
		double d0 = this.mc.thePlayer.getPosition(partialTicks).yCoord - mc.theWorld.getHorizon();

		if (d0 < 0.0D)
		{
			GL11.glPushMatrix();
			GL11.glTranslatef(0.0F, 12.0F, 0.0F);
			GL11.glCallList(this.glSkyList2);
			GL11.glPopMatrix();
			f8 = 1.0F;
			f9 = -((float)(d0 + 65.0D));
			f10 = -f8;
			tessellator1.startDrawingQuads();
			tessellator1.setColorRGBA_I(0, 255);
			tessellator1.addVertex((double)(-f8), (double)f9, (double)f8);
			tessellator1.addVertex((double)f8, (double)f9, (double)f8);
			tessellator1.addVertex((double)f8, (double)f10, (double)f8);
			tessellator1.addVertex((double)(-f8), (double)f10, (double)f8);
			tessellator1.addVertex((double)(-f8), (double)f10, (double)(-f8));
			tessellator1.addVertex((double)f8, (double)f10, (double)(-f8));
			tessellator1.addVertex((double)f8, (double)f9, (double)(-f8));
			tessellator1.addVertex((double)(-f8), (double)f9, (double)(-f8));
			tessellator1.addVertex((double)f8, (double)f10, (double)(-f8));
			tessellator1.addVertex((double)f8, (double)f10, (double)f8);
			tessellator1.addVertex((double)f8, (double)f9, (double)f8);
			tessellator1.addVertex((double)f8, (double)f9, (double)(-f8));
			tessellator1.addVertex((double)(-f8), (double)f9, (double)(-f8));
			tessellator1.addVertex((double)(-f8), (double)f9, (double)f8);
			tessellator1.addVertex((double)(-f8), (double)f10, (double)f8);
			tessellator1.addVertex((double)(-f8), (double)f10, (double)(-f8));
			tessellator1.addVertex((double)(-f8), (double)f10, (double)(-f8));
			tessellator1.addVertex((double)(-f8), (double)f10, (double)f8);
			tessellator1.addVertex((double)f8, (double)f10, (double)f8);
			tessellator1.addVertex((double)f8, (double)f10, (double)(-f8));
			tessellator1.draw();
		}

		if (mc.theWorld.provider.isSkyColored())
		{
			GL11.glColor3f(f1 * 0.2F + 0.04F, f2 * 0.2F + 0.04F, f3 * 0.6F + 0.1F);
		}
		else
		{
			GL11.glColor3f(f1, f2, f3);
		}

		//Blackness @ bottom of world
		/*GL11.glPushMatrix();
		GL11.glTranslatef(0.0F, -((float)(d0 - 16.0D)), 0.0F);
		GL11.glCallList(this.glSkyList2);
		GL11.glPopMatrix();*/

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(true);

		BlackHoleRenderManager.INSTANCE.renderQueued();
		RocketEventHandler.onPostWorldRender(partialTicks);
	}

	protected ForgeDirection getRotationAxis(DimensionProperties properties, int posX, int posZ) {
		return ForgeDirection.EAST;
	}

	protected ResourceLocation getTextureForPlanet(DimensionProperties properties) {
		return properties.getPlanetIcon();
	}

	protected ResourceLocation getTextureForPlanetLEO(DimensionProperties properties) {
		return properties.getPlanetIcon();
	}

	protected void renderPlanet(Tessellator buffer, ResourceLocation icon, float planetOrbitalDistance, float alphaMultiplier, double shadowAngle, boolean hasAtmosphere, float[] skyColor, float[] ringColor, boolean gasGiant, boolean hasRing) {
		renderPlanet2(buffer, icon, 0, 0, -100, 10f*AstronomicalBodyHelper.getBodySizeMultiplier(planetOrbitalDistance), alphaMultiplier, shadowAngle, hasAtmosphere, skyColor, ringColor, gasGiant, hasRing);
	}

	protected void renderPlanet(Tessellator buffer,
			PlanetRenderContext context) {
		renderPlanet2(buffer, context);
	}

	protected void renderPlanet2(Tessellator buffer, ResourceLocation icon, int locationX, int locationY, double zLevel, float size, float alphaMultiplier, double shadowAngle, boolean hasAtmosphere, float[] skyColor, float[] ringColor, boolean gasGiant, boolean hasRing) {
		renderPlanetPubHelper(buffer, icon, locationX, locationY, zLevel, size, alphaMultiplier, shadowAngle, hasAtmosphere, skyColor, ringColor, gasGiant, hasRing);
	}

	protected void renderPlanet2(Tessellator buffer,
			PlanetRenderContext context) {
		renderPlanetPubHelper(buffer, context);
	}

	protected void rotateAroundAxis() {
		Vector3F<Float> axis = getRotateAxis();
		GL11.glRotatef(getSkyRotationAmount() * 360.0F, axis.x, axis.y, axis.z);
	}

	protected float getSkyRotationAmount() {
		return celestialAngle;
	}

	protected Vector3F<Float> getRotateAxis() {
		return axis;
	}

	/**
	 * Resolves the same primary-light direction produced by the GL rotations
	 * that are applied before the sun quad is submitted.
	 */
	protected boolean resolvePrimaryLightDirection(
			double rotationalPhiDegrees, float[] output) {
		return CelestialRenderDirection.resolvePlanet(celestialAngle,
				axis.x, axis.y, axis.z, rotationalPhiDegrees, output);
	}

	public static void renderPlanetPubHelper(Tessellator tessellator1, ResourceLocation icon, int locationX, int locationY, double zLevel, float size, float alphaMultiplier, double shadowAngle, boolean hasAtmosphere, float[] skyColor, float[] ringColor, boolean gasGiant, boolean hasRing) {
		renderPlanetPubHelper(tessellator1, PlanetRenderContext.legacy(icon,
				locationX, locationY, zLevel, size, alphaMultiplier,
				shadowAngle, hasAtmosphere, skyColor, ringColor, gasGiant,
				hasRing, PlanetRenderContext.ViewKind.SKY));
	}

	public static void renderPlanetPubHelper(Tessellator tessellator1,
			PlanetRenderContext context) {
		if(context == null || context.getSurfaceTexture() == null
				|| context.getSize() <= 0F)
			return;
		GL11.glEnable(GL11.GL_BLEND);

		//Set planet Orbiting distance; size
		float f10 = context.getSize();

		float f14 = 1f;//(float)(l + 0) / 4.0F;
		float f15 = 0f;//(float)(i1 + 0) / 2.0F;
		float f16 = f15;//(float)(l + 1) / 4.0F;
		float f17 = f14;//(float)(i1 + 1) / 2.0F;

		GL11.glPushMatrix();
		GL11.glTranslated(context.getLocationX(), context.getZLevel(),
				context.getLocationY());

		GL11.glPushMatrix();
		GL11.glRotated(90-context.getLegacyShadowAngle()
				*180/Math.PI, 0, 1, 0);

		//Rings
		if(context.hasRings()) {
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			float[] ringColor = context.getRingColor();
			GL11.glColor4f(ringColor[0], ringColor[1], ringColor[2],
					context.getAlpha()*0.2f);
			float ringSize = f10 *1.4f;
			Minecraft.getMinecraft().renderEngine.bindTexture(DimensionProperties.planetRings);
			tessellator1.startDrawing(GL11.GL_QUADS);
			
			tessellator1.addVertexWithUV(-ringSize,
					context.getZLevel()-0.01f, ringSize, f16, f17);
			tessellator1.addVertexWithUV(ringSize,
					context.getZLevel()-0.01f, ringSize, f14, f17);
			tessellator1.addVertexWithUV(ringSize,
					context.getZLevel()-0.01f, -ringSize, f14, f15);
			tessellator1.addVertexWithUV(-ringSize,
					context.getZLevel()-0.01f, -ringSize, f16, f15);
			tessellator1.draw();
			
			
			GL11.glColor4f(0f, 0f, 0f, context.getAlpha());
			Minecraft.getMinecraft().renderEngine.bindTexture(DimensionProperties.planetRingShadow);
			tessellator1.startDrawing(GL11.GL_QUADS);
			tessellator1.addVertexWithUV(-ringSize,
					context.getZLevel()-0.01f, ringSize, f16, f17);
			tessellator1.addVertexWithUV(ringSize,
					context.getZLevel()-0.01f, ringSize, f14, f17);
			tessellator1.addVertexWithUV(ringSize,
					context.getZLevel()-0.01f, -ringSize, f14, f15);
			tessellator1.addVertexWithUV(-ringSize,
					context.getZLevel()-0.01f, -ringSize, f16, f15);
			tessellator1.draw();
		}
		GL11.glPopMatrix();

		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		Minecraft.getMinecraft().renderEngine.bindTexture(
				context.getSurfaceTexture());
		//TODO: draw sky planets

		tessellator1.startDrawingQuads();

		tessellator1.setColorRGBA_F(1f, 1f, 1f, context.getAlpha());

		tessellator1.addVertexWithUV((double)(-f10), context.getZLevel(),
				(double)f10, (double)f16, (double)f17);
		tessellator1.addVertexWithUV((double)f10, context.getZLevel(),
				(double)f10, (double)f14, (double)f17);
		tessellator1.addVertexWithUV((double)f10, context.getZLevel(),
				(double)(-f10), (double)f14, (double)f15);
		tessellator1.addVertexWithUV((double)(-f10), context.getZLevel(),
				(double)(-f10), (double)f16, (double)f15);

		tessellator1.draw();

		GL11.glPushMatrix();
		GL11.glRotated(90-context.getLegacyShadowAngle()
				*180/Math.PI, 0, 1, 0);

		//Draw Shadow
		Minecraft.getMinecraft().renderEngine.bindTexture(DimensionProperties.getShadowResource());
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1f, 1f, 1f, context.getAlpha());
		
		tessellator1.startDrawing(GL11.GL_QUADS);
		tessellator1.addVertexWithUV(-f10,
				context.getZLevel()-0.01f, f10, f16, f17);
		tessellator1.addVertexWithUV(f10,
				context.getZLevel()-0.01f, f10, f14, f17);
		tessellator1.addVertexWithUV(f10,
				context.getZLevel()-0.01f, -f10, f14, f15);
		tessellator1.addVertexWithUV(-f10,
				context.getZLevel()-0.01f, -f10, f16, f15);
		tessellator1.draw();
		GL11.glPopMatrix();

		// One continuous, light-aware overlay replaces both the
		// unconditional atmGlow texture and the additive atmosphere2 pass.
		AnalyticAtmosphereRenderer.renderBillboardHalo(tessellator1, context);

		GL11.glPopMatrix();
		
		
			tessellator1.setColorRGBA_F(1f, 1f, 1f, 1f);
		}

	private static float sanitizeAtmosphereDensity(float density) {
		if(Float.isNaN(density) || Float.isInfinite(density))
			return 0F;
		return Math.max(0F, Math.min(16F, density));
	}

	private static float clamp01(float value) {
		if(Float.isNaN(value) || Float.isInfinite(value))
			return 0F;
		return Math.max(0F, Math.min(1F, value));
	}

	private static void setStarFieldColor(float visibility,
			boolean physicalAtmosphere) {
		if(physicalAtmosphere)
			GL11.glColor4f(1F, 1F, 1F, clamp01(visibility));
		else
			GL11.glColor4f(visibility, visibility, visibility, visibility);
	}

	private void drawStar(Tessellator buffer, int solarOrbitalDistance,
			float sunSize, float r, float g, float b, float multiplier,
			boolean physicalTransmittance) {
		//Set sun color and distance
		float alpha = physicalTransmittance
				? clamp01(multiplier)
				: Math.min(clamp01(multiplier)*2F, 1F);
		GL11.glColor4f(r, g, b, alpha);
		buffer.startDrawingQuads();
		float f10 = sunSize*20f*AstronomicalBodyHelper.getBodySizeMultiplier(solarOrbitalDistance);
		//multiplier = 2;
		buffer.addVertexWithUV((double)(-f10), 100.0D, (double)(-f10), 0.0D, 0.0D);
		buffer.addVertexWithUV((double)f10, 100.0D, (double)(-f10), 1.0D, 0.0D);
		buffer.addVertexWithUV((double)f10, 100.0D, (double)f10, 1.0D, 1.0D);
		buffer.addVertexWithUV((double)(-f10), 100.0D, (double)f10, 0.0D, 1.0D);
		buffer.draw();
	}

	private void drawStarOrBlackHole(
			Tessellator buffer, StellarBody star, int solarOrbitalDistance,
			float sunSize, float red, float green, float blue,
			float multiplier, boolean physicalTransmittance) {
		float alpha = physicalTransmittance
				? clamp01(multiplier)
				: Math.min(clamp01(multiplier)*2F, 1F);
		if(star != null && star.isBlackHole()) {
			float halfSize = sunSize*20F
					*AstronomicalBodyHelper.getBodySizeMultiplier(
							Math.max(1, solarOrbitalDistance));
			BlackHoleRenderManager.INSTANCE.queuePlanetary(
					star, halfSize, alpha);
		}
		else {
			drawStar(buffer, solarOrbitalDistance, sunSize, red, green, blue,
					multiplier, physicalTransmittance);
		}
	}
}
