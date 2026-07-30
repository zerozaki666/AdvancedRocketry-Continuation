package zmaster587.advancedRocketry.client.render.atmosphere;

/**
 * Pure Java atmosphere math shared by the CPU LUT generator and the GLSL
 * implementation.  All distances are expressed in kilometres and ray
 * parameters assume a unit direction after the public entry point has
 * normalised it.
 */
public final class AtmosphereMath {

	public static final double GEOMETRY_EPSILON = 1.0e-9D;
	public static final double DIRECTION_EPSILON = 1.0e-12D;
	public static final double MAX_OPTICAL_DEPTH = 50.0D;
	public static final double MIN_MIE_ANISOTROPY = -0.2D;
	public static final double MAX_MIE_ANISOTROPY = 0.95D;

	private static final double INV_FOUR_PI = 1.0D / (4.0D * Math.PI);
	private static final double RAYLEIGH_PHASE_SCALE =
			3.0D / (16.0D * Math.PI);
	private static final double MIE_PHASE_SCALE =
			3.0D / (8.0D * Math.PI);

	private AtmosphereMath() {
	}

	public static boolean isFinite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	public static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	public static double clampFinite(double value, double minimum,
			double maximum, double fallback) {
		return isFinite(value) ? clamp(value, minimum, maximum) : fallback;
	}

	/**
	 * Normalises a vector into {@code out[0..2]}.  Invalid or degenerate input
	 * is rejected instead of manufacturing a direction.
	 */
	public static boolean normalize(double x, double y, double z,
			double[] out) {
		requireOutput(out, 3);
		if(!isFinite(x) || !isFinite(y) || !isFinite(z)) {
			clear(out, 3);
			return false;
		}

		double lengthSquared = x * x + y * y + z * z;
		if(!isFinite(lengthSquared)
				|| lengthSquared <= DIRECTION_EPSILON * DIRECTION_EPSILON) {
			clear(out, 3);
			return false;
		}

		double inverseLength = 1.0D / Math.sqrt(lengthSquared);
		out[0] = x * inverseLength;
		out[1] = y * inverseLength;
		out[2] = z * inverseLength;
		return true;
	}

	/**
	 * Intersects a ray with a sphere centred at the origin.  The supplied
	 * direction is normalised internally, so the returned roots are measured
	 * in kilometres.  Both roots are returned, including negative roots.
	 */
	public static boolean intersectSphere(double originX, double originY,
			double originZ, double directionX, double directionY,
			double directionZ, double radiusKm, double[] outNearFar) {
		requireOutput(outNearFar, 2);
		clear(outNearFar, 2);
		if(!validPosition(originX, originY, originZ)
				|| !isFinite(radiusKm) || radiusKm <= GEOMETRY_EPSILON)
			return false;

		double directionLengthSquared = directionX * directionX
				+ directionY * directionY + directionZ * directionZ;
		if(!validDirection(directionX, directionY, directionZ,
				directionLengthSquared))
			return false;

		double inverseLength = 1.0D / Math.sqrt(directionLengthSquared);
		return intersectSphereUnitDirection(originX, originY, originZ,
				directionX * inverseLength, directionY * inverseLength,
				directionZ * inverseLength, radiusKm, outNearFar);
	}

	/**
	 * Resolves the visible atmosphere segment and clips it against the ground.
	 * Cameras below the ground sphere and rays that enter the ground at the
	 * near plane are rejected.  {@code minimumDistanceKm} can be used as a
	 * near-plane epsilon and must not be negative.
	 */
	public static boolean atmosphereSegment(double originX, double originY,
			double originZ, double directionX, double directionY,
			double directionZ, double groundRadiusKm,
			double atmosphereRadiusKm, double minimumDistanceKm,
			double[] outNearFar) {
		requireOutput(outNearFar, 2);
		clear(outNearFar, 2);
		if(!validPosition(originX, originY, originZ)
				|| !isFinite(groundRadiusKm)
				|| !isFinite(atmosphereRadiusKm)
				|| !isFinite(minimumDistanceKm)
				|| groundRadiusKm <= GEOMETRY_EPSILON
				|| atmosphereRadiusKm <= groundRadiusKm + GEOMETRY_EPSILON
				|| minimumDistanceKm < 0.0D)
			return false;

		double radiusSquared = originX * originX + originY * originY
				+ originZ * originZ;
		if(!isFinite(radiusSquared))
			return false;
		double groundTolerance = scaledEpsilon(groundRadiusKm
				* groundRadiusKm);
		if(radiusSquared < groundRadiusKm * groundRadiusKm - groundTolerance)
			return false;

		double directionLengthSquared = directionX * directionX
				+ directionY * directionY + directionZ * directionZ;
		if(!validDirection(directionX, directionY, directionZ,
				directionLengthSquared))
			return false;

		double inverseLength = 1.0D / Math.sqrt(directionLengthSquared);
		double unitX = directionX * inverseLength;
		double unitY = directionY * inverseLength;
		double unitZ = directionZ * inverseLength;

		if(!intersectSphereUnitDirection(originX, originY, originZ, unitX,
				unitY, unitZ, atmosphereRadiusKm, outNearFar))
			return false;

		double near = Math.max(minimumDistanceKm, outNearFar[0]);
		double far = outNearFar[1];
		if(far <= near + GEOMETRY_EPSILON) {
			clear(outNearFar, 2);
			return false;
		}

		if(intersectSphereUnitDirection(originX, originY, originZ, unitX,
				unitY, unitZ, groundRadiusKm, outNearFar)) {
			double groundHit = firstForwardRoot(outNearFar[0],
					outNearFar[1], minimumDistanceKm);
			if(isFinite(groundHit) && groundHit < far) {
				double hitX = originX + unitX * groundHit;
				double hitY = originY + unitY * groundHit;
				double hitZ = originZ + unitZ * groundHit;
				double radialDerivative = hitX * unitX + hitY * unitY
						+ hitZ * unitZ;
				double hitTolerance = scaledEpsilon(groundRadiusKm);
				if(groundHit > minimumDistanceKm + hitTolerance
						|| radialDerivative < -hitTolerance)
					far = Math.max(minimumDistanceKm, groundHit);
			}
		}

		if(far <= near + GEOMETRY_EPSILON) {
			clear(outNearFar, 2);
			return false;
		}

		outNearFar[0] = near;
		outNearFar[1] = far;
		return true;
	}

	public static double exponentialDensity(double heightKm,
			double scaleHeightKm) {
		if(!isFinite(heightKm) || !isFinite(scaleHeightKm)
				|| heightKm < 0.0D || scaleHeightKm <= GEOMETRY_EPSILON)
			return 0.0D;
		double exponent = clamp(-heightKm / scaleHeightKm,
				-MAX_OPTICAL_DEPTH, 0.0D);
		return Math.exp(exponent);
	}

	public static double absorptionDensity(double heightKm,
			double centerHeightKm, double halfWidthKm) {
		if(!isFinite(heightKm) || !isFinite(centerHeightKm)
				|| !isFinite(halfWidthKm) || heightKm < 0.0D
				|| halfWidthKm <= GEOMETRY_EPSILON)
			return 0.0D;
		return clamp(1.0D - Math.abs(heightKm - centerHeightKm)
				/ halfWidthKm, 0.0D, 1.0D);
	}

	public static double rayleighPhase(double cosine) {
		if(!isFinite(cosine))
			return 0.0D;
		double mu = clamp(cosine, -1.0D, 1.0D);
		return RAYLEIGH_PHASE_SCALE * (1.0D + mu * mu);
	}

	public static double miePhase(double cosine, double anisotropy) {
		if(!isFinite(cosine))
			return 0.0D;
		double mu = clamp(cosine, -1.0D, 1.0D);
		double g = clampFinite(anisotropy, MIN_MIE_ANISOTROPY,
				MAX_MIE_ANISOTROPY, 0.76D);
		double gSquared = g * g;
		double base = Math.max(GEOMETRY_EPSILON,
				1.0D + gSquared - 2.0D * g * mu);
		double denominator = (2.0D + gSquared)
				* base * Math.sqrt(base);
		return MIE_PHASE_SCALE * (1.0D - gSquared)
				* (1.0D + mu * mu) / denominator;
	}

	public static double isotropicPhase() {
		return INV_FOUR_PI;
	}

	public static double transmittance(double opticalDepth) {
		if(Double.isNaN(opticalDepth))
			return 0.0D;
		if(opticalDepth == Double.POSITIVE_INFINITY)
			return 0.0D;
		if(opticalDepth == Double.NEGATIVE_INFINITY)
			return 1.0D;
		double tau = clamp(opticalDepth, 0.0D, MAX_OPTICAL_DEPTH);
		return Math.exp(-tau);
	}

	public static double toneMap(double linearRadiance, double exposure) {
		if(!isFinite(linearRadiance) || !isFinite(exposure)
				|| linearRadiance <= 0.0D || exposure <= 0.0D)
			return 0.0D;
		double argument = clamp(linearRadiance * exposure, 0.0D,
				MAX_OPTICAL_DEPTH);
		return 1.0D - Math.exp(-argument);
	}

	public static double srgbToLinear(double value) {
		if(!isFinite(value))
			return 1.0D;
		double clamped = clamp(value, 0.0D, 4.0D);
		if(clamped <= 0.04045D)
			return clamped / 12.92D;
		return Math.pow((clamped + 0.055D) / 1.055D, 2.4D);
	}

	public static double linearToSrgb(double value) {
		if(!isFinite(value) || value <= 0.0D)
			return 0.0D;
		if(value <= 0.0031308D)
			return 12.92D * value;
		return 1.055D * Math.pow(value, 1.0D / 2.4D) - 0.055D;
	}

	/**
	 * Stable 4x4 Bayer offset expressed in one 8-bit output step.  It depends
	 * only on screen position, so paused views and fixed-time screenshots do
	 * not shimmer.
	 */
	public static double orderedDitherOffset8Bit(int screenX, int screenY) {
		int x = screenX & 3;
		int y = screenY & 3;
		int threshold;
		switch(y * 4 + x) {
			case 0:
				threshold = 0;
				break;
			case 1:
				threshold = 8;
				break;
			case 2:
				threshold = 2;
				break;
			case 3:
				threshold = 10;
				break;
			case 4:
				threshold = 12;
				break;
			case 5:
				threshold = 4;
				break;
			case 6:
				threshold = 14;
				break;
			case 7:
				threshold = 6;
				break;
			case 8:
				threshold = 3;
				break;
			case 9:
				threshold = 11;
				break;
			case 10:
				threshold = 1;
				break;
			case 11:
				threshold = 9;
				break;
			case 12:
				threshold = 15;
				break;
			case 13:
				threshold = 7;
				break;
			case 14:
				threshold = 13;
				break;
			default:
				threshold = 5;
				break;
		}
		return ((threshold + 0.5D) / 16.0D - 0.5D) / 255.0D;
	}

	public static double luminance(double red, double green, double blue) {
		if(!isFinite(red) || !isFinite(green) || !isFinite(blue))
			return 0.0D;
		return Math.max(0.0D, 0.2126D * red + 0.7152D * green
				+ 0.0722D * blue);
	}

	/**
	 * Cosine of the local geometric horizon for an observer outside or on the
	 * ground sphere.
	 */
	public static double horizonCosine(double radiusKm,
			double groundRadiusKm) {
		if(!isFinite(radiusKm) || !isFinite(groundRadiusKm)
				|| groundRadiusKm <= GEOMETRY_EPSILON
				|| radiusKm < groundRadiusKm)
			return 0.0D;
		double ratio = clamp(groundRadiusKm / radiusKm, 0.0D, 1.0D);
		return -Math.sqrt(Math.max(0.0D, 1.0D - ratio * ratio));
	}

	/**
	 * Analytic ground-shadow test.  The light direction convention is
	 * sample-to-star, matching the atmosphere phase functions.
	 */
	public static boolean isGroundShadowed(double sampleX, double sampleY,
			double sampleZ, double lightDirectionX, double lightDirectionY,
			double lightDirectionZ, double groundRadiusKm,
			double angularEpsilon) {
		if(!validPosition(sampleX, sampleY, sampleZ)
				|| !isFinite(groundRadiusKm)
				|| groundRadiusKm <= GEOMETRY_EPSILON
				|| !isFinite(angularEpsilon))
			return true;

		double sampleRadiusSquared = sampleX * sampleX + sampleY * sampleY
				+ sampleZ * sampleZ;
		double directionLengthSquared = lightDirectionX * lightDirectionX
				+ lightDirectionY * lightDirectionY
				+ lightDirectionZ * lightDirectionZ;
		if(!isFinite(sampleRadiusSquared)
				|| sampleRadiusSquared < groundRadiusKm * groundRadiusKm
						- scaledEpsilon(groundRadiusKm * groundRadiusKm)
				|| !validDirection(lightDirectionX, lightDirectionY,
						lightDirectionZ, directionLengthSquared))
			return true;

		double sampleRadius = Math.sqrt(sampleRadiusSquared);
		double inverseDirectionLength = 1.0D
				/ Math.sqrt(directionLengthSquared);
		double cosine = (sampleX * lightDirectionX
				+ sampleY * lightDirectionY + sampleZ * lightDirectionZ)
				* inverseDirectionLength / sampleRadius;
		double horizon = horizonCosine(sampleRadius, groundRadiusKm);
		return cosine <= horizon + Math.max(0.0D, angularEpsilon);
	}

	/**
	 * Writes the midpoint and true interval width for one closest-point-biased
	 * quadrature sample into {@code outSampleAndWidth[0..1]}.  Exactly
	 * {@code totalSamples} indices are valid across both sub-intervals.
	 */
	public static boolean quadratureSample(double nearKm, double farKm,
			double closestKm, int totalSamples, int sampleIndex,
			double[] outSampleAndWidth) {
		requireOutput(outSampleAndWidth, 2);
		clear(outSampleAndWidth, 2);
		if(!isFinite(nearKm) || !isFinite(farKm)
				|| !isFinite(closestKm) || totalSamples <= 0
				|| sampleIndex < 0 || sampleIndex >= totalSamples
				|| farKm <= nearKm + GEOMETRY_EPSILON)
			return false;

		double closest = clamp(closestKm, nearKm, farKm);
		double leftLength = closest - nearKm;
		double rightLength = farKm - closest;
		int leftSamples;
		int rightSamples;
		if(leftLength <= GEOMETRY_EPSILON) {
			leftSamples = 0;
			rightSamples = totalSamples;
		}
		else if(rightLength <= GEOMETRY_EPSILON) {
			leftSamples = totalSamples;
			rightSamples = 0;
		}
		else {
			leftSamples = totalSamples / 2;
			rightSamples = totalSamples - leftSamples;
		}

		double edgeStart;
		double edgeEnd;
		if(sampleIndex < leftSamples) {
			double startFraction = sampleIndex / (double)leftSamples;
			double endFraction = (sampleIndex + 1.0D) / leftSamples;
			edgeStart = nearKm + leftLength
					* (1.0D - square(1.0D - startFraction));
			edgeEnd = nearKm + leftLength
					* (1.0D - square(1.0D - endFraction));
		}
		else {
			int rightIndex = sampleIndex - leftSamples;
			if(rightSamples <= 0)
				return false;
			double startFraction = rightIndex / (double)rightSamples;
			double endFraction = (rightIndex + 1.0D) / rightSamples;
			edgeStart = closest + rightLength * square(startFraction);
			edgeEnd = closest + rightLength * square(endFraction);
		}

		double width = edgeEnd - edgeStart;
		if(!isFinite(width) || width <= 0.0D)
			return false;
		outSampleAndWidth[0] = 0.5D * (edgeStart + edgeEnd);
		outSampleAndWidth[1] = width;
		return true;
	}

	public static double closestApproach(double originX, double originY,
			double originZ, double unitDirectionX, double unitDirectionY,
			double unitDirectionZ, double nearKm, double farKm) {
		if(!validPosition(originX, originY, originZ)
				|| !validPosition(unitDirectionX, unitDirectionY,
						unitDirectionZ)
				|| !isFinite(nearKm) || !isFinite(farKm))
			return nearKm;
		return clamp(-(originX * unitDirectionX
				+ originY * unitDirectionY
				+ originZ * unitDirectionZ), nearKm, farKm);
	}

	private static boolean intersectSphereUnitDirection(double originX,
			double originY, double originZ, double unitDirectionX,
			double unitDirectionY, double unitDirectionZ, double radiusKm,
			double[] outNearFar) {
		double b = originX * unitDirectionX + originY * unitDirectionY
				+ originZ * unitDirectionZ;
		double c = originX * originX + originY * originY
				+ originZ * originZ - radiusKm * radiusKm;
		double discriminant = b * b - c;
		double tolerance = scaledEpsilon(Math.max(Math.abs(b * b),
				Math.abs(c)));
		if(discriminant < -tolerance)
			return false;
		discriminant = Math.max(0.0D, discriminant);
		double squareRoot = Math.sqrt(discriminant);
		outNearFar[0] = -b - squareRoot;
		outNearFar[1] = -b + squareRoot;
		return isFinite(outNearFar[0]) && isFinite(outNearFar[1]);
	}

	private static double firstForwardRoot(double first, double second,
			double minimumDistance) {
		double tolerance = scaledEpsilon(Math.max(Math.abs(first),
				Math.abs(second)));
		if(first >= minimumDistance - tolerance)
			return Math.max(minimumDistance, first);
		if(second >= minimumDistance - tolerance)
			return Math.max(minimumDistance, second);
		return Double.NaN;
	}

	private static boolean validPosition(double x, double y, double z) {
		return isFinite(x) && isFinite(y) && isFinite(z);
	}

	private static boolean validDirection(double x, double y, double z,
			double lengthSquared) {
		return validPosition(x, y, z) && isFinite(lengthSquared)
				&& lengthSquared
						> DIRECTION_EPSILON * DIRECTION_EPSILON;
	}

	private static double scaledEpsilon(double scale) {
		return GEOMETRY_EPSILON * Math.max(1.0D, Math.abs(scale));
	}

	private static double square(double value) {
		return value * value;
	}

	private static void requireOutput(double[] output, int requiredLength) {
		if(output == null || output.length < requiredLength)
			throw new IllegalArgumentException("Output array must contain at least "
					+ requiredLength + " elements");
	}

	private static void clear(double[] output, int length) {
		for(int i = 0; i < length; i++)
			output[i] = 0.0D;
	}
}
