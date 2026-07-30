package zmaster587.advancedRocketry.api;

public class Constants {
	public static final String modId = "advancedRocketry";
	/**
	 * Legacy-preferred base for station/UI stellar targets. Real dimensions
	 * always take precedence; the resolver selects a negative fallback encoding
	 * if this value would collide with an existing dimension.
	 */
	public static final int STAR_ID_OFFSET = 10000;
}
