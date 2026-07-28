package zmaster587.advancedRocketry.dimension.sim;

/**
 * Optional server-side hazard radii for a simulated celestial body.
 */
public interface ISimHazard {
	double getWarningRadius();
	double getInfluenceRadius();
	double getCaptureRadius();
}
