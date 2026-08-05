package zmaster587.advancedRocketry.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlanetChipTravelModeTest {

	@Test
	public void defaultsToDirectForMissingOrInvalidValues() {
		assertEquals(PlanetChipTravelMode.DIRECT,
				PlanetChipTravelMode.parse(null));
		assertEquals(PlanetChipTravelMode.DIRECT,
				PlanetChipTravelMode.parse("AUTOMATIC"));
		assertFalse(PlanetChipTravelMode.parse("AUTOMATIC").usesFreeSpace());
	}

	@Test
	public void parsesModesWithoutCaseOrWhitespaceSensitivity() {
		assertEquals(PlanetChipTravelMode.DIRECT,
				PlanetChipTravelMode.parse(" direct "));
		assertEquals(PlanetChipTravelMode.MANUAL,
				PlanetChipTravelMode.parse("manual"));
		assertTrue(PlanetChipTravelMode.parse(" MANUAL ").usesFreeSpace());
	}
}
