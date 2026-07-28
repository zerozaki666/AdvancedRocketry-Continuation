package zmaster587.advancedRocketry.client.render.blackhole;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * Captures the GL state that is not completely covered by glPushAttrib, most
 * importantly shader program, FBO, active texture units and viewport.
 */
final class BlackHoleGlState {

	private final BlackHoleRenderCapabilities capabilities;
	private final int program;
	private final int framebuffer;
	private final int activeTexture;
	private final int unit0Texture;
	private final int unit1Texture;
	private final boolean unit0TextureEnabled;
	private final boolean unit1TextureEnabled;
	private final int[] viewport = new int[4];
	private final int matrixMode;
	private final boolean depthMask;
	private final float[] color = new float[4];
	private boolean serverAttributesPushed;
	private boolean clientAttributesPushed;
	private boolean restored;

	private BlackHoleGlState(BlackHoleRenderCapabilities capabilities) {
		this.capabilities = capabilities;
		program = capabilities.supportsShaderPrograms()
				? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
		framebuffer = capabilities.getCurrentFramebuffer();
		activeTexture = capabilities.supportsKerrApproximation()
				? GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
				: GL13.GL_TEXTURE0;

		if(capabilities.supportsKerrApproximation()) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			unit0Texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			unit0TextureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
			GL13.glActiveTexture(GL13.GL_TEXTURE1);
			unit1Texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			unit1TextureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
			GL13.glActiveTexture(activeTexture);
		}
		else {
			unit0Texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			unit1Texture = 0;
			unit0TextureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
			unit1TextureEnabled = false;
		}

		IntBuffer viewportBuffer = BufferUtils.createIntBuffer(4);
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
		for(int index = 0; index < viewport.length; index++)
			viewport[index] = viewportBuffer.get(index);
		matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
		depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(4);
		GL11.glGetFloat(GL11.GL_CURRENT_COLOR, colorBuffer);
		for(int index = 0; index < color.length; index++)
			color[index] = colorBuffer.get(index);

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		serverAttributesPushed = true;
		GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
		clientAttributesPushed = true;
	}

	static BlackHoleGlState capture(
			BlackHoleRenderCapabilities capabilities) {
		return new BlackHoleGlState(capabilities);
	}

	void restore() {
		if(restored)
			return;
		restored = true;
		try {
			if(clientAttributesPushed) {
				GL11.glPopClientAttrib();
				clientAttributesPushed = false;
			}
			if(serverAttributesPushed) {
				GL11.glPopAttrib();
				serverAttributesPushed = false;
			}

			if(capabilities.supportsKerrApproximation()) {
				GL13.glActiveTexture(GL13.GL_TEXTURE0);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, unit0Texture);
				setEnabled(GL11.GL_TEXTURE_2D, unit0TextureEnabled);
				GL13.glActiveTexture(GL13.GL_TEXTURE1);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, unit1Texture);
				setEnabled(GL11.GL_TEXTURE_2D, unit1TextureEnabled);
				GL13.glActiveTexture(activeTexture);
			}
			else {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, unit0Texture);
				setEnabled(GL11.GL_TEXTURE_2D, unit0TextureEnabled);
			}
			if(capabilities.supportsShaderPrograms())
				GL20.glUseProgram(program);

			if(capabilities.hasFramebufferObjects())
				capabilities.bindFramebuffer(framebuffer);
			GL11.glViewport(viewport[0], viewport[1], viewport[2],
					viewport[3]);
			GL11.glDepthMask(depthMask);
			GL11.glColor4f(color[0], color[1], color[2], color[3]);
			GL11.glMatrixMode(matrixMode);
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("state-restore",
					"Could not completely restore OpenGL state after a black-hole pass.",
					throwable);
		}
	}

	private static void setEnabled(int capability, boolean enabled) {
		if(enabled)
			GL11.glEnable(capability);
		else
			GL11.glDisable(capability);
	}
}
