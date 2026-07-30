package zmaster587.advancedRocketry.client.render.blackhole;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * Owns the texture containing the celestial color buffer immediately before
 * black holes are drawn.  The copy never samples the active color attachment,
 * avoiding read/write feedback.
 */
final class SceneColorCapture {

	private static final int MAX_CAPTURE_DIMENSION = 4096;
	private static final long MAX_CAPTURE_PIXELS = 4096L*2160L;

	private int textureId;
	private int width;
	private int height;
	private long capturedFrameSerial = Long.MIN_VALUE;

	boolean capture(BlackHoleRenderContext context,
			BlackHoleRenderCapabilities capabilities) {
		if(!capabilities.canCopySceneColor()
				|| !context.hasDrawableViewport())
			return false;

		int captureWidth = context.getViewportWidth();
		int captureHeight = context.getViewportHeight();
		int allowedDimension = Math.min(MAX_CAPTURE_DIMENSION,
				capabilities.getMaximumTextureSize());
		long pixels = (long)captureWidth*(long)captureHeight;
		if(captureWidth > allowedDimension || captureHeight > allowedDimension
				|| pixels <= 0L || pixels > MAX_CAPTURE_PIXELS) {
			BlackHoleDiagnostics.warnOnce("capture-size",
					"Celestial viewport " + captureWidth + "x" + captureHeight
					+ " exceeds the safe scene-copy budget; using legacy visuals.");
			return false;
		}

		if(capturedFrameSerial == context.getFrameSerial()
				&& textureId != 0 && width == captureWidth
				&& height == captureHeight)
			return true;

		int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		int previousTexture = GL11.glGetInteger(
				GL11.GL_TEXTURE_BINDING_2D);
		int previousUnpackAlignment = GL11.glGetInteger(
				GL11.GL_UNPACK_ALIGNMENT);
		try {
			if(!ensureTexture(captureWidth, captureHeight))
				return false;
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
					context.getViewportX(), context.getViewportY(),
					captureWidth, captureHeight);
			int error = GL11.glGetError();
			if(error != GL11.GL_NO_ERROR) {
				BlackHoleDiagnostics.warnOnce("capture-gl-" + error,
						"Scene-color copy failed with OpenGL error 0x"
						+ Integer.toHexString(error)
						+ "; using legacy visuals.");
				return false;
			}
			capturedFrameSerial = context.getFrameSerial();
			return true;
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("capture-exception",
					"Scene-color copy failed; using legacy visuals.",
					throwable);
			return false;
		}
		finally {
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,
					previousUnpackAlignment);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
			GL13.glActiveTexture(previousActive);
		}
	}

	private boolean ensureTexture(int requestedWidth, int requestedHeight) {
		if(textureId != 0 && width == requestedWidth
				&& height == requestedHeight && GL11.glIsTexture(textureId))
			return true;

		deleteTexture();
		textureId = GL11.glGenTextures();
		if(textureId == 0)
			return false;
		width = requestedWidth;
		height = requestedHeight;
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
				GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
				width, height, 0, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);
		if(GL11.glGetError() != GL11.GL_NO_ERROR) {
			deleteTexture();
			return false;
		}
		capturedFrameSerial = Long.MIN_VALUE;
		return true;
	}

	int getTextureId() {
		return textureId;
	}

	int getWidth() {
		return width;
	}

	int getHeight() {
		return height;
	}

	void dispose() {
		deleteTexture();
		width = 0;
		height = 0;
		capturedFrameSerial = Long.MIN_VALUE;
	}

	void abandon() {
		textureId = 0;
		width = 0;
		height = 0;
		capturedFrameSerial = Long.MIN_VALUE;
	}

	private void deleteTexture() {
		if(textureId != 0) {
			try {
				if(GL11.glIsTexture(textureId))
					GL11.glDeleteTextures(textureId);
			}
			catch(Throwable throwable) {
				BlackHoleDiagnostics.warnOnce("capture-dispose",
						"Could not dispose the scene-color texture.",
						throwable);
			}
			textureId = 0;
		}
	}
}
