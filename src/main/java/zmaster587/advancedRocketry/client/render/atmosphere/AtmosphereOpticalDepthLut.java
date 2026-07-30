package zmaster587.advancedRocketry.client.render.atmosphere;

import java.nio.ShortBuffer;

/**
 * Immutable CPU representation of the optical-depth lookup texture.  This
 * class performs no OpenGL work; callers may generate it on a worker and copy
 * the encoded RGBA16 payload into an upload buffer on the render thread.
 */
public final class AtmosphereOpticalDepthLut {

	public static final int DEFAULT_WIDTH = 128;
	public static final int DEFAULT_HEIGHT = 64;
	public static final int DEFAULT_INTEGRATION_SAMPLES = 64;
	public static final int CHANNEL_RAYLEIGH = 0;
	public static final int CHANNEL_MIE = 1;
	public static final int CHANNEL_ABSORPTION = 2;
	public static final int CHANNEL_RESERVED = 3;
	public static final int CHANNEL_COUNT = 4;
	public static final int MAX_UNSIGNED_SHORT = 0xffff;

	private static final double ENCODE_SCALE_MARGIN = 1.001D;

	private final Key key;
	private final int width;
	private final int height;
	private final int integrationSamples;
	private final char[] encodedRgba16;
	private final double rayleighDecodeScale;
	private final double mieDecodeScale;
	private final double absorptionDecodeScale;

	private AtmosphereOpticalDepthLut(Key key, int width, int height,
			int integrationSamples, char[] encodedRgba16,
			double rayleighDecodeScale, double mieDecodeScale,
			double absorptionDecodeScale) {
		this.key = key;
		this.width = width;
		this.height = height;
		this.integrationSamples = integrationSamples;
		this.encodedRgba16 = encodedRgba16;
		this.rayleighDecodeScale = rayleighDecodeScale;
		this.mieDecodeScale = mieDecodeScale;
		this.absorptionDecodeScale = absorptionDecodeScale;
	}

	public static AtmosphereOpticalDepthLut generate(Key key) {
		return generate(key, DEFAULT_WIDTH, DEFAULT_HEIGHT,
				DEFAULT_INTEGRATION_SAMPLES);
	}

	/**
	 * Generates the three density columns using the same closest-point-biased
	 * quadrature prescribed for the fixed shader loops.  The sample count is a
	 * total per ray, not a per-half count.
	 */
	public static AtmosphereOpticalDepthLut generate(Key key, int width,
			int height, int integrationSamples) {
		if(key == null)
			throw new IllegalArgumentException("LUT key cannot be null");
		int texelCount = checkedTexelCount(width, height);
		if(integrationSamples <= 0 || integrationSamples > 4096)
			throw new IllegalArgumentException(
					"Integration samples must be in the range 1..4096");

		double[] columns = new double[texelCount * 3];
		double[] sphereRoots = new double[2];
		double[] sampleAndWidth = new double[2];
		double maximumRayleigh = 0.0D;
		double maximumMie = 0.0D;
		double maximumAbsorption = 0.0D;

		double atmosphereRadius = key.groundRadiusKm
				+ key.atmosphereHeightKm;
		for(int y = 0; y < height; y++) {
			double v = (y + 0.5D) / height;
			double q = v * v;
			for(int x = 0; x < width; x++) {
				double u = (x + 0.5D) / width;
				double altitude = key.atmosphereHeightKm * u * u;
				double radius = key.groundRadiusKm + altitude;
				double horizonCosine = AtmosphereMath.horizonCosine(radius,
						key.groundRadiusKm);
				double lightCosine = horizonCosine
						+ (1.0D - horizonCosine) * q;
				lightCosine = AtmosphereMath.clamp(lightCosine,
						-1.0D, 1.0D);
				double tangentDirection = Math.sqrt(Math.max(0.0D,
						1.0D - lightCosine * lightCosine));

				if(!AtmosphereMath.intersectSphere(0.0D, radius, 0.0D,
						tangentDirection, lightCosine, 0.0D,
						atmosphereRadius, sphereRoots))
					continue;
				double near = 0.0D;
				double far = sphereRoots[1];
				if(!AtmosphereMath.isFinite(far)
						|| far <= AtmosphereMath.GEOMETRY_EPSILON)
					continue;
				double closest = AtmosphereMath.clamp(-radius
						* lightCosine, near, far);

				double rayleighColumn = 0.0D;
				double mieColumn = 0.0D;
				double absorptionColumn = 0.0D;
				for(int sampleIndex = 0; sampleIndex < integrationSamples;
						sampleIndex++) {
					if(!AtmosphereMath.quadratureSample(near, far, closest,
							integrationSamples, sampleIndex,
							sampleAndWidth))
						continue;
					double distance = sampleAndWidth[0];
					double radialDistanceSquared = radius * radius
							+ 2.0D * radius * lightCosine * distance
							+ distance * distance;
					double sampleRadius = Math.sqrt(Math.max(0.0D,
							radialDistanceSquared));
					double sampleAltitude = sampleRadius
							- key.groundRadiusKm;
					double intervalWidth = sampleAndWidth[1];
					rayleighColumn += AtmosphereMath.exponentialDensity(
							sampleAltitude, key.rayleighScaleHeightKm)
							* intervalWidth;
					mieColumn += AtmosphereMath.exponentialDensity(
							sampleAltitude, key.mieScaleHeightKm)
							* intervalWidth;
					absorptionColumn += AtmosphereMath.absorptionDensity(
							sampleAltitude, key.absorptionCenterKm,
							key.absorptionWidthKm) * intervalWidth;
				}

				int columnIndex = (y * width + x) * 3;
				columns[columnIndex] = finiteNonNegative(rayleighColumn);
				columns[columnIndex + 1] = finiteNonNegative(mieColumn);
				columns[columnIndex + 2] =
						finiteNonNegative(absorptionColumn);
				maximumRayleigh = Math.max(maximumRayleigh,
						columns[columnIndex]);
				maximumMie = Math.max(maximumMie,
						columns[columnIndex + 1]);
				maximumAbsorption = Math.max(maximumAbsorption,
						columns[columnIndex + 2]);
			}
		}

		double rayleighScale = decodeScale(maximumRayleigh);
		double mieScale = decodeScale(maximumMie);
		double absorptionScale = decodeScale(maximumAbsorption);
		char[] encoded = new char[texelCount * CHANNEL_COUNT];
		for(int texel = 0; texel < texelCount; texel++) {
			int columnIndex = texel * 3;
			int encodedIndex = texel * CHANNEL_COUNT;
			encoded[encodedIndex + CHANNEL_RAYLEIGH] = encode(
					columns[columnIndex], rayleighScale);
			encoded[encodedIndex + CHANNEL_MIE] = encode(
					columns[columnIndex + 1], mieScale);
			encoded[encodedIndex + CHANNEL_ABSORPTION] = encode(
					columns[columnIndex + 2], absorptionScale);
			encoded[encodedIndex + CHANNEL_RESERVED] = 0;
		}

		return new AtmosphereOpticalDepthLut(key, width, height,
				integrationSamples, encoded, rayleighScale, mieScale,
				absorptionScale);
	}

	public Key getKey() {
		return key;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getIntegrationSamples() {
		return integrationSamples;
	}

	public int getTexelCount() {
		return width * height;
	}

	public int getEncodedElementCount() {
		return encodedRgba16.length;
	}

	public int getEncodedUnsigned16(int texelIndex, int channel) {
		if(texelIndex < 0 || texelIndex >= getTexelCount())
			throw new IndexOutOfBoundsException("Texel index: " + texelIndex);
		checkChannel(channel);
		return encodedRgba16[texelIndex * CHANNEL_COUNT + channel];
	}

	public double getEncodedNormalized(int texelIndex, int channel) {
		return getEncodedUnsigned16(texelIndex, channel)
				/ (double)MAX_UNSIGNED_SHORT;
	}

	public double getDecodeScale(int channel) {
		switch(channel) {
			case CHANNEL_RAYLEIGH:
				return rayleighDecodeScale;
			case CHANNEL_MIE:
				return mieDecodeScale;
			case CHANNEL_ABSORPTION:
				return absorptionDecodeScale;
			case CHANNEL_RESERVED:
				return 1.0D;
			default:
				throw new IndexOutOfBoundsException("LUT channel: "
						+ channel);
		}
	}

	public double getRayleighDecodeScale() {
		return rayleighDecodeScale;
	}

	public double getMieDecodeScale() {
		return mieDecodeScale;
	}

	public double getAbsorptionDecodeScale() {
		return absorptionDecodeScale;
	}

	public double getDecodedColumnDensity(int texelIndex, int channel) {
		return getEncodedNormalized(texelIndex, channel)
				* getDecodeScale(channel);
	}

	/**
	 * Appends the immutable unsigned-short payload to a caller-owned upload
	 * buffer.  Java shorts preserve the raw 16 bits expected by
	 * {@code GL_UNSIGNED_SHORT}.
	 */
	public void putEncodedRgba16(ShortBuffer target) {
		if(target == null || target.remaining() < encodedRgba16.length)
			throw new IllegalArgumentException(
					"Target buffer does not have enough remaining elements");
		for(char value : encodedRgba16)
			target.put((short)value);
	}

	/**
	 * Maps altitude and local-up/light cosine to texture coordinates clamped
	 * to texel centres.  Ground-blocked directions return false before any LUT
	 * lookup, preventing horizon bilinear light leaks.
	 */
	public boolean mapToTextureCoordinates(double altitudeKm,
			double lightCosine, double[] outUv) {
		requireOutput(outUv, 2);
		outUv[0] = 0.0D;
		outUv[1] = 0.0D;
		if(!AtmosphereMath.isFinite(altitudeKm)
				|| !AtmosphereMath.isFinite(lightCosine))
			return false;

		double altitude = AtmosphereMath.clamp(altitudeKm, 0.0D,
				key.atmosphereHeightKm);
		double radius = key.groundRadiusKm + altitude;
		double horizon = AtmosphereMath.horizonCosine(radius,
				key.groundRadiusKm);
		double angularEpsilon = getLutAngularEpsilon(altitude);
		if(lightCosine <= horizon + angularEpsilon)
			return false;

		double u = Math.sqrt(AtmosphereMath.clamp(altitude
				/ key.atmosphereHeightKm, 0.0D, 1.0D));
		double q = AtmosphereMath.clamp((lightCosine - horizon)
				/ Math.max(1.0D - horizon,
						AtmosphereMath.GEOMETRY_EPSILON),
				0.0D, 1.0D);
		double v = Math.sqrt(q);
		outUv[0] = clampToTexelCenters(u, width);
		outUv[1] = clampToTexelCenters(v, height);
		return true;
	}

	public double getLutAngularEpsilon(double altitudeKm) {
		double altitude = AtmosphereMath.clampFinite(altitudeKm, 0.0D,
				key.atmosphereHeightKm, 0.0D);
		double horizon = AtmosphereMath.horizonCosine(
				key.groundRadiusKm + altitude, key.groundRadiusKm);
		double firstCenter = 0.5D / height;
		return (1.0D - horizon) * firstCenter * firstCenter;
	}

	/**
	 * CPU bilinear sampling helper for tests and non-GL consumers.  The
	 * returned columns are decoded physical path lengths in kilometres.
	 */
	public boolean sampleColumns(double altitudeKm, double lightCosine,
			double[] outRayleighMieAbsorption) {
		requireOutput(outRayleighMieAbsorption, 3);
		outRayleighMieAbsorption[0] = 0.0D;
		outRayleighMieAbsorption[1] = 0.0D;
		outRayleighMieAbsorption[2] = 0.0D;
		if(!mapToTextureCoordinates(altitudeKm, lightCosine,
				outRayleighMieAbsorption))
			return false;

		double u = outRayleighMieAbsorption[0];
		double v = outRayleighMieAbsorption[1];
		outRayleighMieAbsorption[0] = 0.0D;
		outRayleighMieAbsorption[1] = 0.0D;
		double x = u * width - 0.5D;
		double y = v * height - 0.5D;
		int x0 = clampIndex((int)Math.floor(x), width);
		int y0 = clampIndex((int)Math.floor(y), height);
		int x1 = clampIndex(x0 + 1, width);
		int y1 = clampIndex(y0 + 1, height);
		double xFraction = AtmosphereMath.clamp(x - Math.floor(x),
				0.0D, 1.0D);
		double yFraction = AtmosphereMath.clamp(y - Math.floor(y),
				0.0D, 1.0D);

		for(int channel = 0; channel < 3; channel++) {
			double lower = mix(getDecodedColumnDensity(y0 * width + x0,
					channel), getDecodedColumnDensity(y0 * width + x1,
					channel), xFraction);
			double upper = mix(getDecodedColumnDensity(y1 * width + x0,
					channel), getDecodedColumnDensity(y1 * width + x1,
					channel), xFraction);
			outRayleighMieAbsorption[channel] = mix(lower, upper,
					yFraction);
		}
		return true;
	}

	private static int checkedTexelCount(int width, int height) {
		if(width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"LUT dimensions must be positive");
		long texelCount = (long)width * height;
		if(texelCount > Integer.MAX_VALUE / CHANNEL_COUNT)
			throw new IllegalArgumentException("LUT dimensions are too large");
		return (int)texelCount;
	}

	private static double finiteNonNegative(double value) {
		return AtmosphereMath.isFinite(value) && value > 0.0D
				? value : 0.0D;
	}

	private static double decodeScale(double maximum) {
		if(!AtmosphereMath.isFinite(maximum)
				|| maximum <= AtmosphereMath.GEOMETRY_EPSILON)
			return 1.0D;
		return maximum * ENCODE_SCALE_MARGIN;
	}

	private static char encode(double value, double scale) {
		double normalised = AtmosphereMath.clamp(value
				/ Math.max(scale, AtmosphereMath.GEOMETRY_EPSILON),
				0.0D, 1.0D);
		return (char)Math.round(normalised * MAX_UNSIGNED_SHORT);
	}

	private static double clampToTexelCenters(double coordinate, int size) {
		double halfTexel = 0.5D / size;
		return AtmosphereMath.clamp(coordinate, halfTexel,
				1.0D - halfTexel);
	}

	private static int clampIndex(int index, int size) {
		return Math.max(0, Math.min(size - 1, index));
	}

	private static double mix(double first, double second, double amount) {
		return first + (second - first) * amount;
	}

	private static void checkChannel(int channel) {
		if(channel < 0 || channel >= CHANNEL_COUNT)
			throw new IndexOutOfBoundsException("LUT channel: " + channel);
	}

	private static void requireOutput(double[] output, int length) {
		if(output == null || output.length < length)
			throw new IllegalArgumentException("Output array must contain at least "
					+ length + " elements");
	}

	/**
	 * Immutable density-geometry cache key.  Coefficients, pressure, colours,
	 * stellar intensity and exposure deliberately do not participate.
	 */
	public static final class Key {
		private final double groundRadiusKm;
		private final double atmosphereHeightKm;
		private final double rayleighScaleHeightKm;
		private final double mieScaleHeightKm;
		private final double absorptionCenterKm;
		private final double absorptionWidthKm;
		private final int hashCode;

		public Key(double groundRadiusKm, double atmosphereHeightKm,
				double rayleighScaleHeightKm, double mieScaleHeightKm,
				double absorptionCenterKm, double absorptionWidthKm) {
			if(!inRange(groundRadiusKm, 100.0D, 200000.0D)
					|| !inRange(atmosphereHeightKm, 1.0D,
							Math.min(10000.0D, groundRadiusKm * 0.5D))
					|| !inRange(rayleighScaleHeightKm, 0.1D,
							atmosphereHeightKm)
					|| !inRange(mieScaleHeightKm, 0.05D,
							atmosphereHeightKm)
					|| !inRange(absorptionCenterKm, 0.0D,
							atmosphereHeightKm)
					|| !inRange(absorptionWidthKm, 0.1D,
							atmosphereHeightKm))
				throw new IllegalArgumentException(
						"Invalid optical-depth LUT key");
			this.groundRadiusKm = groundRadiusKm;
			this.atmosphereHeightKm = atmosphereHeightKm;
			this.rayleighScaleHeightKm = rayleighScaleHeightKm;
			this.mieScaleHeightKm = mieScaleHeightKm;
			this.absorptionCenterKm = absorptionCenterKm;
			this.absorptionWidthKm = absorptionWidthKm;
			hashCode = computeHashCode();
		}

		public double getGroundRadiusKm() {
			return groundRadiusKm;
		}

		public double getAtmosphereHeightKm() {
			return atmosphereHeightKm;
		}

		public double getAtmosphereRadiusKm() {
			return groundRadiusKm + atmosphereHeightKm;
		}

		public double getRayleighScaleHeightKm() {
			return rayleighScaleHeightKm;
		}

		public double getMieScaleHeightKm() {
			return mieScaleHeightKm;
		}

		public double getAbsorptionCenterKm() {
			return absorptionCenterKm;
		}

		public double getAbsorptionWidthKm() {
			return absorptionWidthKm;
		}

		@Override
		public boolean equals(Object other) {
			if(this == other)
				return true;
			if(!(other instanceof Key))
				return false;
			Key key = (Key)other;
			return same(groundRadiusKm, key.groundRadiusKm)
					&& same(atmosphereHeightKm, key.atmosphereHeightKm)
					&& same(rayleighScaleHeightKm,
							key.rayleighScaleHeightKm)
					&& same(mieScaleHeightKm, key.mieScaleHeightKm)
					&& same(absorptionCenterKm, key.absorptionCenterKm)
					&& same(absorptionWidthKm, key.absorptionWidthKm);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public String toString() {
			return "AtmosphereLutKey{groundRadiusKm=" + groundRadiusKm
					+ ", atmosphereHeightKm=" + atmosphereHeightKm
					+ ", rayleighScaleHeightKm="
					+ rayleighScaleHeightKm + ", mieScaleHeightKm="
					+ mieScaleHeightKm + ", absorptionCenterKm="
					+ absorptionCenterKm + ", absorptionWidthKm="
					+ absorptionWidthKm + '}';
		}

		private int computeHashCode() {
			int result = appendHash(1, groundRadiusKm);
			result = appendHash(result, atmosphereHeightKm);
			result = appendHash(result, rayleighScaleHeightKm);
			result = appendHash(result, mieScaleHeightKm);
			result = appendHash(result, absorptionCenterKm);
			return appendHash(result, absorptionWidthKm);
		}

		private static int appendHash(int current, double value) {
			long bits = Double.doubleToLongBits(value);
			return 31 * current + (int)(bits ^ bits >>> 32);
		}

		private static boolean same(double first, double second) {
			return Double.doubleToLongBits(first)
					== Double.doubleToLongBits(second);
		}

		private static boolean inRange(double value, double minimum,
				double maximum) {
			return AtmosphereMath.isFinite(value) && value >= minimum
					&& value <= maximum;
		}
	}
}
