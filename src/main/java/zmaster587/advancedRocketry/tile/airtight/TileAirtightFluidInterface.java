package zmaster587.advancedRocketry.tile.airtight;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import zmaster587.advancedRocketry.block.IAirtightInterfaceNeighborAware;

/**
 * Six-sided, zero-storage fluid relay. The first fluid that actually crosses
 * the block remains selected until its last physical input side is removed.
 */
public class TileAirtightFluidInterface extends TileEntity
		implements IFluidHandler, IAirtightInterfaceNeighborAware {

	private static final String NBT_FLUID = "activeFluid";
	private static final String NBT_SOURCES = "activeFluidSources";

	private final AirtightFluidLock fluidLock = new AirtightFluidLock();
	private final TileEntity[] sourceTiles =
			new TileEntity[AirtightFluidLock.SIDE_COUNT];

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && worldObj.getTotalWorldTime() % 5 == 0)
			pruneRemovedSources();
	}

	@Override
	public void onAdjacentBlockChanged() {
		if(worldObj != null && !worldObj.isRemote)
			pruneRemovedSources();
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
		if(resource == null || resource.getFluid() == null
				|| resource.amount <= 0 || worldObj == null)
			return 0;
		pruneRemovedSources();
		String fluidName = resource.getFluid().getName();
		if(!fluidLock.allows(fluidName)
				|| !AirtightRelayTraversalGuard.enter(this))
			return 0;

		try {
			int remaining = resource.amount;
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				IFluidHandler handler = adjacentHandler(direction);
				if(handler == null || handler == this
						|| !handler.canFill(direction.getOpposite(),
								resource.getFluid()))
					continue;
				FluidStack offered = resource.copy();
				offered.amount = remaining;
				int filled = handler.fill(direction.getOpposite(), offered,
						doFill);
				remaining -= Math.min(remaining, Math.max(0, filled));
				if(remaining <= 0)
					break;
			}
			int accepted = resource.amount - remaining;
			if(doFill && accepted > 0)
				recordSource(fluidName, from);
			return accepted;
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource,
			boolean doDrain) {
		if(resource == null || resource.getFluid() == null
				|| resource.amount <= 0 || worldObj == null)
			return null;
		pruneRemovedSources();
		String fluidName = resource.getFluid().getName();
		if(!fluidLock.allows(fluidName)
				|| !AirtightRelayTraversalGuard.enter(this))
			return null;

		try {
			int remaining = resource.amount;
			int drainedAmount = 0;
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				IFluidHandler handler = adjacentHandler(direction);
				if(handler == null || handler == this
						|| !handler.canDrain(direction.getOpposite(),
								resource.getFluid()))
					continue;
				FluidStack request = resource.copy();
				request.amount = remaining;
				FluidStack drained = handler.drain(direction.getOpposite(),
						request, doDrain);
				if(drained == null || !drained.isFluidEqual(resource)
						|| drained.amount <= 0)
					continue;
				int moved = Math.min(remaining, drained.amount);
				drainedAmount += moved;
				remaining -= moved;
				if(doDrain)
					recordSource(fluidName, direction);
				if(remaining <= 0)
					break;
			}
			return drainedAmount == 0 ? null
					: new FluidStack(resource.getFluid(), drainedAmount);
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain,
			boolean doDrain) {
		if(maxDrain <= 0 || worldObj == null)
			return null;
		pruneRemovedSources();
		Fluid fluid = activeFluid();
		if(fluid == null)
			fluid = discoverFluid(from);
		return fluid == null ? null
				: drain(from, new FluidStack(fluid, maxDrain), doDrain);
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid) {
		if(fluid == null || worldObj == null)
			return false;
		pruneRemovedSources();
		if(!fluidLock.allows(fluid.getName())
				|| !AirtightRelayTraversalGuard.enter(this))
			return false;
		try {
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				IFluidHandler handler = adjacentHandler(direction);
				if(handler != null && handler != this
						&& handler.canFill(direction.getOpposite(), fluid))
					return true;
			}
			return false;
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid) {
		if(worldObj == null)
			return false;
		pruneRemovedSources();
		Fluid requested = fluid == null ? activeFluid() : fluid;
		if(requested == null)
			requested = discoverFluid(from);
		if(requested == null || !fluidLock.allows(requested.getName())
				|| !AirtightRelayTraversalGuard.enter(this))
			return false;
		try {
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				IFluidHandler handler = adjacentHandler(direction);
				if(handler != null && handler != this
						&& handler.canDrain(direction.getOpposite(), requested))
					return true;
			}
			return false;
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from) {
		if(worldObj == null || !AirtightRelayTraversalGuard.enter(this))
			return emptyTankInfo();
		try {
			Fluid fluid = activeFluid();
			if(fluid == null)
				fluid = discoverFluidWithoutGuard(from);
			if(fluid == null)
				return emptyTankInfo();

			long available = 0;
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
				if(direction == from)
					continue;
				IFluidHandler handler = adjacentHandler(direction);
				if(handler == null || handler == this)
					continue;
				FluidTankInfo[] infos = handler.getTankInfo(
						direction.getOpposite());
				if(infos == null)
					continue;
				for(FluidTankInfo info : infos) {
					if(info != null && info.fluid != null
							&& info.fluid.getFluid() == fluid)
						available += Math.max(0, info.fluid.amount);
				}
			}
			int amount = (int)Math.min(Integer.MAX_VALUE, available);
			return new FluidTankInfo[] { new FluidTankInfo(
					new FluidStack(fluid, amount), amount) };
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	private Fluid discoverFluid(ForgeDirection excluded) {
		if(!AirtightRelayTraversalGuard.enter(this))
			return null;
		try {
			return discoverFluidWithoutGuard(excluded);
		}
		finally {
			AirtightRelayTraversalGuard.exit(this);
		}
	}

	private Fluid discoverFluidWithoutGuard(ForgeDirection excluded) {
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			if(direction == excluded)
				continue;
			IFluidHandler handler = adjacentHandler(direction);
			if(handler == null || handler == this)
				continue;
			FluidStack simulated = handler.drain(direction.getOpposite(), 1,
					false);
			if(simulated != null && simulated.getFluid() != null
					&& simulated.amount > 0)
				return simulated.getFluid();
			FluidTankInfo[] infos = handler.getTankInfo(direction.getOpposite());
			if(infos == null)
				continue;
			for(FluidTankInfo info : infos) {
				if(info != null && info.fluid != null
						&& info.fluid.amount > 0)
					return info.fluid.getFluid();
			}
		}
		return null;
	}

	private void recordSource(String fluidName, ForgeDirection direction) {
		if(direction == null || direction.ordinal() >= AirtightFluidLock.SIDE_COUNT)
			return;
		fluidLock.recordSource(fluidName, direction.ordinal());
		sourceTiles[direction.ordinal()] = adjacent(direction);
		markDirty();
	}

	private void pruneRemovedSources() {
		if(worldObj == null || fluidLock.getSourceMask() == 0)
			return;
		int connectedMask = 0;
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			int bit = 1 << direction.ordinal();
			if((fluidLock.getSourceMask() & bit) == 0)
				continue;
			TileEntity current = adjacent(direction);
			if(!(current instanceof IFluidHandler))
				continue;
			if(sourceTiles[direction.ordinal()] != null
					&& sourceTiles[direction.ordinal()] != current)
				continue;
			sourceTiles[direction.ordinal()] = current;
			connectedMask |= bit;
		}
		int oldMask = fluidLock.getSourceMask();
		fluidLock.retainSources(connectedMask);
		int removedMask = oldMask & ~fluidLock.getSourceMask();
		for(int side = 0; side < sourceTiles.length; side++) {
			if((removedMask & (1 << side)) != 0)
				sourceTiles[side] = null;
		}
		if(removedMask != 0)
			markDirty();
	}

	private Fluid activeFluid() {
		return fluidLock.getFluidName() == null ? null
				: FluidRegistry.getFluid(fluidLock.getFluidName());
	}

	private IFluidHandler adjacentHandler(ForgeDirection direction) {
		TileEntity tile = adjacent(direction);
		return tile instanceof IFluidHandler ? (IFluidHandler)tile : null;
	}

	private TileEntity adjacent(ForgeDirection direction) {
		return worldObj.getTileEntity(xCoord + direction.offsetX,
				yCoord + direction.offsetY, zCoord + direction.offsetZ);
	}

	private static FluidTankInfo[] emptyTankInfo() {
		return new FluidTankInfo[] { new FluidTankInfo(null, 0) };
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		if(fluidLock.getFluidName() != null) {
			nbt.setString(NBT_FLUID, fluidLock.getFluidName());
			nbt.setByte(NBT_SOURCES, (byte)fluidLock.getSourceMask());
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		String fluidName = nbt.getString(NBT_FLUID);
		if(FluidRegistry.getFluid(fluidName) == null)
			fluidName = null;
		fluidLock.restore(fluidName, nbt.getByte(NBT_SOURCES));
	}
}
