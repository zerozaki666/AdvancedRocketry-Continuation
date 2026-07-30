package zmaster587.advancedRocketry.client.render.planet;

/**
 * Allocation-free direction math shared by the sky geometry and atmosphere
 * integration points.  Directions use the sample-to-primary-light
 * convention.
 */
final class CelestialRenderDirection {

	private static final double DEGREES_TO_RADIANS = Math.PI/180D;
	private static final double CYCLES_TO_RADIANS = Math.PI*2D;
	private static final double MINIMUM_LENGTH_SQUARED = 0.0000000001D;

	private CelestialRenderDirection() {
	}

	/**
	 * Reproduces the transform used by {@link RenderPlanetarySky}: first the
	 * celestial rotation about the planet axis, then the two Y rotations
	 * {@code rotationalPhi} and {@code -90 degrees}.
	 */
	static boolean resolvePlanet(float celestialAngle, float axisX,
			float axisY, float axisZ, double rotationalPhiDegrees,
			float[] output) {
		if(output == null || output.length < 3)
			return false;
		double axisLengthSquared = (double)axisX*axisX
				+(double)axisY*axisY+(double)axisZ*axisZ;
		if(!finite(axisLengthSquared)
				|| axisLengthSquared < MINIMUM_LENGTH_SQUARED)
			return clear(output);

		double inverseAxisLength = 1D/Math.sqrt(axisLengthSquared);
		double x = axisX*inverseAxisLength;
		double y = axisY*inverseAxisLength;
		double z = axisZ*inverseAxisLength;
		double angle = celestialAngle*CYCLES_TO_RADIANS;
		double sine = Math.sin(angle);
		double cosine = Math.cos(angle);
		double oneMinusCosine = 1D-cosine;

		// Rodrigues rotation of the untransformed sun center (0, 1, 0).
		double lightX = -z*sine+x*y*oneMinusCosine;
		double lightY = cosine+y*y*oneMinusCosine;
		double lightZ = x*sine+z*y*oneMinusCosine;
		return rotateYAndStore(lightX, lightY, lightZ,
				(rotationalPhiDegrees-90D)*DEGREES_TO_RADIANS, output);
	}

	/**
	 * Reproduces {@link RenderStationSpaceSky#rotateAroundAxis()}: station
	 * EAST rotation is applied to the source first, followed by station UP,
	 * {@code rotationalPhi}, and the fixed -90 degree sky rotation.
	 */
	static boolean resolveStation(double rotationalPhiDegrees,
			double upRotationCycles, double eastRotationCycles,
			float[] output) {
		if(output == null || output.length < 3
				|| !finite(upRotationCycles)
				|| !finite(eastRotationCycles))
			return output != null && output.length >= 3 && clear(output);

		double eastAngle = eastRotationCycles*CYCLES_TO_RADIANS;
		double lightX = 0D;
		double lightY = Math.cos(eastAngle);
		double lightZ = Math.sin(eastAngle);
		double yAngle = upRotationCycles*CYCLES_TO_RADIANS
				+(rotationalPhiDegrees-90D)*DEGREES_TO_RADIANS;
		return rotateYAndStore(lightX, lightY, lightZ, yAngle, output);
	}

	/**
	 * Converts the primary direction into the local coordinates of a body
	 * drawn after {@code glRotate(phi, Z); glRotate(theta, X)}.  Passing this
	 * vector to OpenGL lighting under those same matrices reconstructs the
	 * exact primary-sun direction instead of applying the body transform
	 * twice.
	 */
	static boolean resolveBodyLocal(double orbitalPhiDegrees,
			double orbitalThetaRadians, float[] output) {
		if(output == null || output.length < 3
				|| !finite(orbitalPhiDegrees)
				|| !finite(orbitalThetaRadians))
			return output != null && output.length >= 3 && clear(output);

		double phi = -orbitalPhiDegrees*DEGREES_TO_RADIANS;
		double phiSine = Math.sin(phi);
		double phiCosine = Math.cos(phi);
		double lightX = -phiSine;
		double lightY = phiCosine;

		double theta = -orbitalThetaRadians;
		double thetaSine = Math.sin(theta);
		double thetaCosine = Math.cos(theta);
		double rotatedY = thetaCosine*lightY;
		double lightZ = thetaSine*lightY;
		return normalizeAndStore(lightX, rotatedY, lightZ, output);
	}

	private static boolean rotateYAndStore(double x, double y, double z,
			double angle, float[] output) {
		if(!finite(angle))
			return clear(output);
		double sine = Math.sin(angle);
		double cosine = Math.cos(angle);
		return normalizeAndStore(cosine*x+sine*z, y,
				-sine*x+cosine*z, output);
	}

	private static boolean normalizeAndStore(double x, double y, double z,
			float[] output) {
		double lengthSquared = x*x+y*y+z*z;
		if(!finite(lengthSquared)
				|| lengthSquared < MINIMUM_LENGTH_SQUARED)
			return clear(output);
		double inverseLength = 1D/Math.sqrt(lengthSquared);
		output[0] = (float)(x*inverseLength);
		output[1] = (float)(y*inverseLength);
		output[2] = (float)(z*inverseLength);
		if(!finite(output[0]) || !finite(output[1])
				|| !finite(output[2]))
			return clear(output);
		return true;
	}

	private static boolean clear(float[] output) {
		output[0] = 0F;
		output[1] = 0F;
		output[2] = 0F;
		return false;
	}

	private static boolean finite(float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	private static boolean finite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}
}
