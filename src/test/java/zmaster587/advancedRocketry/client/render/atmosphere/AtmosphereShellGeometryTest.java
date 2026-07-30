package zmaster587.advancedRocketry.client.render.atmosphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AtmosphereShellGeometryTest {

	private static final double EPSILON = 1.0e-8D;
	private static final double EARTH_SHELL_RATIO = 100.0D/6360.0D;

	@Test
	public void ordinaryScalePreservesLegacySurfacePosition() {
		double groundRadius = 66.0D;
		double envelope = AtmosphereShellGeometry.maximumRenderedRadius(
				groundRadius, EARTH_SHELL_RATIO, 1.0D,
				true, true, true, false);
		double centerY = AtmosphereShellGeometry.resolveStationCenterY(
				groundRadius, envelope);

		assertEquals(-76.0D, centerY, EPSILON);
		assertEquals(10.0D, Math.abs(centerY)-groundRadius, EPSILON);
		assertTrue(Math.abs(centerY) > envelope);
	}

	@Test
	public void scaleTwoReportedThresholdGetsMeshSafeClearance() {
		double orbitalDistance = (4300.0D-100.0D)/200.0D;
		double groundRadius = 66.0D*100.0D/orbitalDistance*2.0D;
		double envelope = AtmosphereShellGeometry.maximumRenderedRadius(
				groundRadius, EARTH_SHELL_RATIO, 1.0D,
				true, true, true, false);
		double legacyCenterDistance = groundRadius+10.0D;
		double centerY = AtmosphereShellGeometry.resolveStationCenterY(
				groundRadius, envelope);
		double legacyClearance = legacyCenterDistance-envelope;

		assertTrue(legacyClearance > 0.0D);
		assertTrue(legacyClearance < 1.0D);
		assertTrue(Math.abs(centerY) > legacyCenterDistance);
		assertTrue(Math.abs(centerY)-envelope > 1.0D);
	}

	@Test
	public void scaleTwoLowOrbitKeepsCameraOutsideAtmosphere() {
		double orbitalDistance = (4100.0D-100.0D)/200.0D;
		double groundRadius = 66.0D*100.0D/orbitalDistance*2.0D;
		double envelope = AtmosphereShellGeometry.maximumRenderedRadius(
				groundRadius, EARTH_SHELL_RATIO, 1.0D,
				true, true, true, false);
		double legacyCenterDistance = groundRadius+10.0D;
		double centerY = AtmosphereShellGeometry.resolveStationCenterY(
				groundRadius, envelope);
		double expectedClearance = envelope*(1.0D/Math.cos(
				Math.PI/AtmosphereShellGeometry
						.STATION_SHELL_LATITUDE_SEGMENTS)-1.0D);

		assertTrue(legacyCenterDistance < envelope);
		assertEquals(expectedClearance,
				Math.abs(centerY)-envelope, EPSILON);
		assertTrue(Math.abs(centerY) > envelope);
	}

	@Test
	public void highPressureFallbackDefinesMaximumEnvelope() {
		double groundRadius = 660.0D;
		double physical = AtmosphereShellGeometry.physicalOuterRadius(
				groundRadius, EARTH_SHELL_RATIO);
		double fallback = AtmosphereShellGeometry.fallbackOuterRadius(
				groundRadius, EARTH_SHELL_RATIO, 4.0D);
		double envelope = AtmosphereShellGeometry.maximumRenderedRadius(
				groundRadius, EARTH_SHELL_RATIO, 4.0D,
				true, true, true, false);

		assertTrue(fallback > physical);
		assertEquals(fallback, envelope, EPSILON);
		assertTrue(Math.abs(AtmosphereShellGeometry.resolveStationCenterY(
				groundRadius, envelope)) > envelope);
	}

	@Test
	public void thinAtmosphereEnvelopeMatchesFallbackMinimumThickness() {
		double groundRadius = 200000.0D;
		double thinPhysicalRatio = 1.0D/200000.0D;
		double physical = AtmosphereShellGeometry.physicalOuterRadius(
				groundRadius, thinPhysicalRatio);
		double fallback = AtmosphereShellGeometry.fallbackOuterRadius(
				groundRadius, thinPhysicalRatio, 1.0D);
		double envelope = AtmosphereShellGeometry.maximumRenderedRadius(
				groundRadius, thinPhysicalRatio, 1.0D,
				true, true, false, false);

		assertEquals(200001.0D, physical, EPSILON);
		assertEquals(200020.0D, fallback, EPSILON);
		assertEquals(fallback, envelope, EPSILON);
	}

	@Test
	public void offModeEnvelopeStillIncludesEnabledCloudLayer() {
		double groundRadius = 2000.0D;
		double cloud = AtmosphereShellGeometry.cloudOuterRadius(
				groundRadius, false);
		double cloudEnvelope =
				AtmosphereShellGeometry.maximumRenderedRadius(
						groundRadius, EARTH_SHELL_RATIO, 1.0D,
						false, false, true, false);
		double disabledEnvelope =
				AtmosphereShellGeometry.maximumRenderedRadius(
						groundRadius, EARTH_SHELL_RATIO, 1.0D,
						false, false, false, false);

		assertEquals(cloud, cloudEnvelope, EPSILON);
		assertEquals(groundRadius, disabledEnvelope, EPSILON);
		assertTrue(Math.abs(
				AtmosphereShellGeometry.resolveStationCenterY(
						groundRadius, cloudEnvelope))
				> groundRadius+10.0D);
		assertEquals(-(groundRadius+10.0D),
				AtmosphereShellGeometry.resolveStationCenterY(
						groundRadius, disabledEnvelope), EPSILON);
	}

	@Test
	public void legacyUsesFallbackWithoutShaderEnvelope() {
		double groundRadius = 660.0D;
		double fallback = AtmosphereShellGeometry.fallbackOuterRadius(
				groundRadius, EARTH_SHELL_RATIO, 0.25D);
		double envelope = AtmosphereShellGeometry.maximumRenderedRadius(
				groundRadius, EARTH_SHELL_RATIO, 0.25D,
				false, true, false, false);

		assertEquals(fallback, envelope, EPSILON);
		assertTrue(envelope < AtmosphereShellGeometry.physicalOuterRadius(
				groundRadius, EARTH_SHELL_RATIO));
	}

	@Test
	public void gasGiantCloudUsesItsActualDisplayScale() {
		double groundRadius = 100.0D;
		assertEquals(101.2D,
				AtmosphereShellGeometry.cloudOuterRadius(
						groundRadius, true), EPSILON);
	}

	@Test
	public void maximumLegalShellRatioRemainsFiniteAndOutside() {
		double groundRadius = 1000000.0D;
		double envelope = AtmosphereShellGeometry.maximumRenderedRadius(
				groundRadius, 0.5D, 4.0D,
				true, true, true, true);
		double centerY = AtmosphereShellGeometry.resolveStationCenterY(
				groundRadius, envelope);
		double expectedClearance = envelope*(1.0D/Math.cos(
				Math.PI/AtmosphereShellGeometry
						.STATION_SHELL_LATITUDE_SEGMENTS)-1.0D);

		assertEquals(1800000.0D, envelope, EPSILON);
		assertTrue(Double.isFinite(centerY));
		assertEquals(expectedClearance,
				Math.abs(centerY)-envelope, 1.0e-5D);
	}

	@Test
	public void bareSurfaceKeepsLegacyPositionAtExtremeScale() {
		double groundRadius = 1000000.0D;
		assertEquals(-(groundRadius+10.0D),
				AtmosphereShellGeometry.resolveStationCenterY(
						groundRadius, groundRadius), EPSILON);
	}
}
