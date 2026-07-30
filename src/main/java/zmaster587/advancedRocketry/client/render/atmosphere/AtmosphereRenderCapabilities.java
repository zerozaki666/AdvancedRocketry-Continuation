package zmaster587.advancedRocketry.client.render.atmosphere;

import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Render-thread snapshot of the compatibility-profile features used by the
 * bounded atmosphere path.  No optional shader-pack classes are referenced.
 */
final class AtmosphereRenderCapabilities {

	private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;

	private boolean contextAvailable;
	private boolean openGl20;
	private boolean framebufferObjects;
	private boolean coreFramebufferObjects;
	private boolean arbFramebufferObjects;
	private int textureImageUnits;
	private int maximumTextureSize;
	private int currentProgram;
	private int currentFramebuffer;

	AtmosphereRenderCapabilities() {
	}

	private void set(boolean contextAvailable,
			boolean openGl20, boolean framebufferObjects,
			boolean coreFramebufferObjects, boolean arbFramebufferObjects,
			int textureImageUnits, int maximumTextureSize, int currentProgram,
			int currentFramebuffer) {
		this.contextAvailable = contextAvailable;
		this.openGl20 = openGl20;
		this.framebufferObjects = framebufferObjects;
		this.coreFramebufferObjects = coreFramebufferObjects;
		this.arbFramebufferObjects = arbFramebufferObjects;
		this.textureImageUnits = textureImageUnits;
		this.maximumTextureSize = maximumTextureSize;
		this.currentProgram = currentProgram;
		this.currentFramebuffer = currentFramebuffer;
	}

	/**
	 * Refreshes this render-thread-owned snapshot in place.  Callers keep
	 * separate instances so an active GL-state capture never observes another
	 * render path refreshing the values it is using for restoration.
	 */
	AtmosphereRenderCapabilities detect() {
		if(!Display.isCreated()) {
			set(false, false, false, false, false, 0, 0, 0, 0);
			return this;
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
			set(true, gl20, fbo, coreFbo, arbFbo, units, textureSize,
					program, framebuffer);
			return this;
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce("capability-query",
					"OpenGL capability detection failed; using the continuous fixed-function fallback.",
					throwable);
			set(false, false, false, false, false, 0, 0, 0, 0);
			return this;
		}
	}

	boolean isContextAvailable() {
		return contextAvailable;
	}

	boolean supportsShaderPrograms() {
		return contextAvailable && openGl20;
	}

	boolean supportsAtmosphereShaders(int lutWidth, int lutHeight) {
		return supportsShaderPrograms() && textureImageUnits >= 1
				&& lutWidth > 0 && lutHeight > 0
				&& lutWidth <= maximumTextureSize
				&& lutHeight <= maximumTextureSize;
	}

	int getCurrentProgram() {
		return currentProgram;
	}

	int getCurrentFramebuffer() {
		return currentFramebuffer;
	}

	boolean hasFramebufferObjects() {
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
