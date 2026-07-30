package zmaster587.advancedRocketry.dimension.sim;

/**
 * Optional, explicit type information for simulated celestial bodies.
 *
 * <p>This enum deliberately lives beside {@link ISimStellar}; add-ons which
 * only implement the older interface remain source and binary compatible.</p>
 */
public enum SimBodyType {
	STAR,
	BLACK_HOLE,
	PLANET,
	GAS_GIANT
}
