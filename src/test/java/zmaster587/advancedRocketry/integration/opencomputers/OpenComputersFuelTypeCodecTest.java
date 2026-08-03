package zmaster587.advancedRocketry.integration.opencomputers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import zmaster587.advancedRocketry.api.fuel.FuelRegistry.FuelType;

public class OpenComputersFuelTypeCodecTest {

	@Test
	public void parsesAllFiveStableFuelNamesCaseInsensitively() {
		assertEquals(FuelType.LIQUID,
				OpenComputersFuelTypeCodec.parse("liquid"));
		assertEquals(FuelType.NUCLEAR,
				OpenComputersFuelTypeCodec.parse("NUCLEAR"));
		assertEquals(FuelType.ION,
				OpenComputersFuelTypeCodec.parse(" Ion "));
		assertEquals(FuelType.WARP,
				OpenComputersFuelTypeCodec.parse("warp"));
		assertEquals(FuelType.IMPULSE,
				OpenComputersFuelTypeCodec.parse("impulse"));
	}

	@Test
	public void rejectsLaserModesAndUnknownFuelNames() {
		assertNull(OpenComputersFuelTypeCodec.parse("single"));
		assertNull(OpenComputersFuelTypeCodec.parse("spiral"));
		assertNull(OpenComputersFuelTypeCodec.parse(""));
		assertNull(OpenComputersFuelTypeCodec.parse(null));
	}

	@Test
	public void emitsLowercaseStableNames() {
		assertEquals("liquid",
				OpenComputersFuelTypeCodec.name(FuelType.LIQUID));
		assertEquals("impulse",
				OpenComputersFuelTypeCodec.name(FuelType.IMPULSE));
	}
}
