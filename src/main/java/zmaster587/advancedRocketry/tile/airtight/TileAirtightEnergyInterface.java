package zmaster587.advancedRocketry.tile.airtight;

import cofh.api.energy.IEnergyHandler;
import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/** Six-sided RF relay with no internal energy storage. */
public class TileAirtightEnergyInterface extends TileEntity
		implements IEnergyHandler {

	@Override
	public boolean canConnectEnergy(ForgeDirection from) {
		return from != null;
	}

	@Override
	public int receiveEnergy(ForgeDirection from, int maxReceive,
			boolean simulate) {
		if(maxReceive <= 0 || worldObj == null
				|| !AirtightRelayTraversalGuard.enter(this))
			return 0;

		try {
			int remaining = maxReceive;
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				TileEntity tile = adjacent(direction);
				if(!(tile instanceof IEnergyReceiver) || tile == this)
					continue;
				int accepted = ((IEnergyReceiver)tile).receiveEnergy(
						direction.getOpposite(), remaining, simulate);
				remaining -= Math.min(remaining, Math.max(0, accepted));
				if(remaining <= 0)
					break;
			}
			return maxReceive - remaining;
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	@Override
	public int extractEnergy(ForgeDirection from, int maxExtract,
			boolean simulate) {
		if(maxExtract <= 0 || worldObj == null
				|| !AirtightRelayTraversalGuard.enter(this))
			return 0;

		try {
			int remaining = maxExtract;
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				TileEntity tile = adjacent(direction);
				if(!(tile instanceof IEnergyProvider) || tile == this)
					continue;
				int extracted = ((IEnergyProvider)tile).extractEnergy(
						direction.getOpposite(), remaining, simulate);
				remaining -= Math.min(remaining, Math.max(0, extracted));
				if(remaining <= 0)
					break;
			}
			return maxExtract - remaining;
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	@Override
	public int getEnergyStored(ForgeDirection from) {
		return queryEnergy(from, false);
	}

	@Override
	public int getMaxEnergyStored(ForgeDirection from) {
		return queryEnergy(from, true);
	}

	private int queryEnergy(ForgeDirection from, boolean maximum) {
		if(worldObj == null || !AirtightRelayTraversalGuard.enter(this))
			return 0;
		try {
			long total = 0;
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				TileEntity tile = adjacent(direction);
				if(!(tile instanceof IEnergyProvider) || tile == this)
					continue;
				IEnergyProvider provider = (IEnergyProvider)tile;
				total += maximum
						? provider.getMaxEnergyStored(direction.getOpposite())
						: provider.getEnergyStored(direction.getOpposite());
				if(total >= Integer.MAX_VALUE)
					return Integer.MAX_VALUE;
			}
			return (int)total;
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	private TileEntity adjacent(ForgeDirection direction) {
		return worldObj.getTileEntity(xCoord + direction.offsetX,
				yCoord + direction.offsetY, zCoord + direction.offsetZ);
	}
}
