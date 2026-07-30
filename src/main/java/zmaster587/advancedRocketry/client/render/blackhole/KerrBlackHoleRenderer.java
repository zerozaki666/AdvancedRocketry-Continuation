package zmaster587.advancedRocketry.client.render.blackhole;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/**
 * Kerr-inspired bounded screen-space approximation.
 *
 * <p>This is intentionally not a numerical Kerr geodesic solver.  The FAST
 * program uses a clamped weak-field lensing heuristic; HIGH uses a fixed
 * compile-time loop to refine that bounded deflection.</p>
 */
final class KerrBlackHoleRenderer {

	private static final ResourceLocation VERTEX_SHADER =
			new ResourceLocation("advancedrocketry:shaders/blackhole.vert");
	private static final ResourceLocation FAST_FRAGMENT_SHADER =
			new ResourceLocation(
					"advancedrocketry:shaders/blackhole_fast.frag");
	private static final ResourceLocation HIGH_FRAGMENT_SHADER =
			new ResourceLocation(
					"advancedrocketry:shaders/blackhole_high.frag");

	private final BlackHoleShaderProgram fastProgram =
			new BlackHoleShaderProgram(VERTEX_SHADER, FAST_FRAGMENT_SHADER,
					"Kerr FAST");
	private final BlackHoleShaderProgram highProgram =
			new BlackHoleShaderProgram(VERTEX_SHADER, HIGH_FRAGMENT_SHADER,
					"Kerr HIGH");
	private final KerrShadowBoundaryLut shadowBoundary =
			new KerrShadowBoundaryLut();

	boolean render(BlackHoleView view, BlackHoleRenderContext context,
			SceneColorCapture sceneCapture, boolean highQuality,
			int configuredSteps) {
		IResourceManager resourceManager =
				context.getMinecraft().getResourceManager();
		BlackHoleShaderProgram program =
				highQuality ? highProgram : fastProgram;
		KerrShadowBoundaryLut.Entry boundary = shadowBoundary.get(
				view.getSpin(), view.getViewInclinationRadians());
		if(boundary == null || !program.ensureLoaded(resourceManager))
			return false;

		try {
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glEnable(GL11.GL_TEXTURE_2D);

			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D,
					sceneCapture.getTextureId());
			GL13.glActiveTexture(GL13.GL_TEXTURE1);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D,
					boundary.getTextureId());
			GL13.glActiveTexture(GL13.GL_TEXTURE0);

			program.use();
			program.set1i("uSceneColor", 0);
			program.set1i("uShadowBoundary", 1);
			program.set4f("uViewport", context.getViewportX(),
					context.getViewportY(), context.getViewportWidth(),
					context.getViewportHeight());
			program.set2f("uCenter", view.getCenterX(), view.getCenterY());
			program.set1f("uShadowRadius", view.getScreenRadius());
			program.set1f("uProxyRadius", view.getProxyRadius());
			program.set1f("uMass", view.getMass());
			program.set1f("uSpin", view.getSpin());
			program.set2f("uProjectedSpinAxis",
					view.getProjectedAxisX(), view.getProjectedAxisY());
			program.set1f("uInclination",
					view.getViewInclinationRadians());
			program.set1f("uDiskInner",
					view.getDiskInnerRadiusOverM());
			program.set1f("uDiskOuter",
					view.getDiskOuterRadiusOverM());
			program.set1f("uAccretionRate", view.getAccretionRate());
			program.set1f("uWorldTime",
					(float)(context.getAnimationTime()%1048576D));
			program.set1f("uBodyPhase",
					view.getDeterministicPhase());
			program.set1f("uAlpha", view.getAlpha());
			program.set1f("uSteps",
					Math.max(1, Math.min(32, configuredSteps)));

			drawProxy(view);
			int error = GL11.glGetError();
			if(error != GL11.GL_NO_ERROR)
				BlackHoleDiagnostics.warnOnce("kerr-gl-" + error,
						"Kerr draw returned OpenGL error 0x"
						+ Integer.toHexString(error)
						+ "; using legacy visuals.");
			return error == GL11.GL_NO_ERROR;
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("kerr-render",
					"Kerr approximation failed; using legacy visuals.",
					throwable);
			return false;
		}
		finally {
			try {
				program.stop();
				GL13.glActiveTexture(GL13.GL_TEXTURE1);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
				GL13.glActiveTexture(GL13.GL_TEXTURE0);
			}
			catch(Throwable throwable) {
				BlackHoleDiagnostics.warnOnce("kerr-local-cleanup",
						"Could not clean up a Kerr draw; the pass-level state guard will restore it.",
						throwable);
			}
		}
	}

	private static void drawProxy(BlackHoleView view) {
		float radius = view.getProxyRadius();
		float left = view.getCenterX() - radius;
		float right = view.getCenterX() + radius;
		float bottom = view.getCenterY() - radius;
		float top = view.getCenterY() + radius;
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0F, 0F);
		GL11.glVertex2f(left, bottom);
		GL11.glTexCoord2f(1F, 0F);
		GL11.glVertex2f(right, bottom);
		GL11.glTexCoord2f(1F, 1F);
		GL11.glVertex2f(right, top);
		GL11.glTexCoord2f(0F, 1F);
		GL11.glVertex2f(left, top);
		GL11.glEnd();
	}

	void dispose() {
		fastProgram.dispose();
		highProgram.dispose();
		shadowBoundary.dispose();
	}

	void abandon() {
		fastProgram.abandon();
		highProgram.abandon();
		shadowBoundary.abandon();
	}
}
