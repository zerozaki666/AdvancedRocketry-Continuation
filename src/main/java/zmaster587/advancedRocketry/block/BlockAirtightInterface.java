package zmaster587.advancedRocketry.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Full-cube, no-GUI container used by the airtight pass-through interfaces.
 */
public class BlockAirtightInterface extends BlockContainer {

	private final Class<? extends TileEntity> tileClass;

	public BlockAirtightInterface(Material material,
			Class<? extends TileEntity> tileClass) {
		super(material);
		this.tileClass = tileClass;
		setStepSound(Block.soundTypeMetal);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int metadata) {
		try {
			return tileClass.newInstance();
		}
		catch(Exception exception) {
			throw new IllegalStateException("Could not create airtight interface "
					+ tileClass.getName(), exception);
		}
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z,
			EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		return false;
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z,
			Block neighbor) {
		super.onNeighborBlockChange(world, x, y, z, neighbor);
		TileEntity tile = world.getTileEntity(x, y, z);
		if(tile instanceof IAirtightInterfaceNeighborAware)
			((IAirtightInterfaceNeighborAware)tile)
					.onAdjacentBlockChanged();
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z,
			EntityLivingBase placer, ItemStack stack) {
		super.onBlockPlacedBy(world, x, y, z, placer, stack);
		TileEntity tile = world.getTileEntity(x, y, z);
		if(tile instanceof IAirtightInterfacePlacementAware)
			((IAirtightInterfacePlacementAware)tile).onPlacedBy(placer);
	}

	@Override
	public boolean isOpaqueCube() {
		return true;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return true;
	}
}
