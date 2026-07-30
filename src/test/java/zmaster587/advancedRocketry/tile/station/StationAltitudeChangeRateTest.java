package zmaster587.advancedRocketry.tile.station;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StationAltitudeChangeRateTest {

	private static final double EPSILON = 1.0e-12D;

	@Test
	public void defaultProgressPreservesLegacyStepExactly() {
		double legacyStep = 0.0875D;

		assertEquals(StationAltitudeChangeRate.MINIMUM_MULTIPLIER,
				StationAltitudeChangeRate.getMultiplier(0), EPSILON);
		assertEquals(legacyStep,
				StationAltitudeChangeRate.scaleLegacyStep(
						legacyStep, 0), EPSILON);
	}

	@Test
	public void finalProgressProvidesTenTimesLegacyStep() {
		double legacyStep = 0.0875D;

		assertEquals(StationAltitudeChangeRate.MAXIMUM_MULTIPLIER,
				StationAltitudeChangeRate.getMultiplier(
						StationAltitudeChangeRate.SLIDER_STEPS),
				EPSILON);
		assertEquals(legacyStep*10.0D,
				StationAltitudeChangeRate.scaleLegacyStep(
						legacyStep,
						StationAltitudeChangeRate.SLIDER_STEPS),
				EPSILON);
	}

	@Test
	public void progressIsClampedToSupportedRange() {
		assertEquals(0,
				StationAltitudeChangeRate.clampSliderProgress(-1));
		assertEquals(StationAltitudeChangeRate.SLIDER_STEPS,
				StationAltitudeChangeRate.clampSliderProgress(100));
		assertEquals(StationAltitudeChangeRate.MINIMUM_MULTIPLIER,
				StationAltitudeChangeRate.getMultiplier(-1), EPSILON);
		assertEquals(StationAltitudeChangeRate.MAXIMUM_MULTIPLIER,
				StationAltitudeChangeRate.getMultiplier(100), EPSILON);
	}

	@Test
	public void eachSliderStepAddsHalfOfLegacyRate() {
		for(int progress = 0;
				progress <= StationAltitudeChangeRate.SLIDER_STEPS;
				progress++)
			assertEquals(1.0D+0.5D*progress,
					StationAltitudeChangeRate.getMultiplier(progress),
					EPSILON);
	}

	@Test
	public void normalAltitudeMatchesLegacyFalloff() {
		int total = 190;
		double orbitalDistance = 21.0D;
		double oldFormula =
				0.1D*(total-orbitalDistance+1.0D)/total;

		assertEquals(oldFormula,
				StationAltitudeChangeRate.getLegacyStep(
						total, orbitalDistance),
				EPSILON);
	}

	@Test
	public void upperTargetRangeKeepsPositiveStep() {
		double expectedMinimum = 0.1D/190.0D;

		assertEquals(expectedMinimum,
				StationAltitudeChangeRate.getLegacyStep(190, 191.0D),
				EPSILON);
		assertEquals(expectedMinimum,
				StationAltitudeChangeRate.getLegacyStep(190, 200.0D),
				EPSILON);
	}

	@Test
	public void legalAltitudeRangeAlwaysHasFinitePositiveStep() {
		for(int orbitalDistance = 10; orbitalDistance <= 200;
				orbitalDistance++) {
			double step = StationAltitudeChangeRate.getLegacyStep(
					190, orbitalDistance);
			assertTrue(Double.isFinite(step));
			assertTrue(step > 0.0D);
		}
	}

	@Test
	public void moveTowardsWorksInBothDirectionsWithoutOvershoot() {
		double legacyStep = 0.08D;

		assertEquals(10.08D,
				StationAltitudeChangeRate.moveTowards(
						10.0D, 20.0D, legacyStep, 0),
				EPSILON);
		assertEquals(19.92D,
				StationAltitudeChangeRate.moveTowards(
						20.0D, 10.0D, legacyStep, 0),
				EPSILON);
		assertEquals(10.03D,
				StationAltitudeChangeRate.moveTowards(
						10.0D, 10.03D, legacyStep, 0),
				EPSILON);
		assertEquals(19.97D,
				StationAltitudeChangeRate.moveTowards(
						20.0D, 19.97D, legacyStep, 0),
				EPSILON);
	}
}
