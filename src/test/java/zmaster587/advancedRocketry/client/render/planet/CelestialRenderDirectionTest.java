package zmaster587.advancedRocketry.client.render.planet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CelestialRenderDirectionTest {

	private static final float EPSILON = 0.000001F;

	@Test
	public void planetDirectionMatchesSkyRotationOrder() {
		float[] direction = new float[3];
		assertTrue(CelestialRenderDirection.resolvePlanet(
				0F, 1F, 0F, 0F, 0D, direction));
		assertDirection(direction, 0F, 1F, 0F);

		assertTrue(CelestialRenderDirection.resolvePlanet(
				0.25F, 1F, 0F, 0F, 0D, direction));
		assertDirection(direction, -1F, 0F, 0F);
	}

	@Test
	public void stationDirectionUsesEastThenUpRotations() {
		float[] direction = new float[3];
		assertTrue(CelestialRenderDirection.resolveStation(
				90D, 0D, 0.25D, direction));
		assertDirection(direction, 0F, 0F, 1F);

		assertTrue(CelestialRenderDirection.resolveStation(
				0D, 0D, 0.25D, direction));
		assertDirection(direction, -1F, 0F, 0F);
	}

	@Test
	public void bodyLocalDirectionReconstructsPrimaryDirection() {
		float[] local = new float[3];
		double phiDegrees = 37D;
		double thetaRadians = 0.81D;
		assertTrue(CelestialRenderDirection.resolveBodyLocal(
				phiDegrees, thetaRadians, local));

		double thetaSine = Math.sin(thetaRadians);
		double thetaCosine = Math.cos(thetaRadians);
		double afterThetaX = local[0];
		double afterThetaY = thetaCosine*local[1]
				-thetaSine*local[2];
		double afterThetaZ = thetaSine*local[1]
				+thetaCosine*local[2];
		double phi = Math.toRadians(phiDegrees);
		double resultX = Math.cos(phi)*afterThetaX
				-Math.sin(phi)*afterThetaY;
		double resultY = Math.sin(phi)*afterThetaX
				+Math.cos(phi)*afterThetaY;

		assertEquals(0D, resultX, EPSILON);
		assertEquals(1D, resultY, EPSILON);
		assertEquals(0D, afterThetaZ, EPSILON);
	}

	@Test
	public void invalidInputFailsClosedAndClearsOutput() {
		float[] direction = new float[] {1F, 1F, 1F};
		assertFalse(CelestialRenderDirection.resolvePlanet(
				0F, 0F, 0F, 0F, 0D, direction));
		assertDirection(direction, 0F, 0F, 0F);

		assertFalse(CelestialRenderDirection.resolveStation(
				Double.NaN, 0D, 0D, direction));
		assertDirection(direction, 0F, 0F, 0F);
	}

	private static void assertDirection(float[] direction,
			float expectedX, float expectedY, float expectedZ) {
		assertEquals(expectedX, direction[0], EPSILON);
		assertEquals(expectedY, direction[1], EPSILON);
		assertEquals(expectedZ, direction[2], EPSILON);
	}
}
