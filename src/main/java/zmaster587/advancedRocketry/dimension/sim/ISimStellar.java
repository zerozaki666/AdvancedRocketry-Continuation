package zmaster587.advancedRocketry.dimension.sim;

/**
 * Immutable input consumed by the lightweight universe simulation.
 */
public interface ISimStellar {
	String getID();
	String getName();
	double getMass();
	double getSize();
	double getOrbitRadius();
	double getInitialAngle();
	double getInclination();
	double getStaticX();
	double getStaticY();
	double getStaticZ();
	String getParentID();
	boolean isStar();
	int getDimensionId();
	boolean isLandable();
}
