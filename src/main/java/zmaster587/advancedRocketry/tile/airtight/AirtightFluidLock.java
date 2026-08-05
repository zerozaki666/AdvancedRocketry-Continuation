package zmaster587.advancedRocketry.tile.airtight;

/** Tracks a fluid type and the physical input sides that currently own it. */
final class AirtightFluidLock {

	static final int SIDE_COUNT = 6;

	private String fluidName;
	private int sourceMask;

	boolean allows(String candidate) {
		return candidate != null
				&& (fluidName == null || fluidName.equals(candidate));
	}

	void recordSource(String candidate, int side) {
		if(candidate == null || side < 0 || side >= SIDE_COUNT)
			return;
		if(fluidName == null)
			fluidName = candidate;
		if(fluidName.equals(candidate))
			sourceMask |= 1 << side;
	}

	void retainSources(int connectedMask) {
		sourceMask &= connectedMask & ((1 << SIDE_COUNT) - 1);
		if(sourceMask == 0)
			fluidName = null;
	}

	void restore(String savedFluidName, int savedSourceMask) {
		fluidName = savedFluidName == null || savedFluidName.length() == 0
				? null : savedFluidName;
		sourceMask = savedSourceMask & ((1 << SIDE_COUNT) - 1);
		if(sourceMask == 0)
			fluidName = null;
	}

	String getFluidName() {
		return fluidName;
	}

	int getSourceMask() {
		return sourceMask;
	}
}
