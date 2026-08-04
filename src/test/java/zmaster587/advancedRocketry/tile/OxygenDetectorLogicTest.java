package zmaster587.advancedRocketry.tile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

public class OxygenDetectorLogicTest {

	@Test
	public void anyModeRequiresOneExposedBreathableSide() {
		boolean[] exposed = sides(false, false, true, true, false, false);
		boolean[] breathable = sides(false, false, false, true, true, false);

		assertTrue(OxygenDetectorLogic.shouldEmit(false, exposed, breathable));
		breathable[3] = false;
		assertFalse(OxygenDetectorLogic.shouldEmit(false, exposed, breathable));
	}

	@Test
	public void allModeIgnoresObstructedSides() {
		boolean[] exposed = sides(false, true, true, false, false, false);
		boolean[] breathable = sides(false, true, true, false, true, true);

		assertTrue(OxygenDetectorLogic.shouldEmit(true, exposed, breathable));
		breathable[2] = false;
		assertFalse(OxygenDetectorLogic.shouldEmit(true, exposed, breathable));
	}

	@Test
	public void allModeFailsClosedWhenEverySideIsObstructed() {
		assertFalse(OxygenDetectorLogic.shouldEmit(true,
				sides(false, false, false, false, false, false),
				sides(true, true, true, true, true, true)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsIncompleteSideArrays() {
		OxygenDetectorLogic.shouldEmit(false, new boolean[5], new boolean[6]);
	}

	@Test
	public void detectorDefaultsToAnyModeAndPersistsAllMode() {
		TileOxygenDetector detector = new TileOxygenDetector();
		assertFalse(detector.isRequireAllSides());

		detector.setRequireAllSides(true);
		NBTTagCompound saved = new NBTTagCompound();
		detector.writeDetectorState(saved);

		TileOxygenDetector loaded = new TileOxygenDetector();
		loaded.readDetectorState(saved);
		assertTrue(loaded.isRequireAllSides());
	}

	private static boolean[] sides(boolean down, boolean up, boolean north,
			boolean south, boolean west, boolean east) {
		return new boolean[] { down, up, north, south, west, east };
	}
}
