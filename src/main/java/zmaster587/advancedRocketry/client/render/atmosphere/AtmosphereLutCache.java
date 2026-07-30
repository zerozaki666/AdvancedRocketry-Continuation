package zmaster587.advancedRocketry.client.render.atmosphere;

import java.nio.ShortBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;

/**
 * Bounded LRU of asynchronously generated optical-depth LUTs.  Worker threads
 * only produce immutable CPU data; texture creation and upload happen on the
 * render thread when {@link #acquire} is called.
 */
final class AtmosphereLutCache {

	private static final int MAX_ENTRIES = 16;
	private static final int INTEGRATION_SAMPLES = 64;

	private final Map<RequestKey, Entry> entries =
			new LinkedHashMap<RequestKey, Entry>(16, 0.75F, true);
	private final RequestKey lookupKey = new RequestKey();
	private final ExecutorService worker =
			Executors.newSingleThreadExecutor(new ThreadFactory() {
				@Override
				public Thread newThread(Runnable runnable) {
					Thread thread = new Thread(runnable,
							"AdvancedRocketry Atmosphere LUT");
					thread.setDaemon(true);
					thread.setPriority(Thread.MIN_PRIORITY);
					return thread;
				}
			});
	private ShortBuffer uploadBuffer;
	private ContextCapabilities contextIdentity;
	private long generation;

	synchronized Binding acquire(AtmosphereVisualProfile profile,
			int width, int height) {
		if(profile == null || !refreshContextIdentity())
			return null;
		lookupKey.set(profile.getOpticalDepthLutKey(), width, height);
		Entry entry = entries.get(lookupKey);
		if(entry == null) {
			RequestKey requestKey = new RequestKey(
					profile.getOpticalDepthLutKey(), width, height);
			entry = new Entry(requestKey, generation);
			entries.put(requestKey, entry);
			submit(entry);
			evictOverflow();
			return null;
		}
		if(entry.textureId != 0) {
			if(isTextureUsable(entry.textureId))
				return entry.binding;
			entry.textureId = 0;
			entry.binding = null;
			entry.cpuData = null;
			entry.failed = false;
			entry.cancelled = false;
			entry.future = null;
			submit(entry);
			return null;
		}
		if(entry.failed || entry.cpuData == null)
			return null;
		if(entry.generation != generation) {
			entries.remove(entry.key);
			return null;
		}
		upload(entry);
		return entry.textureId == 0 ? null : entry.binding;
	}

	private void submit(final Entry entry) {
		entry.future = worker.submit(new Runnable() {
			@Override
			public void run() {
				try {
					AtmosphereOpticalDepthLut result =
							AtmosphereOpticalDepthLut.generate(
									entry.key.geometry,
									entry.key.width, entry.key.height,
									INTEGRATION_SAMPLES);
					synchronized(AtmosphereLutCache.this) {
						if(entry.generation == generation
								&& !entry.cancelled)
							entry.cpuData = result;
					}
				}
				catch(Throwable throwable) {
					synchronized(AtmosphereLutCache.this) {
						entry.failed = true;
					}
					AtmosphereDiagnostics.warnOnce(
							"lut-generate-" + entry.key.hashCode(),
							"Could not generate optical-depth LUT "
							+ entry.key + "; using the continuous fallback.",
							throwable);
				}
			}
		});
	}

	private void upload(Entry entry) {
		if(!Display.isCreated() || entry.cpuData == null)
			return;
		int elementCount = entry.cpuData.getEncodedElementCount();
		if(uploadBuffer == null || uploadBuffer.capacity() < elementCount)
			uploadBuffer = BufferUtils.createShortBuffer(elementCount);
		uploadBuffer.clear();
		entry.cpuData.putEncodedRgba16(uploadBuffer);
		uploadBuffer.flip();

		int texture = 0;
		boolean textureUnitSelected = false;
		try {
			clearGlErrors();
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			textureUnitSelected = true;
			texture = GL11.glGenTextures();
			if(texture == 0)
				throw new IllegalStateException(
						"glGenTextures returned 0");
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16,
					entry.cpuData.getWidth(), entry.cpuData.getHeight(),
					0, GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT,
					uploadBuffer);
			int error = GL11.glGetError();
			if(error != GL11.GL_NO_ERROR)
				throw new IllegalStateException(
						"GL error 0x" + Integer.toHexString(error));
			entry.textureId = texture;
			texture = 0;
			entry.binding = new Binding(entry.textureId,
					entry.cpuData.getWidth(), entry.cpuData.getHeight(),
					(float)entry.cpuData.getRayleighDecodeScale(),
					(float)entry.cpuData.getMieDecodeScale(),
					(float)entry.cpuData.getAbsorptionDecodeScale());
			entry.cpuData = null;
		}
		catch(Throwable throwable) {
			entry.failed = true;
			AtmosphereDiagnostics.warnOnce(
					"lut-upload-" + entry.key.hashCode(),
					"Could not upload GL_RGBA16 optical-depth LUT; using the continuous fallback.",
					throwable);
		}
		finally {
			if(texture != 0)
				GL11.glDeleteTextures(texture);
			if(textureUnitSelected)
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}
	}

	private static void clearGlErrors() {
		for(int index = 0; index < 32; index++) {
			if(GL11.glGetError() == GL11.GL_NO_ERROR)
				return;
		}
	}

	private static boolean isTextureUsable(int textureId) {
		if(textureId == 0 || !Display.isCreated())
			return false;
		try {
			return GL11.glIsTexture(textureId);
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce(
					"lut-texture-query",
					"Could not validate an atmosphere LUT texture; "
							+ "rebuilding it.",
					throwable);
			return false;
		}
	}

	private void evictOverflow() {
		while(entries.size() > MAX_ENTRIES) {
			Iterator<Map.Entry<RequestKey, Entry>> iterator =
					entries.entrySet().iterator();
			if(!iterator.hasNext())
				return;
			Entry entry = iterator.next().getValue();
			iterator.remove();
			disposeEntry(entry);
		}
	}

	synchronized void clear() {
		// If the context changed, refreshContextIdentity abandons the old
		// numeric ids without deleting potentially unrelated objects in the
		// replacement context.
		refreshContextIdentity();
		generation++;
		for(Entry entry : entries.values())
			disposeEntry(entry);
		entries.clear();
		uploadBuffer = null;
	}

	synchronized void abandon() {
		generation++;
		for(Entry entry : entries.values()) {
			entry.cancelled = true;
			if(entry.future != null)
				entry.future.cancel(false);
			entry.textureId = 0;
			entry.binding = null;
			entry.cpuData = null;
		}
		entries.clear();
		uploadBuffer = null;
		contextIdentity = null;
	}

	void shutdown() {
		clear();
		worker.shutdownNow();
	}

	private static void disposeEntry(Entry entry) {
		entry.cancelled = true;
		if(entry.future != null)
			entry.future.cancel(false);
		if(entry.textureId != 0 && Display.isCreated()) {
			try {
				GL11.glDeleteTextures(entry.textureId);
			}
			catch(Throwable throwable) {
				AtmosphereDiagnostics.warnOnce("lut-delete",
						"Could not delete an atmosphere LUT texture.",
						throwable);
			}
		}
		entry.textureId = 0;
		entry.binding = null;
		entry.cpuData = null;
	}

	private boolean refreshContextIdentity() {
		if(!Display.isCreated())
			return false;
		try {
			ContextCapabilities current =
					GLContext.getCapabilities();
			if(contextIdentity == current)
				return true;

			generation++;
			for(Entry entry : entries.values()) {
				entry.cancelled = true;
				if(entry.future != null)
					entry.future.cancel(false);
				// The previous context owns this numeric texture id.  Never
				// delete it in the replacement context, where the same
				// integer may already name an unrelated object.
				entry.textureId = 0;
				entry.binding = null;
				entry.cpuData = null;
			}
			entries.clear();
			uploadBuffer = null;
			contextIdentity = current;
			return true;
		}
		catch(Throwable throwable) {
			AtmosphereDiagnostics.warnOnce(
					"lut-context-query",
					"Could not identify the current GL context; "
							+ "using the continuous fallback.",
					throwable);
			return false;
		}
	}

	static final class Binding {
		final int textureId;
		final int width;
		final int height;
		final float rayleighDecodeScale;
		final float mieDecodeScale;
		final float absorptionDecodeScale;

		private Binding(int textureId, int width, int height,
				float rayleighDecodeScale, float mieDecodeScale,
				float absorptionDecodeScale) {
			this.textureId = textureId;
			this.width = width;
			this.height = height;
			this.rayleighDecodeScale = rayleighDecodeScale;
			this.mieDecodeScale = mieDecodeScale;
			this.absorptionDecodeScale = absorptionDecodeScale;
		}
	}

	private static final class Entry {
		final RequestKey key;
		final long generation;
		volatile AtmosphereOpticalDepthLut cpuData;
		Future<?> future;
		Binding binding;
		int textureId;
		boolean failed;
		boolean cancelled;

		private Entry(RequestKey key, long generation) {
			this.key = key;
			this.generation = generation;
		}
	}

	private static final class RequestKey {
		AtmosphereOpticalDepthLut.Key geometry;
		int width;
		int height;
		int hashCode;

		private RequestKey() {
		}

		private RequestKey(AtmosphereOpticalDepthLut.Key geometry,
				int width, int height) {
			set(geometry, width, height);
		}

		private void set(AtmosphereOpticalDepthLut.Key geometry,
				int width, int height) {
			if(geometry == null || width <= 0 || height <= 0)
				throw new IllegalArgumentException("Invalid LUT request");
			this.geometry = geometry;
			this.width = width;
			this.height = height;
			hashCode = 31*(31+geometry.hashCode())+width*31+height;
		}

		@Override
		public boolean equals(Object other) {
			if(this == other)
				return true;
			if(!(other instanceof RequestKey))
				return false;
			RequestKey key = (RequestKey)other;
			return width == key.width && height == key.height
					&& geometry.equals(key.geometry);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public String toString() {
			return geometry + "@" + width + "x" + height;
		}
	}
}
