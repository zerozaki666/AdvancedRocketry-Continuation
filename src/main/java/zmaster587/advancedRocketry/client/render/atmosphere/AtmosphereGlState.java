package zmaster587.advancedRocketry.client.render.atmosphere;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * Captures compatibility state plus the shader/FBO/texture values omitted by
 * {@code glPushAttrib}.  Restore is idempotent and is always called in a
 * pass-level finally block.
 */
final class AtmosphereGlState {

	private AtmosphereRenderCapabilities capabilities;
	private int program;
	private int framebuffer;
	private int activeTexture;
	private int unit0Texture;
	private boolean unit0TextureEnabled;
	private final int[] viewport = new int[4];
	private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(4);
	private int matrixMode;
	private boolean depthMask;
	private final float[] color = new float[4];
	private final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(4);
	private boolean serverAttributesPushed;
	private boolean clientAttributesPushed;
	private boolean projectionPushed;
	private boolean modelViewPushed;
	private boolean restored = true;

	AtmosphereGlState() {
	}

	AtmosphereGlState capture(
			AtmosphereRenderCapabilities capabilities) {
		if(capabilities == null)
			throw new IllegalArgumentException(
					"Atmosphere capabilities cannot be null");
		if(!restored || serverAttributesPushed || clientAttributesPushed
				|| projectionPushed || modelViewPushed)
			throw new IllegalStateException(
					"Atmosphere GL state capture is already active");
		this.capabilities = capabilities;
		program = capabilities.supportsShaderPrograms()
				? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
		framebuffer = capabilities.getCurrentFramebuffer();
		activeTexture = capabilities.supportsShaderPrograms()
				? GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
				: GL13.GL_TEXTURE0;

		int capturedUnit0Texture;
		boolean capturedUnit0TextureEnabled;
		if(capabilities.supportsShaderPrograms()) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			try {
				capturedUnit0Texture =
						GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
				capturedUnit0TextureEnabled =
						GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
			}
			finally {
				GL13.glActiveTexture(activeTexture);
			}
		}
		else {
			capturedUnit0Texture =
					GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			capturedUnit0TextureEnabled =
					GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
		}
		unit0Texture = capturedUnit0Texture;
		unit0TextureEnabled = capturedUnit0TextureEnabled;

		viewportBuffer.clear();
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
		for(int index = 0; index < viewport.length; index++)
			viewport[index] = viewportBuffer.get(index);
		matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
		depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		colorBuffer.clear();
		GL11.glGetFloat(GL11.GL_CURRENT_COLOR, colorBuffer);
		for(int index = 0; index < color.length; index++)
			color[index] = colorBuffer.get(index);

		restored = false;
		try {
			GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
			serverAttributesPushed = true;
			GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
			clientAttributesPushed = true;
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPushMatrix();
			projectionPushed = true;
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glPushMatrix();
			modelViewPushed = true;
			GL11.glMatrixMode(matrixMode);
		}
		catch(Throwable throwable) {
			restore();
			if(throwable instanceof RuntimeException)
				throw (RuntimeException)throwable;
			if(throwable instanceof Error)
				throw (Error)throwable;
			throw new IllegalStateException(
					"Could not capture atmosphere matrices", throwable);
		}
		return this;
	}

	void restore() {
		if(restored)
			return;
		restored = true;
		restoreMatrices();
		if(clientAttributesPushed) {
			try {
				GL11.glPopClientAttrib();
				clientAttributesPushed = false;
			}
			catch(Throwable throwable) {
				warnRestore("client-attrib", throwable);
			}
		}
		if(serverAttributesPushed) {
			try {
				GL11.glPopAttrib();
				serverAttributesPushed = false;
			}
			catch(Throwable throwable) {
				warnRestore("server-attrib", throwable);
			}
		}

		if(capabilities.supportsShaderPrograms()) {
			try {
				GL13.glActiveTexture(GL13.GL_TEXTURE0);
				try {
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, unit0Texture);
					setEnabled(GL11.GL_TEXTURE_2D,
							unit0TextureEnabled);
				}
				finally {
					GL13.glActiveTexture(activeTexture);
				}
			}
			catch(Throwable throwable) {
				warnRestore("texture", throwable);
			}
			try {
				GL20.glUseProgram(program);
			}
			catch(Throwable throwable) {
				warnRestore("program", throwable);
			}
		}
		else {
			try {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, unit0Texture);
				setEnabled(GL11.GL_TEXTURE_2D, unit0TextureEnabled);
			}
			catch(Throwable throwable) {
				warnRestore("texture", throwable);
			}
		}

		if(capabilities.hasFramebufferObjects()) {
			try {
				capabilities.bindFramebuffer(framebuffer);
			}
			catch(Throwable throwable) {
				warnRestore("framebuffer", throwable);
			}
		}
		try {
			GL11.glViewport(viewport[0], viewport[1], viewport[2],
					viewport[3]);
			GL11.glDepthMask(depthMask);
			GL11.glColor4f(color[0], color[1], color[2], color[3]);
			GL11.glMatrixMode(matrixMode);
		}
		catch(Throwable throwable) {
			warnRestore("fixed-function", throwable);
		}
	}

	private void restoreMatrices() {
		if(modelViewPushed) {
			try {
				GL11.glMatrixMode(GL11.GL_MODELVIEW);
				GL11.glPopMatrix();
				modelViewPushed = false;
			}
			catch(Throwable throwable) {
				warnRestore("model-view-matrix", throwable);
			}
		}
		if(projectionPushed) {
			try {
				GL11.glMatrixMode(GL11.GL_PROJECTION);
				GL11.glPopMatrix();
				projectionPushed = false;
			}
			catch(Throwable throwable) {
				warnRestore("projection-matrix", throwable);
			}
		}
		try {
			GL11.glMatrixMode(matrixMode);
		}
		catch(Throwable throwable) {
			warnRestore("matrix-mode", throwable);
		}
	}

	private static void warnRestore(String part, Throwable throwable) {
		AtmosphereDiagnostics.warnOnce("state-restore-" + part,
				"Could not restore atmosphere OpenGL " + part + " state.",
				throwable);
	}

	private static void setEnabled(int capability, boolean enabled) {
		if(enabled)
			GL11.glEnable(capability);
		else
			GL11.glDisable(capability);
	}
}
