package zmaster587.advancedRocketry.client.render.planet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StationPlanetRotationMathTest {

	private static final double SURFACE_PERIOD_TICKS = 50000D;
	private static final double EPSILON = 1.0e-9D;

	@Test
	public void longLivedSaveStillInterpolatesEveryFrame() {
		long oldSaveTime = 1L << 28;
		double start = StationPlanetRotationMath.getRotationDegrees(
				oldSaveTime, 0F, SURFACE_PERIOD_TICKS, 1D);
		double halfwayThroughTick =
				StationPlanetRotationMath.getRotationDegrees(
						oldSaveTime, 0.5F, SURFACE_PERIOD_TICKS, 1D);

		assertEquals(0.5D*360D/SURFACE_PERIOD_TICKS,
				forwardAngle(start, halfwayThroughTick), EPSILON);
		assertTrue(halfwayThroughTick != start);
	}

	@Test
	public void longLivedSaveStillAdvancesOneTickAtATime() {
		long oldSaveTime = 1L << 28;
		double first = StationPlanetRotationMath.getRotationDegrees(
				oldSaveTime, 0F, SURFACE_PERIOD_TICKS, 1D);
		double next = StationPlanetRotationMath.getRotationDegrees(
				oldSaveTime+1L, 0F, SURFACE_PERIOD_TICKS, 1D);

		assertEquals(360D/SURFACE_PERIOD_TICKS,
				forwardAngle(first, next), EPSILON);
	}

	@Test
	public void multiplierKeepsConfiguredPeriodAndStopBehavior() {
		assertEquals(180D, StationPlanetRotationMath.getRotationDegrees(
				25000L, 0F, SURFACE_PERIOD_TICKS, 1D), EPSILON);
		assertEquals(0D, StationPlanetRotationMath.getRotationDegrees(
				25000L, 0.5F, SURFACE_PERIOD_TICKS, 0D), EPSILON);
	}

	private static double forwardAngle(double start, double end) {
		double difference = end-start;
		return difference < 0D ? difference+360D : difference;
	}
}
