package zmaster587.advancedRocketry.block;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import zmaster587.advancedRocketry.tile.TileOxygenDetector;

/**
 * Redstone-emitting block backed by the focused six-side oxygen detector.
 */
public class BlockOxygenDetector extends BlockRedstoneEmitter {

	public BlockOxygenDetector(Material material, String activeIconName) {
		super(material, activeIconName);
	}

	@Override
	public TileEntity createTileEntity(World world, int metadata) {
		return new TileOxygenDetector();
	}
}
