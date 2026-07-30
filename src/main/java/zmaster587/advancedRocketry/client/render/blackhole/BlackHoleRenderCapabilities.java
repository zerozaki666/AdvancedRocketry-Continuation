package zmaster587.advancedRocketry.client.render.blackhole;

import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Render-thread snapshot of the OpenGL features used by the Kerr
 * approximation.  No optional-mod classes are referenced here.
 */
public final class BlackHoleRenderCapabilities {

	// Shared by core, ARB and EXT framebuffer implementations.
	private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
	private static final int GL_SAMPLES = 0x80A9;

	private final boolean contextAvailable;
	private final boolean openGl20;
	private final boolean framebufferObjects;
	private final boolean coreFramebufferObjects;
	private final boolean arbFramebufferObjects;
	private final int textureImageUnits;
	private final int maximumTextureSize;
	private final int currentProgram;
	private final int currentFramebuffer;
	private final int sampleCount;

	private BlackHoleRenderCapabilities(boolean contextAvailable,
			boolean openGl20, boolean framebufferObjects,
			boolean coreFramebufferObjects, boolean arbFramebufferObjects,
			int textureImageUnits, int maximumTextureSize, int currentProgram,
			int currentFramebuffer, int sampleCount) {
		this.contextAvailable = contextAvailable;
		this.openGl20 = openGl20;
		this.framebufferObjects = framebufferObjects;
		this.coreFramebufferObjects = coreFramebufferObjects;
		this.arbFramebufferObjects = arbFramebufferObjects;
		this.textureImageUnits = textureImageUnits;
		this.maximumTextureSize = maximumTextureSize;
		this.currentProgram = currentProgram;
		this.currentFramebuffer = currentFramebuffer;
		this.sampleCount = sampleCount;
	}

	public static BlackHoleRenderCapabilities detect() {
		if(!Display.isCreated()) {
			return new BlackHoleRenderCapabilities(false, false, false,
					false, false, 0, 0, 0, 0, 0);
		}

		try {
			ContextCapabilities capabilities = GLContext.getCapabilities();
			boolean coreFbo = capabilities.OpenGL30;
			boolean arbFbo = capabilities.GL_ARB_framebuffer_object;
			boolean extFbo = capabilities.GL_EXT_framebuffer_object;
			boolean fbo = coreFbo || arbFbo || extFbo;
			boolean gl20 = capabilities.OpenGL20;
			int units = gl20
					? GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) : 0;
			int textureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
			int program = gl20
					? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
			int framebuffer = fbo
					? GL11.glGetInteger(GL_FRAMEBUFFER_BINDING) : 0;
			int samples = GL11.glGetInteger(GL_SAMPLES);
			return new BlackHoleRenderCapabilities(true, gl20, fbo,
					coreFbo, arbFbo, units, textureSize, program,
					framebuffer, Math.max(0, samples));
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("capability-query",
					"OpenGL capability query failed; using legacy visuals.",
					throwable);
			return new BlackHoleRenderCapabilities(false, false, false,
					false, false, 0, 0, 0, 0, 0);
		}
	}

	public boolean isContextAvailable() {
		return contextAvailable;
	}

	public boolean supportsKerrApproximation() {
		return contextAvailable && openGl20 && textureImageUnits >= 2;
	}

	public boolean supportsShaderPrograms() {
		return contextAvailable && openGl20;
	}

	/**
	 * AUTO deliberately fails closed around an already-bound shader or an
	 * unresolved multisample target.  Forced FAST/HIGH modes may replace and
	 * restore an external program, but never attempt an implicit MSAA resolve.
	 */
	public boolean isAutomaticModeSafe() {
		return supportsKerrApproximation() && currentProgram == 0
				&& sampleCount == 0;
	}

	public boolean canCopySceneColor() {
		return contextAvailable && sampleCount == 0
				&& maximumTextureSize > 0;
	}

	public int getMaximumTextureSize() {
		return maximumTextureSize;
	}

	public int getCurrentProgram() {
		return currentProgram;
	}

	public int getCurrentFramebuffer() {
		return currentFramebuffer;
	}

	public int getSampleCount() {
		return sampleCount;
	}

	public boolean hasFramebufferObjects() {
		return framebufferObjects;
	}

	void bindFramebuffer(int framebuffer) {
		if(!framebufferObjects)
			return;
		if(coreFramebufferObjects)
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
		else if(arbFramebufferObjects)
			ARBFramebufferObject.glBindFramebuffer(
					ARBFramebufferObject.GL_FRAMEBUFFER, framebuffer);
		else
			EXTFramebufferObject.glBindFramebufferEXT(
					EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer);
	}
}
