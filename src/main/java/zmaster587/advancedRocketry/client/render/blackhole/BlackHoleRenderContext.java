package zmaster587.advancedRocketry.client.render.blackhole;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;

/**
 * Immutable state shared by all black holes submitted to one celestial pass.
 */
public final class BlackHoleRenderContext {

	private final Minecraft minecraft;
	private final long worldTotalTime;
	private final float partialTicks;
	private final double animationTime;
	private final long frameSerial;
	private final int viewportX;
	private final int viewportY;
	private final int viewportWidth;
	private final int viewportHeight;

	private BlackHoleRenderContext(Minecraft minecraft, long worldTotalTime,
			float partialTicks, long frameSerial, int viewportX, int viewportY,
			int viewportWidth, int viewportHeight) {
		this.minecraft = minecraft;
		this.worldTotalTime = worldTotalTime;
		this.partialTicks = sanitizePartialTicks(partialTicks);
		this.animationTime = worldTotalTime + this.partialTicks;
		this.frameSerial = frameSerial;
		this.viewportX = viewportX;
		this.viewportY = viewportY;
		this.viewportWidth = viewportWidth;
		this.viewportHeight = viewportHeight;
	}

	static BlackHoleRenderContext capture(Minecraft minecraft,
			long worldTotalTime, float partialTicks, long frameSerial) {
		IntBuffer viewport = BufferUtils.createIntBuffer(4);
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
		return new BlackHoleRenderContext(minecraft, worldTotalTime,
				partialTicks, frameSerial, viewport.get(0), viewport.get(1),
				Math.max(0, viewport.get(2)), Math.max(0, viewport.get(3)));
	}

	private static float sanitizePartialTicks(float value) {
		if(Float.isNaN(value) || Float.isInfinite(value))
			return 0F;
		return Math.max(0F, Math.min(1F, value));
	}

	public Minecraft getMinecraft() {
		return minecraft;
	}

	public long getWorldTotalTime() {
		return worldTotalTime;
	}

	public float getPartialTicks() {
		return partialTicks;
	}

	/**
	 * The only animation clock used by this renderer.
	 */
	public double getAnimationTime() {
		return animationTime;
	}

	public long getFrameSerial() {
		return frameSerial;
	}

	public int getViewportX() {
		return viewportX;
	}

	public int getViewportY() {
		return viewportY;
	}

	public int getViewportWidth() {
		return viewportWidth;
	}

	public int getViewportHeight() {
		return viewportHeight;
	}

	public boolean hasDrawableViewport() {
		return viewportWidth > 0 && viewportHeight > 0;
	}
}
