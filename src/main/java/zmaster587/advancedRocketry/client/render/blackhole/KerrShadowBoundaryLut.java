package zmaster587.advancedRocketry.client.render.blackhole;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * Generates and caches the observer-dependent apparent shadow boundary.
 *
 * <p>The sampled curve is a CPU-side Kerr critical-curve approximation in
 * units where G=c=M=1.  It is recentered, resampled at 256 polar angles and
 * uploaded as a normalized 256x1 luminance texture.  It describes the
 * apparent shadow, not a flattened event horizon.</p>
 */
final class KerrShadowBoundaryLut {

	static final int SAMPLE_COUNT = 256;
	private static final int COARSE_RADIAL_SAMPLES = 4096;
	private static final int RADIAL_SAMPLES = 4096;
	private static final int MAX_CACHE_ENTRIES = 48;
	private static final double TWO_PI = Math.PI*2D;
	private static final double SQRT_27 = Math.sqrt(27D);
	private static final double MIN_SPIN = 1.0E-4D;
	private static final double MIN_INCLINATION_DEGREES = 1D;

	private final LinkedHashMap<Key, Entry> entries =
			new LinkedHashMap<Key, Entry>(16, 0.75F, true);

	Entry get(float spin, float inclinationRadians) {
		int quantizedSpin = (int)Math.round(
				clamp(spin, 0D, 0.998D)*256D);
		int inclinationDegrees = (int)Math.round(Math.toDegrees(
				clamp(inclinationRadians, 0D, Math.PI)));
		// The critical curve is mirror-symmetric for i and pi-i.
		inclinationDegrees = Math.min(inclinationDegrees,
				180 - inclinationDegrees);
		Key key = new Key(quantizedSpin, inclinationDegrees);
		Entry cached = entries.get(key);
		if(cached != null && cached.isTextureValid())
			return cached;
		if(cached != null)
			entries.remove(key);

		float[] values = generate(quantizedSpin/256D,
				Math.toRadians(inclinationDegrees));
		if(values == null) {
			BlackHoleDiagnostics.warnOnce("shadow-lut-generate",
					"Could not generate a complete Kerr shadow boundary; using legacy visuals.");
			return null;
		}
		int texture = upload(values);
		if(texture == 0)
			return null;
		Entry result = new Entry(texture, values);
		entries.put(key, result);
		trimCache();
		return result;
	}

	/**
	 * Package-visible for deterministic mathematical tests.
	 */
	static float[] generate(double requestedSpin,
			double requestedInclinationRadians) {
		double spin = clamp(requestedSpin, 0D, 0.998D);
		double originalInclination = clamp(requestedInclinationRadians,
				0D, Math.PI/2D);
		if(spin < MIN_SPIN)
			return circle();

		double samplingInclination = Math.max(
				Math.toRadians(MIN_INCLINATION_DEGREES),
				originalInclination);
		double progradeRadius = 2D*(1D + Math.cos((2D/3D)
				*Math.acos(-spin)));
		double retrogradeRadius = 2D*(1D + Math.cos((2D/3D)
				*Math.acos(spin)));
		double epsilon = Math.max(1.0E-6D,
				(retrogradeRadius - progradeRadius)*1.0E-5D);
		double start = progradeRadius + epsilon;
		double end = retrogradeRadius - epsilon;
		if(!isFinite(start) || !isFinite(end) || end <= start)
			return null;

		double sin = Math.sin(samplingInclination);
		double cos = Math.cos(samplingInclination);
		double cot = cos/sin;
		// For nearly face-on observers the valid spherical-photon-orbit
		// interval is a very small subset of the equatorial prograde/retrograde
		// range.  Locate that interval first, then spend the fixed sampling
		// budget inside it instead of losing almost every sample.
		double firstValidRadius = Double.NaN;
		double lastValidRadius = Double.NaN;
		double coarseStep = (end - start)/(COARSE_RADIAL_SAMPLES - 1D);
		for(int index = 0; index < COARSE_RADIAL_SAMPLES; index++) {
			double radius = start + coarseStep*index;
			if(calculateCriticalPoint(spin, sin, cos, cot, radius) != null) {
				if(Double.isNaN(firstValidRadius))
					firstValidRadius = radius;
				lastValidRadius = radius;
			}
		}
		if(Double.isNaN(firstValidRadius) || Double.isNaN(lastValidRadius))
			return null;
		double refinedStart = Math.max(start,
				firstValidRadius - coarseStep*2D);
		double refinedEnd = Math.min(end,
				lastValidRadius + coarseStep*2D);
		if(refinedEnd <= refinedStart)
			return null;

		List<Point> points = new ArrayList<Point>(RADIAL_SAMPLES*2);
		for(int index = 0; index < RADIAL_SAMPLES; index++) {
			double fraction = index/(double)(RADIAL_SAMPLES - 1);
			double radius = refinedStart
					+ (refinedEnd - refinedStart)*fraction;
			Point upper = calculateCriticalPoint(spin, sin, cos, cot,
					radius);
			if(upper == null)
				continue;
			points.add(upper);
			if(upper.y > 1.0E-8D)
				points.add(new Point(upper.x, -upper.y));
		}
		if(points.size() < SAMPLE_COUNT)
			return null;

		double centroidX = 0D;
		double centroidY = 0D;
		for(Point point : points) {
			centroidX += point.x;
			centroidY += point.y;
		}
		centroidX /= points.size();
		centroidY /= points.size();

		List<PolarPoint> polar = new ArrayList<PolarPoint>(points.size());
		for(Point point : points) {
			double x = point.x - centroidX;
			double y = point.y - centroidY;
			double angle = Math.atan2(y, x);
			if(angle < 0D)
				angle += TWO_PI;
			double radius = Math.sqrt(x*x + y*y);
			if(isFinite(angle) && isFinite(radius) && radius > 0D)
				polar.add(new PolarPoint(angle, radius));
		}
		if(polar.size() < SAMPLE_COUNT)
			return null;
		Collections.sort(polar, PolarPoint.ANGLE_ORDER);

		double maximumGap = 0D;
		for(int index = 0; index < polar.size(); index++) {
			PolarPoint first = polar.get(index);
			PolarPoint second = polar.get((index + 1)%polar.size());
			double secondAngle = index + 1 == polar.size()
					? second.angle + TWO_PI : second.angle;
			maximumGap = Math.max(maximumGap, secondAngle - first.angle);
		}
		if(maximumGap > Math.toRadians(12D))
			return null;

		float[] result = new float[SAMPLE_COUNT];
		int cursor = 0;
		double sum = 0D;
		for(int index = 0; index < SAMPLE_COUNT; index++) {
			double target = TWO_PI*index/SAMPLE_COUNT;
			while(cursor + 1 < polar.size()
					&& polar.get(cursor + 1).angle < target)
				cursor++;
			PolarPoint lower = polar.get(cursor);
			PolarPoint upper;
			double lowerAngle = lower.angle;
			double upperAngle;
			if(target < polar.get(0).angle) {
				lower = polar.get(polar.size() - 1);
				upper = polar.get(0);
				lowerAngle = lower.angle - TWO_PI;
				upperAngle = upper.angle;
			}
			else if(cursor + 1 >= polar.size()) {
				upper = polar.get(0);
				upperAngle = upper.angle + TWO_PI;
			}
			else {
				upper = polar.get(cursor + 1);
				upperAngle = upper.angle;
			}
			double denominator = upperAngle - lowerAngle;
			double blend = denominator <= 1.0E-10D ? 0D
					: clamp((target - lowerAngle)/denominator, 0D, 1D);
			double radius = lower.radius
					+ (upper.radius - lower.radius)*blend;
			result[index] = (float)clamp(radius/SQRT_27, 0.45D, 1.55D);
			sum += result[index];
		}

		if(originalInclination < Math.toRadians(2D)) {
			float mean = (float)(sum/SAMPLE_COUNT);
			float fullCurveWeight = (float)clamp(
					(Math.toDegrees(originalInclination) - 1D), 0D, 1D);
			for(int index = 0; index < result.length; index++)
				result[index] = mean
						+ (result[index] - mean)*fullCurveWeight;
		}

		for(float value : result) {
			if(!isFinite(value) || value <= 0F)
				return null;
		}
		return result;
	}

	private static Point calculateCriticalPoint(double spin, double sin,
			double cos, double cot, double radius) {
		double oneMinusRadius = 1D - radius;
		double xiDenominator = spin*oneMinusRadius;
		double etaDenominator = spin*spin*oneMinusRadius*oneMinusRadius;
		if(Math.abs(xiDenominator) < 1.0E-10D
				|| Math.abs(etaDenominator) < 1.0E-10D)
			return null;
		double xi = (radius*radius*(radius - 3D)
				+ spin*spin*(radius + 1D))/xiDenominator;
		double eta = radius*radius*radius*(4D*spin*spin
				- radius*(radius - 3D)*(radius - 3D))/etaDenominator;
		double alpha = -xi/sin;
		double betaSquared = eta + spin*spin*cos*cos - xi*xi*cot*cot;
		if(betaSquared < -1.0E-8D || !isFinite(alpha)
				|| !isFinite(betaSquared))
			return null;
		double beta = Math.sqrt(Math.max(0D, betaSquared));
		return isFinite(beta) ? new Point(alpha, beta) : null;
	}

	private static float[] circle() {
		float[] values = new float[SAMPLE_COUNT];
		for(int index = 0; index < values.length; index++)
			values[index] = 1F;
		return values;
	}

	private static int upload(float[] values) {
		int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE1);
		int previousTexture = GL11.glGetInteger(
				GL11.GL_TEXTURE_BINDING_2D);
		int previousUnpackAlignment = GL11.glGetInteger(
				GL11.GL_UNPACK_ALIGNMENT);
		int texture = 0;
		try {
			texture = GL11.glGenTextures();
			if(texture == 0)
				return 0;
			FloatBuffer data = BufferUtils.createFloatBuffer(values.length);
			data.put(values);
			data.flip();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
					GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_LUMINANCE16,
					SAMPLE_COUNT, 1, 0, GL11.GL_LUMINANCE,
					GL11.GL_FLOAT, data);
			if(GL11.glGetError() != GL11.GL_NO_ERROR) {
				GL11.glDeleteTextures(texture);
				return 0;
			}
			return texture;
		}
		catch(Throwable throwable) {
			if(texture != 0)
				GL11.glDeleteTextures(texture);
			BlackHoleDiagnostics.warnOnce("shadow-lut-upload",
					"Could not upload the Kerr shadow boundary; using legacy visuals.",
					throwable);
			return 0;
		}
		finally {
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,
					previousUnpackAlignment);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
			GL13.glActiveTexture(previousActive);
		}
	}

	private void trimCache() {
		while(entries.size() > MAX_CACHE_ENTRIES) {
			Iterator<Map.Entry<Key, Entry>> iterator =
					entries.entrySet().iterator();
			if(!iterator.hasNext())
				return;
			Map.Entry<Key, Entry> eldest = iterator.next();
			eldest.getValue().dispose();
			iterator.remove();
		}
	}

	void dispose() {
		for(Entry entry : entries.values())
			entry.dispose();
		entries.clear();
	}

	void abandon() {
		entries.clear();
	}

	private static boolean isFinite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static double clamp(double value, double minimum,
			double maximum) {
		if(!isFinite(value))
			return minimum;
		return Math.max(minimum, Math.min(maximum, value));
	}

	static final class Entry {
		private int textureId;
		private final float[] values;

		private Entry(int textureId, float[] values) {
			this.textureId = textureId;
			this.values = values;
		}

		int getTextureId() {
			return textureId;
		}

		float[] getValues() {
			return values;
		}

		private boolean isTextureValid() {
			return textureId != 0 && GL11.glIsTexture(textureId);
		}

		private void dispose() {
			if(textureId != 0) {
				if(GL11.glIsTexture(textureId))
					GL11.glDeleteTextures(textureId);
				textureId = 0;
			}
		}
	}

	private static final class Key {
		private final int spin;
		private final int inclination;

		private Key(int spin, int inclination) {
			this.spin = spin;
			this.inclination = inclination;
		}

		@Override
		public int hashCode() {
			return 31*spin + inclination;
		}

		@Override
		public boolean equals(Object object) {
			if(this == object)
				return true;
			if(!(object instanceof Key))
				return false;
			Key other = (Key)object;
			return spin == other.spin && inclination == other.inclination;
		}
	}

	private static final class Point {
		private final double x;
		private final double y;

		private Point(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}

	private static final class PolarPoint {
		private static final Comparator<PolarPoint> ANGLE_ORDER =
				new Comparator<PolarPoint>() {
					@Override
					public int compare(PolarPoint first, PolarPoint second) {
						return Double.compare(first.angle, second.angle);
					}
				};

		private final double angle;
		private final double radius;

		private PolarPoint(double angle, double radius) {
			this.angle = angle;
			this.radius = radius;
		}
	}
}
