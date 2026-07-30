package zmaster587.advancedRocketry.client.render.atmosphere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ShortBuffer;

import org.junit.Test;

public class AtmosphereOpticalDepthLutTest {

	private static final AtmosphereOpticalDepthLut.Key EARTH_KEY =
			new AtmosphereOpticalDepthLut.Key(
					6360D, 100D, 8D, 1.2D, 25D, 15D);

	@Test
	public void generatesFiniteRgba16Columns() {
		AtmosphereOpticalDepthLut lut =
				AtmosphereOpticalDepthLut.generate(EARTH_KEY, 24, 12, 32);
		assertEquals(24, lut.getWidth());
		assertEquals(12, lut.getHeight());
		assertEquals(24*12, lut.getTexelCount());
		assertEquals(24*12*4, lut.getEncodedElementCount());

		for(int texel = 0; texel < lut.getTexelCount(); texel++) {
			for(int channel = 0; channel < 3; channel++) {
				double value = lut.getDecodedColumnDensity(texel, channel);
				assertTrue(Double.isFinite(value));
				assertTrue(value >= 0D);
			}
			assertEquals(0, lut.getEncodedUnsigned16(texel,
					AtmosphereOpticalDepthLut.CHANNEL_RESERVED));
		}

		ShortBuffer encoded =
				ShortBuffer.allocate(lut.getEncodedElementCount());
		lut.putEncodedRgba16(encoded);
		assertEquals(0, encoded.remaining());
	}

	@Test
	public void horizonMappingRejectsBlockedLightAndClampsCenters() {
		AtmosphereOpticalDepthLut lut =
				AtmosphereOpticalDepthLut.generate(EARTH_KEY, 32, 16, 24);
		double[] coordinates = new double[3];

		assertFalse(lut.mapToTextureCoordinates(0D, -1D, coordinates));
		assertTrue(lut.mapToTextureCoordinates(0D, 1D, coordinates));
		assertTrue(coordinates[0] >= 0.5D/32D);
		assertTrue(coordinates[0] <= 1D-0.5D/32D);
		assertTrue(coordinates[1] >= 0.5D/16D);
		assertTrue(coordinates[1] <= 1D-0.5D/16D);

		assertTrue(lut.sampleColumns(0D, 1D, coordinates));
		assertTrue(coordinates[0] > 0D);
		assertTrue(coordinates[1] > 0D);
		assertTrue(coordinates[2] >= 0D);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsInvalidGeometryKey() {
		new AtmosphereOpticalDepthLut.Key(
				6360D, 4000D, 8D, 1.2D, 25D, 15D);
	}
}
