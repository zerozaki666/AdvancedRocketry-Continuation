package zmaster587.advancedRocketry.block;

/** Optional hook for airtight interfaces that cache adjacent connections. */
public interface IAirtightInterfaceNeighborAware {

	void onAdjacentBlockChanged();
}
