package zmaster587.advancedRocketry.client.render.atmosphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AtmosphereMathTest {

	private static final double EPSILON = 1.0e-8D;

	@Test
	public void intersectsOutsideInsideAndTangentRays() {
		double[] roots = new double[2];
		assertTrue(AtmosphereMath.intersectSphere(
				0D, 0D, -2D, 0D, 0D, 1D, 1D, roots));
		assertEquals(1D, roots[0], EPSILON);
		assertEquals(3D, roots[1], EPSILON);

		assertFalse(AtmosphereMath.intersectSphere(
				0D, 0D, -2D, 0D, 1D, 0D, 1D, roots));
		assertTrue(AtmosphereMath.intersectSphere(
				-2D, 1D, 0D, 1D, 0D, 0D, 1D, roots));
		assertEquals(2D, roots[0], EPSILON);
		assertEquals(2D, roots[1], EPSILON);

		assertTrue(AtmosphereMath.intersectSphere(
				0D, 0D, 0D, 2D, 0D, 0D, 1D, roots));
		assertEquals(-1D, roots[0], EPSILON);
		assertEquals(1D, roots[1], EPSILON);
	}

	@Test
	public void atmosphereSegmentRejectsGroundAndClipsGroundHit() {
		double[] segment = new double[2];
		assertFalse(AtmosphereMath.atmosphereSegment(
				0D, 0D, 0D, 0D, 1D, 0D,
				6360D, 6460D, 0D, segment));

		assertTrue(AtmosphereMath.atmosphereSegment(
				0D, 6370D, 0D, 0D, 1D, 0D,
				6360D, 6460D, 0D, segment));
		assertEquals(0D, segment[0], EPSILON);
		assertEquals(90D, segment[1], EPSILON);

		assertTrue(AtmosphereMath.atmosphereSegment(
				0D, 6370D, 0D, 0D, -1D, 0D,
				6360D, 6460D, 0D, segment));
		assertEquals(10D, segment[1], 1.0e-5D);
	}

	@Test
	public void densityPhaseAndTransferFunctionsRemainFinite() {
		double previous = Double.POSITIVE_INFINITY;
		for(int height = 0; height <= 100; height++) {
			double density = AtmosphereMath.exponentialDensity(height, 8D);
			assertTrue(Double.isFinite(density));
			assertTrue(density <= previous);
			previous = density;
		}

		for(double cosine = -1D; cosine <= 1D; cosine += 0.05D) {
			assertTrue(Double.isFinite(
					AtmosphereMath.rayleighPhase(cosine)));
			assertTrue(Double.isFinite(
					AtmosphereMath.miePhase(cosine, -0.2D)));
			assertTrue(Double.isFinite(
					AtmosphereMath.miePhase(cosine, 0.95D)));
		}

		assertEquals(1D, AtmosphereMath.transmittance(-1D), EPSILON);
		assertEquals(0D, AtmosphereMath.transmittance(
				Double.POSITIVE_INFINITY), EPSILON);
		assertEquals(0D, AtmosphereMath.toneMap(Double.NaN, 1D), EPSILON);
		assertTrue(AtmosphereMath.toneMap(1000D, 1000D) <= 1D);
	}

	@Test
	public void normalizationAndGroundShadowFailDark() {
		double[] direction = new double[3];
		assertTrue(AtmosphereMath.normalize(3D, 4D, 0D, direction));
		assertEquals(1D, Math.sqrt(direction[0]*direction[0]
				+direction[1]*direction[1]
				+direction[2]*direction[2]), EPSILON);
		assertFalse(AtmosphereMath.normalize(0D, 0D, 0D, direction));
		assertFalse(AtmosphereMath.normalize(
				Double.NaN, 0D, 0D, direction));

		assertFalse(AtmosphereMath.isGroundShadowed(
				0D, 6370D, 0D, 0D, 1D, 0D, 6360D, 0D));
		assertTrue(AtmosphereMath.isGroundShadowed(
				0D, 6370D, 0D, 0D, -1D, 0D, 6360D, 0D));
	}
}
