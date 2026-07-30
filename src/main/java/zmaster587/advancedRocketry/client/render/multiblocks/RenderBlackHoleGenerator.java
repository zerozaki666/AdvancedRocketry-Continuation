package zmaster587.advancedRocketry.client.render.multiblocks;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.util.ForgeDirection;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.tile.multiblock.energy.TileBlackHoleGenerator;
import zmaster587.libVulpes.block.RotatableBlock;
import zmaster587.libVulpes.render.RenderHelper;

/**
 * Machine-only renderer. Astronomical black holes use the sky renderer and do
 * not pass through this TESR.
 */
@SideOnly(Side.CLIENT)
public class RenderBlackHoleGenerator extends TileEntitySpecialRenderer {

	private static final ResourceLocation MODEL_LOCATION =
			new ResourceLocation(
					"advancedrocketry:models/blackholegenerator.obj");
	private static final ResourceLocation TEXTURE =
			new ResourceLocation(
					"advancedrocketry:textures/models/blackholegenerator.png");

	private final IModelCustom model;

	public RenderBlackHoleGenerator() {
		IModelCustom loadedModel = null;
		try {
			loadedModel = AdvancedModelLoader.loadModel(MODEL_LOCATION);
		}
		catch(RuntimeException ex) {
			AdvancedRocketry.logger.error(
					"Unable to load black hole generator model", ex);
		}
		model = loadedModel;
	}

	@Override
	public void renderTileEntityAt(TileEntity tile, double x, double y,
			double z, float partialTicks) {
		if(!(tile instanceof TileBlackHoleGenerator) || model == null)
			return;

		TileBlackHoleGenerator generator = (TileBlackHoleGenerator)tile;
		if(!generator.canRender())
			return;

		float previousBrightnessX = OpenGlHelper.lastBrightnessX;
		float previousBrightnessY = OpenGlHelper.lastBrightnessY;
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();
		try {
			int brightness = tile.getWorldObj().getLightBrightnessForSkyBlocks(
					tile.xCoord, tile.yCoord + 1, tile.zCoord, 0);
			OpenGlHelper.setLightmapTextureCoords(
					OpenGlHelper.lightmapTexUnit,
					brightness & 0xffff, brightness >>> 16);

			ForgeDirection front =
					RotatableBlock.getFront(tile.getBlockMetadata());
			GL11.glTranslated(x + 0.5D, y + 0.5D, z + 0.5D);
			GL11.glRotatef((front.offsetZ == 1 ? 180F : 0F)
					- front.offsetX * 90F, 0F, 1F, 0F);

			bindTexture(TEXTURE);
			GL11.glColor4f(1F, 1F, 1F, 1F);
			model.renderAll();

			if(generator.isProducingPower())
				renderEnergy(generator, partialTicks);
		}
		finally {
			GL11.glPopMatrix();
			GL11.glPopAttrib();
			OpenGlHelper.setLightmapTextureCoords(
					OpenGlHelper.lightmapTexUnit,
					previousBrightnessX, previousBrightnessY);
		}
	}

	private void renderEnergy(TileBlackHoleGenerator generator,
			float partialTicks) {
		double time = generator.getWorldObj().getTotalWorldTime()
				+ partialTicks;
		float offset = (float)Math.sin(time / 2.56D) * 0.3F;

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		GL11.glColor4f(1F, 1F, 0.5F, 0.5F);

		renderEnergyCube(offset);
		renderEnergyCube(-offset);
	}

	private void renderEnergyCube(float yOffset) {
		GL11.glPushMatrix();
		try {
			GL11.glTranslatef(0F, yOffset, 0F);
			Tessellator tessellator = Tessellator.instance;
			tessellator.startDrawingQuads();
			RenderHelper.renderCubeWithUV(tessellator,
					-0.45D, 0.95D, 0.55D,
					0.45D, 1.05D, 1.45D,
					0D, 1D, 0D, 1D);
			tessellator.draw();
		}
		finally {
			GL11.glPopMatrix();
		}
	}
}
