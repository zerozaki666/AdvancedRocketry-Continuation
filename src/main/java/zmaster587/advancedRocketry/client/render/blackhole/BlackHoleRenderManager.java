package zmaster587.advancedRocketry.client.render.blackhole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import zmaster587.advancedRocketry.api.BlackHoleRenderMode;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;

/**
 * Shared entry point for all astronomical black-hole rendering.
 *
 * <p>The recommended API is {@link #beginFrame(long, float)}, one or more
 * queue calls, then {@link #renderQueued()}.  Queueing projects geometry while
 * the caller's sky matrices are active; rendering later allows one shared
 * scene-color capture.  Immediate convenience methods are provided for
 * integrations that can only dispatch one body at a time.</p>
 */
public final class BlackHoleRenderManager
		implements IResourceManagerReloadListener {

	public static final BlackHoleRenderManager INSTANCE =
			new BlackHoleRenderManager();

	private static final Comparator<BlackHoleView> DEPTH_ORDER =
			new Comparator<BlackHoleView>() {
				@Override
				public int compare(BlackHoleView first, BlackHoleView second) {
					int depth = Float.compare(second.getDepth(),
							first.getDepth());
					return depth != 0 ? depth
							: first.getBodyId().compareTo(second.getBodyId());
				}
			};
	private static final Comparator<BlackHoleView> SHADER_PRIORITY =
			new Comparator<BlackHoleView>() {
				@Override
				public int compare(BlackHoleView first, BlackHoleView second) {
					int radius = Float.compare(second.getScreenRadius(),
							first.getScreenRadius());
					return radius != 0 ? radius
							: first.getBodyId().compareTo(second.getBodyId());
				}
			};

	private final List<BlackHoleView> queued =
			new ArrayList<BlackHoleView>();
	private final SceneColorCapture sceneCapture = new SceneColorCapture();
	private final KerrBlackHoleRenderer kerrRenderer =
			new KerrBlackHoleRenderer();
	private final FallbackBlackHoleRenderer fallbackRenderer =
			new FallbackBlackHoleRenderer();

	private BlackHoleRenderContext context;
	private WorldClient trackedWorld;
	private IReloadableResourceManager registeredResourceManager;
	private long nextFrameSerial;
	private boolean forgeEventsRegistered;

	private BlackHoleRenderManager() {
	}

	/**
	 * Starts a celestial pass.  Animation is derived exclusively from the
	 * supplied world time and partial tick.
	 */
	public synchronized void beginFrame(long worldTotalTime,
			float partialTicks) {
		Minecraft minecraft = Minecraft.getMinecraft();
		ensureLifecycleHooks(minecraft);
		if(trackedWorld != minecraft.theWorld) {
			disposeGlObjects();
			trackedWorld = minecraft.theWorld;
		}
		queued.clear();
		context = BlackHoleRenderContext.capture(minecraft, worldTotalTime,
				partialTicks, ++nextFrameSerial);
	}

	/**
	 * Queues a black hole on the same local plane used by
	 * RenderPlanetarySky.drawStar: center (0,100,0), X/Z half-size.
	 */
	public synchronized boolean queuePlanetary(StellarBody body,
			float halfSize, float alpha) {
		if(context == null)
			throw new IllegalStateException(
					"beginFrame must be called before queuePlanetary");
		BlackHoleView view = BlackHoleView.projectPlanetary(body, halfSize,
				alpha, context);
		return addIfVisible(view);
	}

	/**
	 * Queues an arbitrary camera-facing free-space billboard in the caller's
	 * current model-view coordinate system.
	 */
	public synchronized boolean queueBillboard(StellarBody body, double x,
			double y, double z, double halfSize, float alpha) {
		if(context == null)
			throw new IllegalStateException(
					"beginFrame must be called before queueBillboard");
		BlackHoleView view = BlackHoleView.projectBillboard(body, x, y, z,
				halfSize, alpha, context);
		return addIfVisible(view);
	}

	private boolean addIfVisible(BlackHoleView view) {
		if(view == null || !view.isVisibleIn(context))
			return false;
		queued.add(view);
		return true;
	}

	/**
	 * Draws all queued views, always falling back to fixed-function visuals
	 * when the optional Kerr path is unavailable.
	 */
	public synchronized void renderQueued() {
		if(context == null)
			return;
		if(queued.isEmpty()) {
			context = null;
			return;
		}

		List<BlackHoleView> renderOrder =
				new ArrayList<BlackHoleView>(queued);
		Collections.sort(renderOrder, DEPTH_ORDER);
		queued.clear();

		BlackHoleRenderCapabilities capabilities =
				BlackHoleRenderCapabilities.detect();
		if(!capabilities.isContextAvailable()) {
			context = null;
			return;
		}
		BlackHoleGlState state = null;
		boolean projectionPushed = false;
		boolean modelViewPushed = false;
		try {
			state = BlackHoleGlState.capture(capabilities);
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPushMatrix();
			projectionPushed = true;
			GL11.glLoadIdentity();
			GL11.glOrtho(context.getViewportX(),
					context.getViewportX() + context.getViewportWidth(),
					context.getViewportY(),
					context.getViewportY() + context.getViewportHeight(),
					-1D, 1D);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glPushMatrix();
			modelViewPushed = true;
			GL11.glLoadIdentity();

			Set<BlackHoleView> shaderViews =
					selectShaderViews(renderOrder, capabilities);
			boolean captured = !shaderViews.isEmpty()
					&& sceneCapture.capture(context, capabilities);
			for(BlackHoleView view : renderOrder) {
				boolean icon = view.getScreenRadius() < 2F;
				boolean renderedByShader = false;
				if(captured && shaderViews.contains(view)) {
					BlackHoleRenderMode mode =
							Configuration.blackHoleRenderMode;
					boolean high = mode == BlackHoleRenderMode.HIGH;
					int steps = high
							? Configuration.blackHoleShaderStepsHigh
							: Configuration.blackHoleShaderStepsFast;
					renderedByShader = kerrRenderer.render(view, context,
							sceneCapture, high, steps);
					if(!renderedByShader)
						BlackHoleDiagnostics.warnOnce("shader-runtime-fallback",
								"A Kerr draw failed; legacy visuals remain active.");
				}
				if(!renderedByShader) {
					if(capabilities.supportsShaderPrograms())
						GL20.glUseProgram(0);
					if(capabilities.supportsKerrApproximation())
						GL13.glActiveTexture(GL13.GL_TEXTURE0);
					renderFallbackSafely(view, icon);
				}
			}
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("render-pass",
					"Black-hole celestial pass failed; later frames will use legacy visuals.",
					throwable);
		}
		finally {
			try {
				if(modelViewPushed) {
					GL11.glMatrixMode(GL11.GL_MODELVIEW);
					GL11.glPopMatrix();
				}
				if(projectionPushed) {
					GL11.glMatrixMode(GL11.GL_PROJECTION);
					GL11.glPopMatrix();
				}
			}
			catch(Throwable throwable) {
				BlackHoleDiagnostics.warnOnce("matrix-restore",
						"Could not completely restore black-hole pass matrices.",
						throwable);
			}
			finally {
				if(state != null)
					state.restore();
				context = null;
			}
		}
	}

	private void renderFallbackSafely(BlackHoleView view, boolean icon) {
		try {
			fallbackRenderer.render(view, context, icon);
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("fallback-render",
					"A legacy black-hole draw failed; OpenGL state will still be restored.",
					throwable);
		}
	}

	private Set<BlackHoleView> selectShaderViews(
			List<BlackHoleView> candidates,
			BlackHoleRenderCapabilities capabilities) {
		BlackHoleRenderMode mode = Configuration.blackHoleRenderMode;
		if(mode == null)
			mode = BlackHoleRenderMode.AUTO;
		if(!Configuration.advancedVFX || mode == BlackHoleRenderMode.LEGACY)
			return Collections.emptySet();
		if(!capabilities.supportsKerrApproximation()) {
			BlackHoleDiagnostics.warnOnce("missing-glsl20",
					"OpenGL 2.0 with two fragment texture units is unavailable; using legacy visuals.");
			return Collections.emptySet();
		}
		if(mode == BlackHoleRenderMode.AUTO
				&& !capabilities.isAutomaticModeSafe()) {
			if(capabilities.getCurrentProgram() != 0)
				BlackHoleDiagnostics.warnOnce("external-program",
						"An external shader program is active; AUTO uses legacy black holes.");
			if(capabilities.getSampleCount() > 0)
				BlackHoleDiagnostics.warnOnce("multisample-target",
						"The celestial target is multisampled; AUTO will not guess a resolve path.");
			return Collections.emptySet();
		}
		if(capabilities.getSampleCount() > 0)
			return Collections.emptySet();

		int minimumRadius = Math.max(2,
				Configuration.blackHoleShaderMinScreenRadius);
		List<BlackHoleView> eligible = new ArrayList<BlackHoleView>();
		for(BlackHoleView view : candidates) {
			if(view.getScreenRadius() >= minimumRadius)
				eligible.add(view);
		}
		Collections.sort(eligible, SHADER_PRIORITY);
		int budget = Math.max(0, Configuration.blackHoleMaxShaderBodies);
		Set<BlackHoleView> selected = new HashSet<BlackHoleView>();
		for(int index = 0; index < eligible.size() && index < budget; index++)
			selected.add(eligible.get(index));
		return selected;
	}

	/**
	 * Immediate convenience path for one body on the planetary-sky plane.
	 */
	public synchronized boolean renderPlanetary(StellarBody body,
			float halfSize, float alpha, long worldTotalTime,
			float partialTicks) {
		beginFrame(worldTotalTime, partialTicks);
		boolean queuedBody = queuePlanetary(body, halfSize, alpha);
		renderQueued();
		return queuedBody;
	}

	/**
	 * Immediate convenience path for one arbitrary free-space billboard.
	 */
	public synchronized boolean renderBillboard(StellarBody body, double x,
			double y, double z, double halfSize, float alpha,
			long worldTotalTime, float partialTicks) {
		beginFrame(worldTotalTime, partialTicks);
		boolean queuedBody = queueBillboard(body, x, y, z, halfSize, alpha);
		renderQueued();
		return queuedBody;
	}

	/**
	 * Optional eager hook for ClientProxy.  Calls are idempotent; beginFrame
	 * also installs the listener lazily, so integration cannot omit reload
	 * handling accidentally.
	 */
	public synchronized void registerResourceReloadListener() {
		ensureLifecycleHooks(Minecraft.getMinecraft());
	}

	private void ensureLifecycleHooks(Minecraft minecraft) {
		if(!forgeEventsRegistered) {
			MinecraftForge.EVENT_BUS.register(this);
			forgeEventsRegistered = true;
		}
		IResourceManager resourceManager = minecraft.getResourceManager();
		if(resourceManager instanceof IReloadableResourceManager
				&& resourceManager != registeredResourceManager) {
			registeredResourceManager =
					(IReloadableResourceManager)resourceManager;
			registeredResourceManager.registerReloadListener(this);
		}
	}

	@Override
	public synchronized void onResourceManagerReload(
			IResourceManager resourceManager) {
		disposeGlObjects();
	}

	@SubscribeEvent
	public synchronized void onWorldUnload(WorldEvent.Unload event) {
		if(event.world != null && event.world.isRemote
				&& event.world == trackedWorld) {
			disposeGlObjects();
			trackedWorld = null;
			context = null;
			queued.clear();
		}
	}

	/**
	 * Releases every GL object owned by the subsystem.  Safe to call on
	 * shutdown, world changes and resource reload.
	 */
	public synchronized void dispose() {
		disposeGlObjects();
		context = null;
		queued.clear();
		trackedWorld = null;
	}

	private void disposeGlObjects() {
		if(!Display.isCreated()) {
			sceneCapture.abandon();
			kerrRenderer.abandon();
			return;
		}
		try {
			sceneCapture.dispose();
			kerrRenderer.dispose();
		}
		catch(Throwable throwable) {
			BlackHoleDiagnostics.warnOnce("dispose",
					"Could not dispose all black-hole GL objects.", throwable);
		}
	}
}
