package zmaster587.advancedRocketry.client.render.blackhole;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * Guaranteed fixed-function renderer used for tiny targets and every failed
 * shader/capture/capability path.
 */
final class FallbackBlackHoleRenderer {

	private static final ResourceLocation BLACK_HOLE =
			new ResourceLocation(
					"advancedrocketry:textures/env/blackhole.png");
	private static final ResourceLocation ACCRETION_DISK =
			new ResourceLocation(
					"advancedrocketry:textures/env/accretiondisk.png");
	private static final ResourceLocation BLACK_HOLE_ICON =
			new ResourceLocation(
					"advancedrocketry:textures/env/blackhole_icon.png");
	private static final int CIRCLE_SEGMENTS = 64;
	private static final double SQRT_27 = Math.sqrt(27D);

	void render(BlackHoleView view, BlackHoleRenderContext context,
			boolean iconOnly) {
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);

		if(iconOnly) {
			renderIcon(view, context.getMinecraft());
			return;
		}

		renderAccretionLayers(view, context);
		renderOpaqueShadow(view);
		renderCoreHighlight(view, context.getMinecraft());
		renderCriticalBand(view);
	}

	private void renderIcon(BlackHoleView view, Minecraft minecraft) {
		minecraft.renderEngine.bindTexture(BLACK_HOLE_ICON);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1F, 1F, 1F, view.getAlpha());
		float radius = Math.max(1F, view.getScreenRadius());
		drawTexturedQuad(view.getCenterX(), view.getCenterY(), radius, radius,
				1F, 0F, 0F);
	}

	private void renderAccretionLayers(BlackHoleView view,
			BlackHoleRenderContext context) {
		if(view.getAccretionRate() <= 0.001F)
			return;
		context.getMinecraft().renderEngine.bindTexture(ACCRETION_DISK);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

		float outerScale = Math.max(1.35F, Math.min(3.6F,
				view.getDiskOuterRadiusOverM()/(float)SQRT_27));
		float major = view.getScreenRadius()*outerScale;
		float flatten = Math.max(0.12F,
				Math.abs((float)Math.cos(view.getViewInclinationRadians())));
		float minor = major*flatten;
		float time = (float)(context.getAnimationTime()*0.004D
				+ view.getDeterministicPhase()*32D);
		float alpha = view.getAlpha()*view.getAccretionRate();

		GL11.glColor4f(1F, 0.48F, 0.12F, 0.42F*alpha);
		drawTexturedQuad(view.getCenterX(), view.getCenterY(), major, minor,
				view.getProjectedAxisX(), view.getProjectedAxisY(), time);
		GL11.glColor4f(0.45F, 0.68F, 1F, 0.25F*alpha);
		drawTexturedQuad(view.getCenterX(), view.getCenterY(), major*0.86F,
				Math.max(minor*0.86F, view.getScreenRadius()*0.1F),
				view.getProjectedAxisX(), view.getProjectedAxisY(),
				-time*1.37F + 0.43F);
	}

	private void renderOpaqueShadow(BlackHoleView view) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glColor4f(0F, 0F, 0F, 1F);
		GL11.glBegin(GL11.GL_TRIANGLE_FAN);
		GL11.glVertex2f(view.getCenterX(), view.getCenterY());
		for(int segment = 0; segment <= CIRCLE_SEGMENTS; segment++) {
			double angle = Math.PI*2D*segment/CIRCLE_SEGMENTS;
			GL11.glVertex2d(view.getCenterX()
					+ Math.cos(angle)*view.getScreenRadius(),
					view.getCenterY()
					+ Math.sin(angle)*view.getScreenRadius());
		}
		GL11.glEnd();
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	private void renderCoreHighlight(BlackHoleView view,
			Minecraft minecraft) {
		minecraft.renderEngine.bindTexture(BLACK_HOLE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1F, 1F, 1F,
				Math.min(0.38F, 0.18F + 0.2F*view.getAccretionRate())
				*view.getAlpha());
		drawTexturedQuad(view.getCenterX(), view.getCenterY(),
				view.getScreenRadius()*1.04F,
				view.getScreenRadius()*1.04F, 1F, 0F,
				view.getDeterministicPhase()*(float)Math.PI*2F);
	}

	private void renderCriticalBand(BlackHoleView view) {
		if(view.getAccretionRate() <= 0.001F)
			return;
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glLineWidth(Math.max(1F,
				Math.min(2.5F, view.getScreenRadius()*0.035F)));
		GL11.glColor4f(1F, 0.72F, 0.36F,
				0.55F*view.getAlpha()*view.getAccretionRate());
		GL11.glBegin(GL11.GL_LINE_LOOP);
		for(int segment = 0; segment < CIRCLE_SEGMENTS; segment++) {
			double angle = Math.PI*2D*segment/CIRCLE_SEGMENTS;
			double radius = view.getScreenRadius()*1.055D;
			GL11.glVertex2d(view.getCenterX() + Math.cos(angle)*radius,
					view.getCenterY() + Math.sin(angle)*radius);
		}
		GL11.glEnd();
		GL11.glLineWidth(1F);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	/**
	 * Axis is the projected spin direction (the ellipse minor axis).
	 */
	private static void drawTexturedQuad(float centerX, float centerY,
			float majorRadius, float minorRadius, float axisX, float axisY,
			float textureRotation) {
		float axisLength = (float)Math.sqrt(axisX*axisX + axisY*axisY);
		if(axisLength < 1.0E-5F) {
			axisX = 0F;
			axisY = 1F;
		}
		else {
			axisX /= axisLength;
			axisY /= axisLength;
		}
		float tangentX = -axisY;
		float tangentY = axisX;
		float majorX = tangentX*majorRadius;
		float majorY = tangentY*majorRadius;
		float minorX = axisX*minorRadius;
		float minorY = axisY*minorRadius;

		float cosine = (float)Math.cos(textureRotation);
		float sine = (float)Math.sin(textureRotation);
		GL11.glBegin(GL11.GL_QUADS);
		vertexWithRotatedUv(centerX - majorX - minorX,
				centerY - majorY - minorY, 0F, 0F, cosine, sine);
		vertexWithRotatedUv(centerX + majorX - minorX,
				centerY + majorY - minorY, 1F, 0F, cosine, sine);
		vertexWithRotatedUv(centerX + majorX + minorX,
				centerY + majorY + minorY, 1F, 1F, cosine, sine);
		vertexWithRotatedUv(centerX - majorX + minorX,
				centerY - majorY + minorY, 0F, 1F, cosine, sine);
		GL11.glEnd();
	}

	private static void vertexWithRotatedUv(float x, float y, float u,
			float v, float cosine, float sine) {
		float centeredU = u - 0.5F;
		float centeredV = v - 0.5F;
		GL11.glTexCoord2f(centeredU*cosine - centeredV*sine + 0.5F,
				centeredU*sine + centeredV*cosine + 0.5F);
		GL11.glVertex2f(x, y);
	}
}
