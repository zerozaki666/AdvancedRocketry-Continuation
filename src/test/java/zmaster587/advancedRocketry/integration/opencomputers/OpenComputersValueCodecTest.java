package zmaster587.advancedRocketry.integration.opencomputers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import zmaster587.advancedRocketry.tile.station.StationAltitudeChangeRate;

public class OpenComputersValueCodecTest {

	private static final double EPSILON = 1.0e-12D;

	@Test
	public void altitudeBoundariesAndMidpointsUseGuiQuantization() {
		assertEquals(0, OpenComputersValueCodec
				.altitudeProgressFromKm(2100.0D));
		assertEquals(190, OpenComputersValueCodec
				.altitudeProgressFromKm(40100.0D));
		assertEquals(2300, OpenComputersValueCodec
				.altitudeKmFromProgress(OpenComputersValueCodec
						.altitudeProgressFromKm(2200.0D)));
	}

	@Test
	public void altitudeRangeRejectsNonFiniteAndOutOfRangeValues() {
		assertFalse(OpenComputersValueCodec.isFinite(Double.NaN));
		assertFalse(OpenComputersValueCodec
				.isFinite(Double.POSITIVE_INFINITY));
		assertFalse(OpenComputersValueCodec
				.isFinite(Double.NEGATIVE_INFINITY));
		assertFalse(OpenComputersValueCodec.isInRange(2099.0D,
				OpenComputersValueCodec.MINIMUM_ALTITUDE_KM,
				OpenComputersValueCodec.MAXIMUM_ALTITUDE_KM));
		assertFalse(OpenComputersValueCodec.isInRange(40101.0D,
				OpenComputersValueCodec.MINIMUM_ALTITUDE_KM,
				OpenComputersValueCodec.MAXIMUM_ALTITUDE_KM));
		assertTrue(OpenComputersValueCodec.isFinite(2100.0D));
	}

	@Test
	public void changeRateUsesExistingSliderConstants() {
		assertEquals(0, OpenComputersValueCodec
				.changeRateProgressFromMultiplier(1.0D));
		assertEquals(StationAltitudeChangeRate.SLIDER_STEPS,
				OpenComputersValueCodec
						.changeRateProgressFromMultiplier(10.0D));
		assertEquals(1.5D, OpenComputersValueCodec
				.changeRateMultiplierFromProgress(
						OpenComputersValueCodec
								.changeRateProgressFromMultiplier(1.25D)),
				EPSILON);
		assertFalse(OpenComputersValueCodec.isInRange(0.99D,
				StationAltitudeChangeRate.MINIMUM_MULTIPLIER,
				StationAltitudeChangeRate.MAXIMUM_MULTIPLIER));
		assertFalse(OpenComputersValueCodec.isInRange(10.01D,
				StationAltitudeChangeRate.MINIMUM_MULTIPLIER,
				StationAltitudeChangeRate.MAXIMUM_MULTIPLIER));
	}

	@Test
	public void orientationRatesQuantizeMidpointsTowardLargerValue() {
		assertEquals(-60, OpenComputersValueCodec
				.rotationRateFromInput(-60.0D));
		assertEquals(0, OpenComputersValueCodec
				.rotationRateFromInput(0.0D));
		assertEquals(60, OpenComputersValueCodec
				.rotationRateFromInput(60.0D));
		assertEquals(-1, OpenComputersValueCodec
				.rotationRateFromInput(-1.5D));
		assertEquals(2, OpenComputersValueCodec
				.rotationRateFromInput(1.5D));
	}

	@Test
	public void gravityUsesHundredthStepsAndClosedRange() {
		assertEquals(10, OpenComputersValueCodec
				.gravityPercentFromMultiplier(0.10D));
		assertEquals(100, OpenComputersValueCodec
				.gravityPercentFromMultiplier(1.00D));
		assertEquals(0.11D, OpenComputersValueCodec
				.gravityMultiplierFromPercent(OpenComputersValueCodec
						.gravityPercentFromMultiplier(0.105D)), EPSILON);
		assertFalse(OpenComputersValueCodec.isInRange(0.0D,
				OpenComputersValueCodec.MINIMUM_GRAVITY_MULTIPLIER,
				OpenComputersValueCodec.MAXIMUM_GRAVITY_MULTIPLIER));
		assertFalse(OpenComputersValueCodec.isInRange(2.0D,
				OpenComputersValueCodec.MINIMUM_GRAVITY_MULTIPLIER,
				OpenComputersValueCodec.MAXIMUM_GRAVITY_MULTIPLIER));
	}

	@Test
	public void cyclesConvertToRatesAndNormalizedDegrees() {
		assertEquals(180.0D, OpenComputersValueCodec
				.angleDegreesFromRotationCycles(0.5D), EPSILON);
		assertEquals(270.0D, OpenComputersValueCodec
				.angleDegreesFromRotationCycles(-0.25D), EPSILON);
		assertEquals(90.0D, OpenComputersValueCodec
				.angleDegreesFromRotationCycles(1.25D), EPSILON);

		double rotationsPerHour = -17.25D;
		assertEquals(rotationsPerHour, OpenComputersValueCodec
				.rotationsPerHourFromCyclesPerTick(
						OpenComputersValueCodec
								.cyclesPerTickFromRotationsPerHour(
										rotationsPerHour)), EPSILON);
	}

	@Test
	public void allGuiProgressValuesRoundTripExactly() {
		for(int progress = 0; progress <= 190; progress++) {
			int altitudeKm = OpenComputersValueCodec
					.altitudeKmFromProgress(progress);
			assertEquals(progress, OpenComputersValueCodec
					.altitudeProgressFromKm(altitudeKm));
		}
		for(int progress = 0;
				progress <= StationAltitudeChangeRate.SLIDER_STEPS;
				progress++) {
			double multiplier = OpenComputersValueCodec
					.changeRateMultiplierFromProgress(progress);
			assertEquals(progress, OpenComputersValueCodec
					.changeRateProgressFromMultiplier(multiplier));
		}
		for(int rate = -60; rate <= 60; rate++)
			assertEquals(rate, OpenComputersValueCodec
					.rotationRateFromInput(rate));
		for(int percent = 10; percent <= 100; percent++) {
			double multiplier = OpenComputersValueCodec
					.gravityMultiplierFromPercent(percent);
			assertEquals(percent, OpenComputersValueCodec
					.gravityPercentFromMultiplier(multiplier));
		}
	}
}
