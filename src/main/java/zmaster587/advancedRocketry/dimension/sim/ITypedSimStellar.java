package zmaster587.advancedRocketry.dimension.sim;

/**
 * Optional extension for simulated bodies that can expose an unambiguous
 * celestial type without changing the public {@link ISimStellar} contract.
 */
public interface ITypedSimStellar extends ISimStellar {
	SimBodyType getBodyType();
}
