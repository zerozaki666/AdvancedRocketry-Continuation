package zmaster587.advancedRocketry.client.render.atmosphere;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AtmosphereLightingContextTest {

	@Test
	public void finiteZeroRadianceIsAValidFailDarkLight() {
		AtmosphereLightingContext context =
				new AtmosphereLightingContext();
		assertTrue(context.setDirections(
				0D, 1D, 0D, 0D, 1D, 0D));
		context.setPrimaryLightRadiance(0D, 0D, 0D);

		assertTrue(context.isValid());
		assertFalse(context.hasPositiveRadiance());
	}

	@Test
	public void precomputedResolverPreservesZeroRadiance() {
		AtmosphereLightingContext context =
				new AtmosphereLightingContext();
		CelestialLightResolver resolver =
				new CelestialLightResolver();

		assertTrue(resolver.resolvePrecomputedRadiance(
				0D, 0D, 0D,
				1D, 0D, 0D, 1D, 0D, 0D,
				1F, 1F, 20L, 0.5F, context));
		assertTrue(context.isValid());
		assertFalse(context.hasPositiveRadiance());
	}

	@Test
	public void invalidRadianceRemainsAnInvalidResolution() {
		AtmosphereLightingContext context =
				new AtmosphereLightingContext();
		assertTrue(context.setDirections(
				0D, 1D, 0D, 0D, 1D, 0D));
		context.setPrimaryLightRadiance(
				Double.NaN, 0D, 0D);

		assertFalse(context.isValid());
		assertFalse(context.hasPositiveRadiance());
	}
}
