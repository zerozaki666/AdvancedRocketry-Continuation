package zmaster587.advancedRocketry.tile.oxygen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import zmaster587.advancedRocketry.api.AdvancedRocketryFluids;
import zmaster587.advancedRocketry.armor.ItemSpaceArmor;
import zmaster587.advancedRocketry.armor.ItemSpaceChest;
import zmaster587.libVulpes.api.IModularArmor;
import zmaster587.libVulpes.gui.CommonResources;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleImage;
import zmaster587.libVulpes.inventory.modules.ModuleLiquidIndicator;
import zmaster587.libVulpes.inventory.modules.ModulePower;
import zmaster587.libVulpes.inventory.modules.ModuleSlotArray;
import zmaster587.libVulpes.tile.TileInventoriedRFConsumerTank;
import zmaster587.libVulpes.util.IconResource;

public class TileOxygenCharger extends TileInventoriedRFConsumerTank implements IModularInventory {
	public TileOxygenCharger() {
		super(0, 2, 16000);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int slots) {
		return new int[] {};
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return false;
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
		if(resource != null && isAllowedFluid(resource.getFluid()))
			return super.fill(from, resource, doFill);
		return 0;
	}
	
	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid) {
		return isAllowedFluid(fluid);
	}	

	@Override
	public int getPowerPerOperation() {
		return 0;
	}

	@Override
	public boolean canPerformFunction() {
		if(!worldObj.isRemote) {
			for( Object player : this.worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(this.xCoord, this.yCoord, this.zCoord, this.xCoord + 1, this.yCoord + 2, this.zCoord + 1))) {
				ItemStack stack = ((EntityPlayer)player).getEquipmentInSlot(3);

				//Check for O2 fill
				if(stack != null && stack.getItem() instanceof ItemSpaceChest) {
					FluidStack fluidStack = this.drain(ForgeDirection.UNKNOWN, 100, false);
					ItemSpaceChest chest = (ItemSpaceChest)stack.getItem();

					if(chest.getAirRemaining(stack) < chest.getMaxAir(stack)
							&& fluidStack != null
							&& fluidStack.getFluid().getUnlocalizedName().contains("oxygen")
							&& fluidStack.amount > 0) {
						ItemStack filledChest = stack.copy();
						int amountInserted =
								chest.increment(filledChest, fluidStack.amount);
						if(amountInserted > 0) {
							FluidStack drained = this.drain(
									ForgeDirection.UNKNOWN, amountInserted, true);
							if(drained != null
									&& drained.amount == amountInserted) {
								stack.setTagCompound(
										filledChest.getTagCompound());
								return true;
							}
						}
					}
				}

				//Check for H2 fill (possibly merge with O2 fill
				//Fix conflict with O2 fill
				if(this.tank.getFluid() != null && this.tank.getFluid().getFluid() != AdvancedRocketryFluids.fluidOxygen && stack != null && stack.getItem() instanceof IModularArmor) {
					IInventory inv = ((IModularArmor)stack.getItem()).loadModuleInventory(stack);

					FluidStack fluidStack = this.drain(ForgeDirection.UNKNOWN, 100, false);
					if(fluidStack != null) {
						for(int i = 0; i < inv.getSizeInventory(); i++) {
							
							if(!((IModularArmor)stack.getItem()).canBeExternallyModified(stack, i))
								continue;
							
							ItemStack module = inv.getStackInSlot(i);
							if(module != null && module.getItem() instanceof IFluidContainerItem) {
								IFluidContainerItem container = (IFluidContainerItem)module.getItem();
								int amountToFill = container.fill(module, fluidStack, false);
								if(amountToFill > 0) {
									FluidStack drained = this.drain(ForgeDirection.UNKNOWN, amountToFill, true);
									if(drained == null || drained.amount != amountToFill)
										continue;
									container.fill(module, drained, true);
									
									((IModularArmor)stack.getItem()).saveModuleInventory(stack, inv);
									
									return true;
								}
							}
						}
					}
				}
				
				return false;
			}
		}
		return false;
	}

	@Override
	public void performFunction() {

	}

	@Override
	public List<ModuleBase> getModules(int ID, EntityPlayer player) {
		ArrayList<ModuleBase> modules = new ArrayList<ModuleBase>();
		
		modules.add(new ModuleSlotArray(50, 21, this, 0, 1));
		modules.add(new ModuleSlotArray(50, 57, this, 1, 2));
		if(worldObj.isRemote)
			modules.add(new ModuleImage(49, 38, new IconResource(194, 0, 18, 18, CommonResources.genericBackground)));
		
		//modules.add(new ModulePower(18, 20, this));
		modules.add(new ModuleLiquidIndicator(32, 20, this));
		
		//modules.add(toggleSwitch = new ModuleToggleSwitch(160, 5, 0, "", this, TextureResources.buttonToggleImage, 11, 26, getMachineEnabled()));
		//TODO add itemStack slots for liqiuid
		return modules;
	}

	@Override
	public String getModularInventoryName() {
		return "tile.oxygenCharger.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}
	
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		super.setInventorySlotContents(slot, stack);
		while(useBucket(0, getStackInSlot(0)));
	}
	
	private boolean useBucket(int slot, ItemStack inputStack) {
		if(slot != 0 || inputStack == null || inputStack.stackSize <= 0)
			return false;

		ItemStack singleInput = inputStack.copy();
		singleInput.stackSize = 1;

		FluidStack registeredFluid = FluidContainerRegistry.getFluidForFilledItem(singleInput);
		if(registeredFluid != null) {
			ItemStack emptyContainer = FluidContainerRegistry.drainFluidContainer(singleInput);
			if(!isAllowedFluid(registeredFluid.getFluid())
					|| emptyContainer == null
					|| !canAcceptOutput(emptyContainer)
					|| tank.fill(registeredFluid, false) != registeredFluid.amount)
				return false;

			if(tank.fill(registeredFluid, true) != registeredFluid.amount)
				return false;
			commitContainerMove(slot, emptyContainer);
			return true;
		}

		if(FluidContainerRegistry.isContainer(singleInput)) {
			FluidStack available = tank.drain(tank.getCapacity(), false);
			if(available == null)
				return false;

			ItemStack fullContainer =
					FluidContainerRegistry.fillFluidContainer(available, singleInput);
			FluidStack contained = fullContainer == null
					? null
					: FluidContainerRegistry.getFluidForFilledItem(fullContainer);
			if(contained == null || !canAcceptOutput(fullContainer))
				return false;

			FluidStack simulatedDrain = tank.drain(contained.amount, false);
			if(simulatedDrain == null || simulatedDrain.amount != contained.amount
					|| simulatedDrain.getFluid() != contained.getFluid())
				return false;

			FluidStack actualDrain = tank.drain(contained.amount, true);
			if(actualDrain == null || actualDrain.amount != contained.amount)
				return false;
			commitContainerMove(slot, fullContainer);
			return true;
		}

		if(!(singleInput.getItem() instanceof IFluidContainerItem))
			return false;

		IFluidContainerItem fluidItem = (IFluidContainerItem)singleInput.getItem();
		FluidStack itemFluid = fluidItem.getFluid(singleInput);
		if(itemFluid == null && tank.getFluid() != null) {
			ItemStack filledContainer = singleInput.copy();
			int amountFilled = fluidItem.fill(filledContainer, tank.getFluid(), true);
			FluidStack filledFluid = fluidItem.getFluid(filledContainer);
			if(amountFilled <= 0 || filledFluid == null
					|| filledFluid.amount < fluidItem.getCapacity(filledContainer)
					|| !canAcceptOutput(filledContainer))
				return false;

			FluidStack simulatedDrain = tank.drain(amountFilled, false);
			if(simulatedDrain == null || simulatedDrain.amount != amountFilled)
				return false;

			FluidStack actualDrain = tank.drain(amountFilled, true);
			if(actualDrain == null || actualDrain.amount != amountFilled)
				return false;
			commitContainerMove(slot, filledContainer);
			return true;
		}

		if(itemFluid != null && isAllowedFluid(itemFluid.getFluid())) {
			ItemStack emptiedContainer = singleInput.copy();
			FluidStack offered = fluidItem.drain(emptiedContainer, itemFluid.amount, false);
			if(offered == null || offered.amount <= 0
					|| tank.fill(offered, false) != offered.amount)
				return false;

			FluidStack removed = fluidItem.drain(emptiedContainer, offered.amount, true);
			FluidStack remaining = fluidItem.getFluid(emptiedContainer);
			if(removed == null || removed.amount != offered.amount
					|| (remaining != null && remaining.amount > 0)
					|| !canAcceptOutput(emptiedContainer))
				return false;

			if(tank.fill(removed, true) != removed.amount)
				return false;
			commitContainerMove(slot, emptiedContainer);
			return true;
		}

		return false;
	}

	private boolean isAllowedFluid(Fluid fluid) {
		return fluid != null && (fluid == AdvancedRocketryFluids.fluidOxygen
				|| fluid == AdvancedRocketryFluids.fluidHydrogen
				|| fluid.getUnlocalizedName().toLowerCase().contains("oxygen"));
	}

	private boolean canAcceptOutput(ItemStack output) {
		if(output == null)
			return false;

		ItemStack existing = getStackInSlot(1);
		if(existing == null)
			return true;

		int limit = Math.min(existing.getMaxStackSize(), getInventoryStackLimit());
		return existing.isItemEqual(output)
				&& ItemStack.areItemStackTagsEqual(existing, output)
				&& existing.stackSize + output.stackSize <= limit;
	}

	private void commitContainerMove(int inputSlot, ItemStack output) {
		ItemStack existing = getStackInSlot(1);
		if(existing == null)
			inventory.setInventorySlotContents(1, output.copy());
		else
			existing.stackSize += output.stackSize;
		decrStackSize(inputSlot, 1);
		markDirty();
	}
}
