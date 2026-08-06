package zmaster587.advancedRocketry.tile.airtight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class AirtightFluidLockTest {

	@Test
	public void keepsFirstFluidUntilLastInputSideIsRemoved() {
		AirtightFluidLock lock = new AirtightFluidLock();
		lock.recordSource("water", 2);

		assertTrue(lock.allows("water"));
		assertFalse(lock.allows("lava"));

		lock.recordSource("water", 5);
		lock.retainSources(1 << 5);
		assertEquals("water", lock.getFluidName());
		assertFalse(lock.allows("lava"));

		lock.retainSources(0);
		assertNull(lock.getFluidName());
		assertTrue(lock.allows("lava"));
	}

	@Test
	public void ignoresCompetingFluidAndInvalidSides() {
		AirtightFluidLock lock = new AirtightFluidLock();
		lock.recordSource("water", 1);
		lock.recordSource("lava", 3);
		lock.recordSource("water", 6);

		assertEquals("water", lock.getFluidName());
		assertEquals(1 << 1, lock.getSourceMask());
	}
}
