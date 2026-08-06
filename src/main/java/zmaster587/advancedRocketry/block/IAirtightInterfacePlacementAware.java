package zmaster587.advancedRocketry.block;

import net.minecraft.entity.EntityLivingBase;

/** Optional hook for integrations that need the placing player's identity. */
public interface IAirtightInterfacePlacementAware {

	void onPlacedBy(EntityLivingBase placer);
}
