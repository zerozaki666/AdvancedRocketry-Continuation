package zmaster587.advancedRocketry.client.render.blackhole;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * Fixed-function fallback for shader-hostile render paths.
 *
 * <p>The old upstream fallback stretched one sparse alpha texture over two
 * large quads.  At astronomical scale that exposed the texture's concentric
 * rings and rectangular proxy.  This renderer keeps the guaranteed
 * fixed-function path, but builds a deterministic warm-white disk from
 * bounded annular strips.  The far side is mapped through a point-lens
 * approximation, the apparent shadow is drawn opaquely, and the direct near
 * side is composited last so it can cross the foreground like Gargantua.</p>
 */
final class FallbackBlackHoleRenderer {

	private static final ResourceLocation BLACK_HOLE_ICON =
			new ResourceLocation(
					"advancedrocketry:textures/env/blackhole_icon.png");
	private static final int CIRCLE_SEGMENTS = 128;
	private static final int DISK_SEGMENTS = 96;
	private static final int DIRECT_RADIAL_BANDS = 24;
	private static final int LENSED_RADIAL_BANDS = 20;
	private static final double TWO_PI = Math.PI*2D;
	private static final double SQRT_27 = Math.sqrt(27D);

	void render(BlackHoleView view, BlackHoleRenderContext context,
			boolean iconOnly) {
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_BLEND);

		if(iconOnly) {
			renderIcon(view, context.getMinecraft());
			return;
		}

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		float sinInclination = Math.abs(
				(float)Math.sin(view.getViewInclinationRadians()));
		if(view.getAccretionRate() > 0.001F) {
			if(sinInclination < 0.12F)
				renderDirectDisk(view, context, true);
			else
				renderLensedFarSide(view, context, sinInclination);
		}
		renderOpaqueShadow(view);
		if(view.getAccretionRate() > 0.001F && sinInclination >= 0.12F) {
			renderDirectDisk(view, context, false);
			/*
			 * Preserve a deep central silhouette when a high-spin ISCO lets
			 * the approximate foreground disk enter the apparent shadow.
			 */
			renderOpaqueShadow(view, 0.55F);
		}
		renderCriticalBand(view, context);
	}

	private void renderIcon(BlackHoleView view, Minecraft minecraft) {
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		minecraft.renderEngine.bindTexture(BLACK_HOLE_ICON);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1F, 1F, 1F, view.getAlpha());
		float radius = Math.max(1F, view.getScreenRadius());
		drawTexturedQuad(view.getCenterX(), view.getCenterY(), radius);
	}

	/**
	 * Draws either the complete face-on disk before the shadow or just the
	 * near half after the shadow.
	 */
	private void renderDirectDisk(BlackHoleView view,
			BlackHoleRenderContext context, boolean completeDisk) {
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		float[] axes = getScreenAxes(view);
		double inner = Math.max(0.001D, view.getDiskInnerRadiusOverM());
		double outer = Math.max(inner + 0.001D,
				view.getDiskOuterRadiusOverM());
		float flattening = Math.max(0.075F, Math.abs(
				(float)Math.cos(view.getViewInclinationRadians())));
		double start;
		double end;
		if(completeDisk) {
			start = 0D;
			end = TWO_PI;
		}
		else {
			boolean positiveMinorIsNear =
					Math.cos(view.getViewInclinationRadians()) < 0D;
			double overlap = Math.PI*0.035D;
			start = (positiveMinorIsNear ? 0D : Math.PI) - overlap;
			end = start + Math.PI + overlap*2D;
		}

		for(int band = 0; band < DIRECT_RADIAL_BANDS; band++) {
			double radius0 = mix(inner, outer,
					band/(double)DIRECT_RADIAL_BANDS);
			double radius1 = mix(inner, outer,
					(band + 1D)/DIRECT_RADIAL_BANDS);
			GL11.glBegin(GL11.GL_QUAD_STRIP);
			for(int segment = 0; segment <= DISK_SEGMENTS; segment++) {
				double angle = mix(start, end,
						segment/(double)DISK_SEGMENTS);
				emitDirectDiskVertex(view, context, axes, radius0, angle,
						flattening, completeDisk ? 0.78F : 1F);
				emitDirectDiskVertex(view, context, axes, radius1, angle,
						flattening, completeDisk ? 0.78F : 1F);
			}
			GL11.glEnd();
		}
	}

	private void renderLensedFarSide(BlackHoleView view,
			BlackHoleRenderContext context, float sinInclination) {
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		float[] axes = getScreenAxes(view);
		double inner = Math.max(0.001D, view.getDiskInnerRadiusOverM());
		double outer = Math.max(inner + 0.001D,
				view.getDiskOuterRadiusOverM());
		float flattening = Math.max(0.075F, Math.abs(
				(float)Math.cos(view.getViewInclinationRadians())));
		boolean positiveMinorIsFar =
				Math.cos(view.getViewInclinationRadians()) >= 0D;
		double overlap = Math.PI*0.035D;
		double start = (positiveMinorIsFar ? 0D : Math.PI) - overlap;
		double end = start + Math.PI + overlap*2D;
		double einsteinRadius = 1.10D + 0.20D*sinInclination;
		float lensStrength = smoothstep(0.08F, 0.55F, sinInclination);

		drawLensedBranch(view, context, axes, inner, outer, flattening,
				start, end, einsteinRadius, 1D, 0.88F*lensStrength);
		drawLensedBranch(view, context, axes, inner, outer, flattening,
				start, end, einsteinRadius, -1D, 0.48F*lensStrength);
	}

	private void drawLensedBranch(BlackHoleView view,
			BlackHoleRenderContext context, float[] axes, double inner,
			double outer, float flattening, double start, double end,
			double einsteinRadius, double parity, float alphaScale) {
		for(int band = 0; band < LENSED_RADIAL_BANDS; band++) {
			double radius0 = mix(inner, outer,
					band/(double)LENSED_RADIAL_BANDS);
			double radius1 = mix(inner, outer,
					(band + 1D)/LENSED_RADIAL_BANDS);
			GL11.glBegin(GL11.GL_QUAD_STRIP);
			for(int segment = 0; segment <= DISK_SEGMENTS; segment++) {
				double fraction = segment/(double)DISK_SEGMENTS;
				double angle = mix(start, end, fraction);
				float endFade = 0.38F + 0.62F*(float)Math.sqrt(
						Math.max(0D, Math.sin(Math.PI*fraction)));
				emitLensedDiskVertex(view, context, axes, radius0, angle,
						flattening, einsteinRadius, parity,
						alphaScale*endFade);
				emitLensedDiskVertex(view, context, axes, radius1, angle,
						flattening, einsteinRadius, parity,
						alphaScale*endFade);
			}
			GL11.glEnd();
		}
	}

	private void emitDirectDiskVertex(BlackHoleView view,
			BlackHoleRenderContext context, float[] axes, double radiusOverM,
			double angle, float flattening, float alphaScale) {
		double normalizedRadius = radiusOverM/SQRT_27;
		double major = Math.cos(angle)*normalizedRadius;
		double minor = Math.sin(angle)*normalizedRadius*flattening;
		setDiskColor(view, context, radiusOverM, angle, alphaScale);
		emitScreenVertex(view, axes, major, minor);
	}

	private void emitLensedDiskVertex(BlackHoleView view,
			BlackHoleRenderContext context, float[] axes, double radiusOverM,
			double angle, float flattening, double einsteinRadius,
			double parity, float alphaScale) {
		double sourceRadius = radiusOverM/SQRT_27;
		double sourceX = Math.cos(angle)*sourceRadius;
		double sourceY = Math.sin(angle)*sourceRadius*flattening;
		double sourceLength = Math.sqrt(
				sourceX*sourceX + sourceY*sourceY);
		double imageLength = 0.5D*(sourceLength + parity*Math.sqrt(
				sourceLength*sourceLength
				+ 4D*einsteinRadius*einsteinRadius));
		double scale = sourceLength < 1.0E-6D
				? 0D : imageLength/sourceLength;
		setDiskColor(view, context, radiusOverM,
				parity < 0D ? angle + Math.PI : angle, alphaScale);
		emitScreenVertex(view, axes, sourceX*scale, sourceY*scale);
	}

	private void emitScreenVertex(BlackHoleView view, float[] axes,
			double major, double minor) {
		double scale = view.getScreenRadius();
		GL11.glVertex2d(view.getCenterX()
				+ (axes[2]*major + axes[0]*minor)*scale,
				view.getCenterY()
				+ (axes[3]*major + axes[1]*minor)*scale);
	}

	private void setDiskColor(BlackHoleView view,
			BlackHoleRenderContext context, double radiusOverM, double angle,
			float alphaScale) {
		double inner = Math.max(0.001D, view.getDiskInnerRadiusOverM());
		double outer = Math.max(inner + 0.001D,
				view.getDiskOuterRadiusOverM());
		double radialFraction = clamp(
				(radiusOverM - inner)/(outer - inner), 0D, 1D);
		double safeRadius = Math.max(radiusOverM, inner + 0.0001D);
		double zeroTorque = Math.max(0D,
				1D - Math.sqrt(inner/safeRadius));
		double temperature = Math.pow(Math.max(zeroTorque
				/(safeRadius*safeRadius*safeRadius), 0D), 0.25D);
		double heat = clamp(temperature*Math.pow(Math.max(inner, 1D), 0.75D)
				*2.65D, 0D, 1D);

		double phase = context.getAnimationTime()*0.012D
				+ view.getDeterministicPhase()*TWO_PI;
		double differentialPhase = phase/Math.pow(
				Math.max(radiusOverM, 1D), 1.5D);
		double structure = 0.72D
				+ 0.16D*Math.sin(angle*11D + radiusOverM*2.7D
						- differentialPhase*42D)
				+ 0.09D*Math.sin(angle*23D - radiusOverM*5.1D
						+ differentialPhase*27D)
				+ 0.05D*Math.sin(angle*47D + radiusOverM*1.3D
						- differentialPhase*15D);
		structure = clamp(structure, 0.24D, 1.08D);

		double beta = Math.min(0.85D,
				1D/Math.sqrt(Math.max(safeRadius, 1D)));
		double gamma = 1D/Math.sqrt(Math.max(0.001D, 1D - beta*beta));
		double betaLos = clamp(beta*Math.sin(
				view.getViewInclinationRadians())*Math.cos(angle),
				-0.849D, 0.849D);
		double doppler = clamp(1D/(gamma*(1D - betaLos)), 0.25D, 4D);
		double beaming = clamp(doppler*doppler*doppler, 0.24D, 4.2D);

		double outerFade = 1D - smoothstep(0.68D, 1D, radialFraction);
		double emissivity = (0.24D + 0.92D*heat)*outerFade
				*structure*beaming;
		double accretion = Math.sqrt(clamp(view.getAccretionRate(), 0F, 1F));
		float alpha = (float)clamp(view.getAlpha()*accretion*alphaScale
				*emissivity, 0D, 0.86D);

		double warmBlend = clamp(0.18D + heat*0.95D, 0D, 1D);
		double red = 1D;
		double green = mix(0.30D, 0.92D, warmBlend);
		double blue = mix(0.055D, 0.72D, warmBlend);
		double approaching = clamp(doppler - 1D, 0D, 1D);
		double receding = clamp(1D - doppler, 0D, 1D);
		green = clamp(green + approaching*0.055D - receding*0.07D,
				0D, 1D);
		blue = clamp(blue + approaching*0.12D - receding*0.08D,
				0D, 1D);
		GL11.glColor4f((float)red, (float)green, (float)blue, alpha);
	}

	private void renderOpaqueShadow(BlackHoleView view) {
		renderOpaqueShadow(view, 1F);
	}

	private void renderOpaqueShadow(BlackHoleView view, float radiusScale) {
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glColor4f(0F, 0F, 0F, 1F);
		GL11.glBegin(GL11.GL_TRIANGLE_FAN);
		GL11.glVertex2f(view.getCenterX(), view.getCenterY());
		for(int segment = 0; segment <= CIRCLE_SEGMENTS; segment++) {
			double angle = TWO_PI*segment/CIRCLE_SEGMENTS;
			GL11.glVertex2d(view.getCenterX()
					+ Math.cos(angle)*view.getScreenRadius()*radiusScale,
					view.getCenterY()
					+ Math.sin(angle)*view.getScreenRadius()*radiusScale);
		}
		GL11.glEnd();
	}

	private void renderCriticalBand(BlackHoleView view,
			BlackHoleRenderContext context) {
		if(view.getAccretionRate() <= 0.001F)
			return;
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		float[] axes = getScreenAxes(view);
		double innerRadius = 1.012D;
		double outerRadius = 1.035D + Math.min(0.018D,
				1.5D/Math.max(view.getScreenRadius(), 1F));
		double phase = context.getAnimationTime()*0.006D
				+ view.getDeterministicPhase()*TWO_PI;
		GL11.glBegin(GL11.GL_QUAD_STRIP);
		for(int segment = 0; segment <= CIRCLE_SEGMENTS; segment++) {
			double angle = TWO_PI*segment/CIRCLE_SEGMENTS;
			double asymmetry = 0.70D + 0.30D*Math.cos(angle - phase*0.1D);
			double dopplerSide = 0.72D + 0.28D*Math.cos(angle);
			float alpha = (float)clamp(view.getAlpha()
					*Math.sqrt(view.getAccretionRate())
					*asymmetry*dopplerSide, 0D, 0.82D);
			GL11.glColor4f(1F, 0.86F, 0.61F, alpha*0.62F);
			emitScreenVertex(view, axes,
					Math.cos(angle)*innerRadius,
					Math.sin(angle)*innerRadius);
			GL11.glColor4f(1F, 0.93F, 0.78F, alpha);
			emitScreenVertex(view, axes,
					Math.cos(angle)*outerRadius,
					Math.sin(angle)*outerRadius);
		}
		GL11.glEnd();
	}

	/**
	 * Returns minor-axis X/Y followed by major-axis X/Y.
	 */
	private static float[] getScreenAxes(BlackHoleView view) {
		float axisX = view.getProjectedAxisX();
		float axisY = view.getProjectedAxisY();
		float length = (float)Math.sqrt(axisX*axisX + axisY*axisY);
		if(length < 1.0E-5F) {
			axisX = 0F;
			axisY = 1F;
		}
		else {
			axisX /= length;
			axisY /= length;
		}
		return new float[] {axisX, axisY, -axisY, axisX};
	}

	private static void drawTexturedQuad(float centerX, float centerY,
			float radius) {
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0F, 0F);
		GL11.glVertex2f(centerX - radius, centerY - radius);
		GL11.glTexCoord2f(1F, 0F);
		GL11.glVertex2f(centerX + radius, centerY - radius);
		GL11.glTexCoord2f(1F, 1F);
		GL11.glVertex2f(centerX + radius, centerY + radius);
		GL11.glTexCoord2f(0F, 1F);
		GL11.glVertex2f(centerX - radius, centerY + radius);
		GL11.glEnd();
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		return (float)smoothstep((double)edge0, edge1, value);
	}

	private static double smoothstep(double edge0, double edge1,
			double value) {
		double t = clamp((value - edge0)/(edge1 - edge0), 0D, 1D);
		return t*t*(3D - 2D*t);
	}

	private static double mix(double first, double second, double amount) {
		return first + (second - first)*amount;
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double clamp(double value, double minimum,
			double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
