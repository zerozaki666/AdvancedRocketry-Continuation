package zmaster587.advancedRocketry.tile;

/**
 * Pure aggregation logic for the six-side oxygen detector.
 */
public final class OxygenDetectorLogic {

	public static final int SIDE_COUNT = 6;

	private OxygenDetectorLogic() {
	}

	/**
	 * Returns whether the detector should emit redstone for the supplied side
	 * readings. A fully obstructed detector always fails closed, including in
	 * all-sides mode.
	 */
	public static boolean shouldEmit(boolean requireAll, boolean[] exposed,
			boolean[] breathable) {
		validate(exposed, "exposed");
		validate(breathable, "breathable");

		int exposedCount = 0;
		int breathableCount = 0;
		for(int side = 0; side < SIDE_COUNT; side++) {
			if(!exposed[side])
				continue;
			exposedCount++;
			if(breathable[side])
				breathableCount++;
		}

		if(exposedCount == 0)
			return false;
		return requireAll ? breathableCount == exposedCount
				: breathableCount > 0;
	}

	private static void validate(boolean[] values, String name) {
		if(values == null || values.length != SIDE_COUNT)
			throw new IllegalArgumentException(name
					+ " must contain exactly " + SIDE_COUNT + " sides");
	}
}
