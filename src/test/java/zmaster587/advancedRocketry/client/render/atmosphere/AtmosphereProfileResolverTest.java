package zmaster587.advancedRocketry.client.render.atmosphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties;

public class AtmosphereProfileResolverTest {

	private static final double EPSILON = 1.0e-9D;
	private final AtmosphereProfileResolver resolver =
			new AtmosphereProfileResolver(new NoOpWarningSink());

	@Test
	public void gameplayAuthorityAndGasGiantExceptionArePreserved() {
		AtmosphereVisualProfile disabled = resolve(false, false, 1600,
				new float[] {1F, 1F, 1F},
				new float[] {1F, 1F, 1F}, null);
		assertFalse(disabled.isEnabled());

		AtmosphereVisualProfile gasGiant = resolve(false, true, 0,
				new float[] {1F, 1F, 1F},
				new float[] {1F, 1F, 1F}, null);
		assertTrue(gasGiant.isEnabled());
		assertTrue(gasGiant.isGasGiant());
		assertTrue(gasGiant.getVisualPressure() >= 2D);
	}

	@Test
	public void legacyPressureMappingIsMonotonicAndBounded() {
		int[] densities = {0, 25, 26, 75, 100, 200, 800, 1600};
		double previous = -1D;
		for(int density : densities) {
			double pressure =
					AtmosphereProfileResolver.mapLegacyPressure(density);
			assertTrue(pressure >= previous);
			assertTrue(pressure >= 0D);
			assertTrue(pressure <= 4D);
			previous = pressure;
		}
		assertEquals(1D,
				AtmosphereProfileResolver.mapLegacyPressure(100),
				EPSILON);
		assertEquals(4D,
				AtmosphereProfileResolver.mapLegacyPressure(1600),
				EPSILON);
	}

	@Test
	public void skyAndFogColorsDriveSeparateScatteringTints() {
		AtmosphereVisualProfile redRayleigh = resolve(true, false, 100,
				new float[] {1F, 0.1F, 0.1F},
				new float[] {1F, 1F, 1F}, null);
		AtmosphereVisualProfile greenMie = resolve(true, false, 100,
				new float[] {1F, 1F, 1F},
				new float[] {0.1F, 1F, 0.1F}, null);

		assertTrue(redRayleigh.getRayleighTintRed()
				> redRayleigh.getRayleighTintGreen());
		assertTrue(greenMie.getMieTintGreen()
				> greenMie.getMieTintRed());
		assertNotEquals(redRayleigh.getRayleighTintGreen(),
				greenMie.getRayleighTintGreen(), EPSILON);
	}

	@Test
	public void explicitOverridesWinWithoutMutatingGameplayValues() {
		AtmosphereVisualProperties overrides =
				new AtmosphereVisualProperties();
		overrides.setPlanetRadiusKm(7000D);
		overrides.setAtmosphereHeightKm(120D);
		overrides.setRayleighColor(0.2F, 0.4F, 1F);
		overrides.setMieAnisotropy(0.9D);
		overrides.setExposure(1.5D);

		AtmosphereVisualProfile profile = resolve(true, false, 100,
				new float[] {1F, 1F, 1F},
				new float[] {1F, 1F, 1F}, overrides);
		assertEquals(7000D, profile.getGroundRadiusKm(), EPSILON);
		assertEquals(120D, profile.getAtmosphereHeightKm(), EPSILON);
		assertEquals(0.9D, profile.getMieAnisotropy(), EPSILON);
		assertEquals(1.5D, profile.getExposure(), EPSILON);
		assertEquals(100, profile.getAtmosphereDensity());
	}

	@Test
	public void malformedLegacyColorsResolveToFiniteDefaults() {
		AtmosphereVisualProfile profile = resolve(true, false, 100,
				new float[] {Float.NaN},
				new float[] {Float.POSITIVE_INFINITY, 1F}, null);
		for(int component = 0; component < 3; component++) {
			assertTrue(Double.isFinite(profile.getRayleighTint(component)));
			assertTrue(Double.isFinite(profile.getMieTint(component)));
		}
	}

	@Test
	public void partialLayerOverridesUseTheResolvedBodyPreset() {
		AtmosphereVisualProperties overrides =
				new AtmosphereVisualProperties();
		overrides.setRayleighScaleHeightKm(150D);
		overrides.setMieScaleHeightKm(150D);
		overrides.setAbsorptionCenterKm(150D);
		overrides.setAbsorptionWidthKm(150D);

		AtmosphereVisualProfile gasGiant = resolve(false, true, 0,
				new float[] {1F, 1F, 1F},
				new float[] {1F, 1F, 1F}, overrides);
		assertEquals(200D, gasGiant.getAtmosphereHeightKm(), EPSILON);
		assertEquals(150D,
				gasGiant.getRayleighScaleHeightKm(), EPSILON);
		assertEquals(150D, gasGiant.getMieScaleHeightKm(), EPSILON);
		assertEquals(150D, gasGiant.getAbsorptionCenterKm(), EPSILON);
		assertEquals(150D, gasGiant.getAbsorptionWidthKm(), EPSILON);

		AtmosphereVisualProfile normal = resolve(true, false, 100,
				new float[] {1F, 1F, 1F},
				new float[] {1F, 1F, 1F}, overrides);
		assertEquals(100D, normal.getAtmosphereHeightKm(), EPSILON);
		assertEquals(8D, normal.getRayleighScaleHeightKm(), EPSILON);
		assertEquals(1.2D, normal.getMieScaleHeightKm(), EPSILON);
		assertEquals(25D, normal.getAbsorptionCenterKm(), EPSILON);
		assertEquals(15D, normal.getAbsorptionWidthKm(), EPSILON);
	}

	private AtmosphereVisualProfile resolve(boolean hasAtmosphere,
			boolean gasGiant, int density, float[] sky, float[] fog,
			AtmosphereVisualProperties overrides) {
		return resolver.resolve(42, hasAtmosphere, gasGiant, density,
				sky, fog, overrides == null ? null : overrides.snapshot());
	}

	private static final class NoOpWarningSink
			implements AtmosphereProfileResolver.WarningSink {
		@Override
		public void warnOnce(int dimensionId, String field, String message) {
		}
	}
}
