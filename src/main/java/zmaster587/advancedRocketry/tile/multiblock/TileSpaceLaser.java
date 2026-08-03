package zmaster587.advancedRocketry.tile.multiblock;

import io.netty.buffer.ByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.AdvancedRocketryItems;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.integration.CompatibilityMgr;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess.Result;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.satellite.SatelliteLaser;
import zmaster587.advancedRocketry.satellite.SatelliteLaserNoDrill;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.world.provider.WorldProviderStation;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.api.LibVulpesBlocks;
import zmaster587.libVulpes.block.RotatableBlock;
import zmaster587.libVulpes.compat.InventoryCompat;
import zmaster587.libVulpes.inventory.modules.IButtonInventory;
import zmaster587.libVulpes.inventory.modules.IGuiCallback;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleButton;
import zmaster587.libVulpes.inventory.modules.ModuleImage;
import zmaster587.libVulpes.inventory.modules.ModuleNumericTextbox;
import zmaster587.libVulpes.inventory.modules.ModulePower;
import zmaster587.libVulpes.inventory.modules.ModuleSlotArray;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.inventory.modules.ModuleTextBox;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.tile.multiblock.TileMultiPowerConsumer;
import zmaster587.libVulpes.util.INetworkMachine;
import zmaster587.libVulpes.util.MultiInventory;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;

@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")
public class TileSpaceLaser extends TileMultiPowerConsumer implements ISidedInventory, INetworkMachine, IModularInventory, IGuiCallback, IButtonInventory, SimpleComponent {

	private static final int INVSIZE = 9;
	ItemStack glassPanel;
	//ItemStack invBuffer[];
	SatelliteLaserNoDrill laserSat;
	protected boolean isRunning, finished;
	protected IInventory adjInv;
	private int radius, xCenter, yCenter, numSteps;
	private ForgeDirection prevDir;
	public int laserX, laserZ, tickSinceLastOperation;
	private static final ForgeDirection[] VALID_INVENTORY_DIRECTIONS = { ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH, ForgeDirection.WEST};
	private static final int POWER_PER_OPERATION = (int) (10000  * Configuration.spaceLaserPowerMult); 
	private ModuleTextBox locationX, locationZ;
	private ModuleText updateText;
	MultiInventory inv;

	Object[][][] structure = new Object[][][]{
			{
				{null, null, null, null, null},
				{null, null, LibVulpesBlocks.blockAdvStructureBlock, null, null},
				{null, LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock, null},
				{null, null, LibVulpesBlocks.blockAdvStructureBlock, null, null},
				{null, null, null, null, null}
			},
			{
				{null, null,'c', null, null},
				{null, 'P', Blocks.glass, 'P', null},
				{'P', LibVulpesBlocks.blockAdvStructureBlock, Blocks.glass, LibVulpesBlocks.blockAdvStructureBlock, 'P'},
				{null, 'P', LibVulpesBlocks.blockAdvStructureBlock, 'P', null},
				{null, null, 'P', null, null}
			},
			{
				{null, null, LibVulpesBlocks.blockAdvStructureBlock, null, null},
				{null, LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock, null},
				{LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock, Blocks.glass, LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock},
				{null, LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock, LibVulpesBlocks.blockAdvStructureBlock, null},
				{null, null, LibVulpesBlocks.blockAdvStructureBlock, null, null}
			},
			{
				{null, null, 'O', null, null},
				{null, LibVulpesBlocks.blockAdvStructureBlock, Blocks.glass, LibVulpesBlocks.blockAdvStructureBlock, null},
				{'O', Blocks.glass, Blocks.glass, Blocks.glass, 'O'},
				{null, LibVulpesBlocks.blockAdvStructureBlock, Blocks.glass, LibVulpesBlocks.blockAdvStructureBlock, null},
				{null, null, 'O', null, null}
			},
	};

	public enum MODE{
		SINGLE,
		LINE_X,
		LINE_Z,
		SPIRAL
	};

	private MODE mode;

	Ticket ticket;

	public TileSpaceLaser() {
		super();
		glassPanel = null;
		//invBuffer = new ItemStack[INVSIZE];
		radius = 0;
		xCenter = 0;
		yCenter = 0;
		numSteps = 0;
		prevDir = ForgeDirection.UNKNOWN;


		inv = new MultiInventory(itemOutPorts);
		tickSinceLastOperation = 0;
		laserX = 0;
		laserZ = 0;
		if(Configuration.laserDrillPlanet)
			laserSat = new SatelliteLaser(inv);
		else
			laserSat = new SatelliteLaserNoDrill(inv);

		isRunning = false;
		finished = false;
		mode = MODE.SINGLE;
	}

	//Required so we see the laser
	@SideOnly(Side.CLIENT)
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(this.xCoord -5, this.yCoord - 100, this.zCoord - 5, this.xCoord + 5, this.yCoord +5, this.zCoord + 5);
	}

	@Override
	public Object[][][] getStructure() {
		return structure;
	}

	@Override
	public String getMachineName() {
		return getInventoryName();
	}

	/*
	 * ID 10: client changed xcoord in interface
	 * ID 11: client changed ycoord in interface
	 * ID 12: sync whether the machine is running
	 * ID 13: sync Mode
	 * ID 14: jam reset
	 */
	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		super.writeDataToNetwork(out, id);
		if(id == 10)
			out.writeInt(this.laserX);
		else if(id == 11)
			out.writeInt(this.laserZ);
		else if(id == 12)
			out.writeBoolean(isRunning);
		else if(id == 13)
			out.writeInt(mode.ordinal());
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte id,
			NBTTagCompound nbt) {
		super.readDataFromNetwork(in, id, nbt);
		if(id == 10)
			nbt.setInteger("laserX", in.readInt());
		else if(id == 11)
			nbt.setInteger("laserZ", in.readInt());
		else if(id == 12)
			nbt.setBoolean("isRunning", in.readBoolean());
		else if(id == 13)
			nbt.setInteger("mode", in.readInt());

	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		super.useNetworkData(player, side, id, nbt);
		if(id == 10)
			setLaserCoordinates(nbt.getInteger("laserX"), laserZ);
		else if(id == 11)
			setLaserCoordinates(laserX, nbt.getInteger("laserZ"));
		else if(id == 12)
			this.isRunning = nbt.getBoolean("isRunning");
		else if(id == 13 && !isRunning()) {
			int ordinal = nbt.getInteger("mode");
			if(ordinal >= 0 && ordinal < MODE.values().length)
				setMode(MODE.values()[ordinal]);
		}
		else if(id == 14)
			this.attempUnjam();

		markDirty();
	}

	private void resetSpiral() {
		radius = 0;
		prevDir = ForgeDirection.UNKNOWN;
		xCenter = 0;
		yCenter = 0;
		numSteps = 0;
	}

	@Override
	public boolean isRunning() {return isRunning && isComplete();}

	public boolean isFinished() {return finished;}

	public MODE getMode() {return mode;}

	public void incrementMode() {
		int num = mode.ordinal();
		num++;

		if(num >= MODE.values().length)
			num = 0;

		setMode(MODE.values()[num]);
	}

	public void decrementMode() {
		int num = mode.ordinal();
		num--;

		if(num < 0)
			num = MODE.values().length - 1;

		setMode(MODE.values()[num]);
	}

	public void setMode(MODE newMode) {
		if(newMode == null)
			return;
		if(mode != newMode) {
			if(mode == MODE.SPIRAL || newMode == MODE.SPIRAL)
				resetSpiral();
			mode = newMode;
			onAutomationTargetChanged();
		}
	}

	private void setLaserCoordinates(int x, int z) {
		if(laserX != x || laserZ != z || finished) {
			laserX = x;
			laserZ = z;
			finished = false;
			if(mode == MODE.SPIRAL)
				resetSpiral();
			onAutomationTargetChanged();
		}
	}

	private void onAutomationTargetChanged() {
		markDirty();
		if(worldObj != null && !worldObj.isRemote)
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	@Override
	public boolean canUpdate() {return true;}

	public void setFinished(boolean value) { finished = value; }

	private void setRunning(boolean value) {
		this.isRunning = value;
		markDirty();
		this.worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	@Override
	public void updateEntity() {
		//TODO: drain energy

		//Freaky jenky crap to make sure the multiblock loads on chunkload etc
		if(timeAlive == 0 && !worldObj.isRemote) {
			if(isComplete())
				canRender = completeStructure = completeStructure();
			timeAlive = 0x1;
			checkCanRun();
		}

		if(!this.worldObj.isRemote) {
			tickSinceLastOperation++;

			if(!isAllowedToRun()) {
				laserSat.deactivateLaser();
				this.setFinished(true);
				this.setRunning(false);
				releaseChunkTicket();
			}
			else
				if(hasPowerForOperation() && isReadyForOperation() && laserSat.isAlive() && !laserSat.getJammed()) {
					laserSat.performOperation();

					batteries.extractEnergy(POWER_PER_OPERATION, false);
					tickSinceLastOperation = 0;
				}
		}

		if(laserSat.isFinished()) {
			setRunning(false);
			laserSat.deactivateLaser();
			releaseChunkTicket();

			if(!laserSat.getJammed()) {
				if(mode == MODE.SINGLE) 
					finished = true;

				if(this.worldObj.getBlockPowerInput(this.xCoord, this.yCoord, this.zCoord) != 0) {
					if(mode == MODE.LINE_X) {
						this.laserX += 3;
					}
					else if(mode == MODE.LINE_Z) {
						this.laserZ += 3;
					}
					else if(mode == MODE.SPIRAL) {
						numSteps++;
						if(radius < numSteps) {
							numSteps = 0;
							if(prevDir == ForgeDirection.NORTH)
								prevDir = ForgeDirection.EAST;
							else if(prevDir == ForgeDirection.EAST){
								prevDir = ForgeDirection.SOUTH;
								radius++;
							}
							else if(prevDir == ForgeDirection.SOUTH)
								prevDir = ForgeDirection.WEST;
							else {
								prevDir = ForgeDirection.NORTH;
								radius++;
							}
						}

						this.laserX += 3*prevDir.offsetX;
						this.laserZ += 3*prevDir.offsetZ;
					}
				}
				//TODO: unneeded?
				checkCanRun();
			}
		}
	}

	public boolean isReadyForOperation() {
		if(batteries.getEnergyStored() == 0)
			return false;

		return tickSinceLastOperation > (3*this.batteries.getMaxEnergyStored()/(float)this.batteries.getEnergyStored());
	}

	public void onDestroy() {
		if(laserSat != null) {
			laserSat.deactivateLaser();
		}
		releaseChunkTicket();
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if(laserSat != null) {
			laserSat.deactivateLaser();
		}
		releaseChunkTicket();
		isRunning = false;
	}

	private void releaseChunkTicket() {
		if(ticket != null) {
			ForgeChunkManager.releaseTicket(ticket);
			ticket = null;
		}
	}
	
	@Override
	protected void writeNetworkData(NBTTagCompound nbt) {
		super.writeNetworkData(nbt);
		nbt.setBoolean("IsRunning", isRunning);
		nbt.setBoolean("Finished", finished);
		nbt.setInteger("laserX", laserX);
		nbt.setInteger("laserZ", laserZ);
		nbt.setByte("mode", (byte)mode.ordinal());
		nbt.setBoolean("Jammed", laserSat.getJammed());
	}
	
	@Override
	protected void readNetworkData(NBTTagCompound nbt) {
		super.readNetworkData(nbt);
		isRunning = nbt.getBoolean("IsRunning");
		finished = nbt.getBoolean("Finished");
		laserX = nbt.getInteger("laserX");
		laserZ = nbt.getInteger("laserZ");
		int modeOrdinal = nbt.getByte("mode");
		mode = modeOrdinal >= 0 && modeOrdinal < MODE.values().length
				? MODE.values()[modeOrdinal] : MODE.SINGLE;
		laserSat.setJammed(nbt.getBoolean("Jammed"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		NBTTagCompound laser = new NBTTagCompound();
		laserSat.writeToNBT(laser);

		nbt.setTag("laser", laser);

		NBTTagList list = new NBTTagList();


		if(glassPanel != null) {
			NBTTagCompound tag = new NBTTagCompound();
			glassPanel.writeToNBT(tag);
			nbt.setTag("GlassPane", tag);
		}

		nbt.setInteger("laserX", laserX);
		nbt.setInteger("laserZ", laserZ);
		nbt.setByte("mode", (byte)mode.ordinal());

		if(mode == MODE.SPIRAL && prevDir != null) {
			nbt.setInteger("CenterX", xCenter);
			nbt.setInteger("CenterY", yCenter);
			nbt.setInteger("radius", radius);
			nbt.setInteger("numSteps", numSteps);
			nbt.setInteger("prevDir", prevDir.ordinal());
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		laserSat.readFromNBT(nbt.getCompoundTag("laser"));
		if(nbt.hasKey("GlassPane")) {
			NBTTagCompound tag = nbt.getCompoundTag("GlassPane");
			glassPanel = ItemStack.loadItemStackFromNBT(tag);
		}

		laserX = nbt.getInteger("laserX");
		laserZ = nbt.getInteger("laserZ");
		int modeOrdinal = nbt.getByte("mode");
		mode = modeOrdinal >= 0 && modeOrdinal < MODE.values().length
				? MODE.values()[modeOrdinal] : MODE.SINGLE;

		if(mode == MODE.SPIRAL && nbt.hasKey("prevDir")){
			xCenter = nbt.getInteger("CenterX");
			yCenter = nbt.getInteger("CenterY");
			radius = nbt.getInteger("radius");
			numSteps = nbt.getInteger("numSteps");
			int direction = nbt.getInteger("prevDir");
			prevDir = direction >= 0
					&& direction < ForgeDirection.values().length
							? ForgeDirection.values()[direction]
							: ForgeDirection.UNKNOWN;
		}
	}

	/**
	 * Needs to be called when the laser is finished
	 */
	public void onLaserFinish() {

	}

	/**
	 * Take items from internal inventory
	 * @return
	 */
	public void attempUnjam() {
		if(!laserSat.getJammed())
			return;

		/*IInventory depositInv = getAvalibleInv(ZUtils.getFirstFilledSlot(invBuffer));

		//Assign subInv


		while(depositInv != null) {
			ZUtils.mergeInventory(invBuffer, depositInv);

			if(ZUtils.isInvEmpty(invBuffer))
				break;
			depositInv = getAvalibleInv(ZUtils.getFirstFilledSlot(invBuffer));
		}*/

		//TODO: finish
		laserSat.setJammed(false);
		//!ZUtils.isInvEmpty(invBuffer);
		finished = false;

		checkCanRun();
	}

	//TODO: buildcraft support

	/**
	 * Gets the first inventory with an empty slot
	 * @return first available inventory or null
	 */
	private Object getAvalibleInv() {
		ForgeDirection front = RotatableBlock.getFront(this.getBlockMetadata());

		for(ForgeDirection f : VALID_INVENTORY_DIRECTIONS) {
			if(f == front)
				continue;

			TileEntity e = this.worldObj.getTileEntity(this.xCoord + f.offsetX, this.yCoord + f.offsetY, this.zCoord + f.offsetZ);


			if(InventoryCompat.canInjectItems(e))
				return (IInventory)e;
		}
		return null;
	}

	/**
	 * Gets the first inv with a slot able to hold 'itemStack'
	 * @param item item to fit into inventory
	 * @return inv with capablity of holding 'itemStack'
	 */
	private Object getAvalibleInv(ItemStack item) {
		if(item == null)
			return getAvalibleInv();

		ForgeDirection front = RotatableBlock.getFront(this.getBlockMetadata());

		for(ForgeDirection f : VALID_INVENTORY_DIRECTIONS) {
			if(f == front)
				continue;

			TileEntity e = this.worldObj.getTileEntity(this.xCoord + f.offsetX, this.yCoord + f.offsetY, this.zCoord + f.offsetZ);


			if(InventoryCompat.canInjectItems(e, item))
				return e;
		}
		return null;
	}

	private boolean canMachineSeeEarth() {
		//for(int i = yCoord - 1; i > 0; i--) {
		//	if(worldObj.isBlockNormalCubeDefault(xCoord, i, zCoord,true))
		//		return false;
		//}
		return true;
	}

	private boolean isAllowedToRun() {
		return !(glassPanel == null || batteries.getEnergyStored() == 0 || !(this.worldObj.provider instanceof WorldProviderStation) || !zmaster587.advancedRocketry.dimension.DimensionManager.getInstance().canTravelTo(((WorldProviderStation)this.worldObj.provider).getDimensionProperties(xCoord, zCoord).getParentPlanet()) ||
				Configuration.laserBlackListDims.contains(((WorldProviderStation)this.worldObj.provider).getDimensionProperties(xCoord, zCoord).getParentPlanet()));
	}

	/**
	 * Checks to see if the situation for firing the laser exists... and changes the state accordingly
	 */
	public void checkCanRun() {
		//Laser requires lense, redstone power, not be jammed, and be in orbit and energy to function
		if(!isAllowedToRun() || !worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) {
			if(laserSat.isAlive()) {
				laserSat.deactivateLaser();
			}

			setRunning(false);
			releaseChunkTicket();
		} else if(!laserSat.isAlive() && !finished && !laserSat.getJammed() && worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord) && canMachineSeeEarth()) {

			//Laser will be on at this point
			int orbitDimId = ((WorldProviderStation)this.worldObj.provider).getDimensionProperties(xCoord, zCoord).getParentPlanet();
			if(orbitDimId == SpaceObjectManager.WARPDIMID)
				return;
			WorldServer orbitWorld = DimensionManager.getWorld(orbitDimId);

			if(orbitWorld == null) {
				DimensionManager.initDimension(orbitDimId);
				orbitWorld = DimensionManager.getWorld(orbitDimId);
				if(orbitWorld == null)
					return;
			}


			if(ticket == null) {
				ticket = ForgeChunkManager.requestTicket(AdvancedRocketry.instance, this.worldObj, Type.NORMAL);
				if(ticket != null)
					ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(this.xCoord / 16 - (this.xCoord < 0 ? 1 : 0), this.zCoord / 16 - (this.zCoord < 0 ? 1 : 0)));
			}

			setRunning(laserSat.activateLaser(orbitWorld, laserX, laserZ));
			if(!isRunning)
				releaseChunkTicket();
		}

		if(!this.worldObj.isRemote)
			PacketHandler.sendToNearby(new PacketMachine(this, (byte)12), this.xCoord, this.yCoord, this.zCoord, 128, this.worldObj.provider.dimensionId);
	}

	public int getEnergyPercentScaled(int max) {
		return (int)(max * (batteries.getEnergyStored() / (float)batteries.getMaxEnergyStored()) );
	}

	public boolean hasEnergy() {
		return batteries.getEnergyStored() != 0;
	}

	//InventoryHandling start
	@Override
	public int getSizeInventory() {
		return inv.getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		if(i == 0)
			return glassPanel;
		else {
			i--;
			return inv.getStackInSlot(i);
		}
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		ItemStack ret;

		if(i == 0) {
			ret = glassPanel.copy();
			glassPanel = null;
			return ret;
		}
		return null;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		if(i == 0)
			return glassPanel;
		return null;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack) {

		//TODO: add gregcipies
		if(i == 0)
			glassPanel = itemstack;
		else {


			//TileEntity e = this.worldObj.getTileEntity(this.xCoord + f.offsetX, this.yCoord + f.offsetY, this.zCoord + f.offsetZ);

			if(InventoryCompat.canInjectItems(inv, itemstack))
				InventoryCompat.injectItem(inv, itemstack);


			this.checkCanRun();
		}
	}

	@Override
	public String getInventoryName() {
		return LibVulpes.proxy.getLocalizedString("tile.spaceLaser.name");
	}


	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer) {
		return entityplayer.getDistanceSq(this.xCoord, this.yCoord, this.zCoord) <= 64;
	}

	@Override
	public void openInventory() {
		// TODO Perhaps make sure laser isn't running
	}

	@Override
	public void closeInventory() {
		// TODO Auto-generated method stub

	}

	@Override
	public int[] getAccessibleSlotsFromSide(int var1) {
		return new int[] {};
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemstack, int j) {
		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemstack, int j) {
		return false;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		if(i == 0)
			return CompatibilityMgr.gregtechLoaded ? OreDictionary.getOreName(OreDictionary.getOreID(itemstack)).equals("lenseRuby") : AdvancedRocketryItems.itemLens == itemstack.getItem() ? true : false;

			return inv.isItemValidForSlot(i, itemstack);
	}
	//InventoryHandling end

	//Redstone Flux start

	/**
	 * @param simulate true to simulate.. false to drain the power
	 * @return returns whether enough power is stored for the next opertation
	 */
	public boolean hasPowerForOperation() {
		return POWER_PER_OPERATION <= batteries.getEnergyStored();
	}

	//Redstone Flux end

	public boolean isJammed() {
		return laserSat.getJammed();
	}

	public void setJammed(boolean b) {
		laserSat.setJammed(b);

	}

	@Override
	public boolean hasCustomInventoryName() {
		return false;
	}

	@Override
	public void onModuleUpdated(ModuleBase module) {

		if(module == locationX) {
			if(!((ModuleTextBox)module).getText().isEmpty() && !((ModuleTextBox)module).getText().contentEquals("-"))
				setLaserCoordinates(Integer.parseInt(
						((ModuleTextBox)module).getText()), laserZ);
			PacketHandler.sendToServer(new PacketMachine(this,(byte) 10));
		}
		else if(module == locationZ) {
			if(!((ModuleTextBox)module).getText().isEmpty() && !((ModuleTextBox)module).getText().contentEquals("-"))
				setLaserCoordinates(laserX, Integer.parseInt(
						((ModuleTextBox)module).getText()));
			PacketHandler.sendToServer(new PacketMachine(this,(byte) 11));
		}


	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();

		if(worldObj.isRemote) {
			modules.add(locationX = new ModuleNumericTextbox(this, 113, 31, 50, 10, 16));
			modules.add(locationZ = new ModuleNumericTextbox(this, 113, 41, 50, 10, 16));

			locationX.setText(String.valueOf(this.laserX));
			locationZ.setText(String.valueOf(this.laserZ));

			modules.add(updateText = new ModuleText(130, 20, this.getMode().toString(), 0x0b0b0b, true));
			modules.add(new ModuleText(103, 33, "X:",  0x0b0b0b));
			modules.add(new ModuleText(103, 43, "Z:",  0x0b0b0b));

			modules.add(new ModuleImage(8, 16, TextureResources.laserGuiBG));
		}

		modules.add(new ModuleButton(103, 20, 0, "", this,  zmaster587.libVulpes.inventory.TextureResources.buttonLeft, 5, 8));
		modules.add(new ModuleButton(157, 20, 1, "", this,  zmaster587.libVulpes.inventory.TextureResources.buttonRight, 5, 8));
		modules.add(new ModuleButton(103, 62, 2, LibVulpes.proxy.getLocalizedString("msg.spacelaser.reset"), this,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild, 34, 20));
		modules.add(new ModulePower(11, 25, batteries));
		modules.add(new ModuleSlotArray(56, 54, this, 0, 1));




		return modules;
	}

	@Override
	public String getModularInventoryName() {
		return "tile.spaceLaser.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public void onInventoryButtonPressed(int buttonId) {

		if(buttonId == 0){
			this.decrementMode();
			updateText.setText(this.getMode().toString());
		}
		else if(buttonId == 1) {
			this.incrementMode();
			updateText.setText(this.getMode().toString());
		}
		else if(buttonId == 2) {
			PacketHandler.sendToServer(new PacketMachine(this, (byte)14));
			return;
		}
		else 
			return;

		if(!this.isRunning())
			PacketHandler.sendToServer(new PacketMachine(this, (byte)13));
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "mining_laser";
	}

	private String getModeName() {
		return mode.name().toLowerCase(Locale.ENGLISH);
	}

	private MODE parseMode(String value) {
		if(value == null)
			return null;
		try {
			return MODE.valueOf(value.trim().toUpperCase(Locale.ENGLISH));
		}
		catch(IllegalArgumentException ignored) {
			return null;
		}
	}

	@Callback(doc = "function():number, number -- Returns the current X/Z mining target.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getCoordinates(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { laserX, laserZ };
	}

	@Callback(doc = "function(x:number, z:number):boolean, number|string, number|string -- Atomically sets integer X/Z coordinates.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setCoordinates(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asMutatorError();
		double xValue = args.checkDouble(0);
		double zValue = args.checkDouble(1);
		if(Double.isNaN(xValue) || Double.isInfinite(xValue)
				|| Double.isNaN(zValue) || Double.isInfinite(zValue))
			return OpenComputersComponentAccess.mutatorError("not_finite",
					"Mining coordinates must be finite integers.");
		if(xValue != Math.rint(xValue) || zValue != Math.rint(zValue))
			return OpenComputersComponentAccess.mutatorError("invalid_value",
					"Mining coordinates must be whole numbers.");
		if(xValue < -30000000D || xValue > 30000000D
				|| zValue < -30000000D || zValue > 30000000D)
			return OpenComputersComponentAccess.mutatorError("out_of_range",
					"Mining coordinates must be within -30000000..30000000.");
		setLaserCoordinates((int)xValue, (int)zValue);
		return new Object[] { true, laserX, laserZ };
	}

	@Callback(doc = "function():boolean -- Returns whether the laser satellite is actively running.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isRunning(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { isRunning() };
	}

	@Callback(doc = "function():boolean -- Returns whether SINGLE mode completed its target.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isFinished(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { isFinished() };
	}

	@Callback(doc = "function():boolean -- Returns whether the laser output is jammed.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isJammed(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { isJammed() };
	}

	@Callback(doc = "function():string -- Returns single, line_x, line_z, or spiral.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getMode(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { getModeName() };
	}

	@Callback(doc = "function(mode:string):boolean, string -- Sets the mining mode while idle.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setMode(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asMutatorError();
		if(isRunning())
			return OpenComputersComponentAccess.mutatorError("busy",
					"Mining mode cannot be changed while the laser is running.");
		MODE requested = parseMode(args.checkString(0));
		if(requested == null)
			return OpenComputersComponentAccess.mutatorError("invalid_mode",
					"Mode must be single, line_x, line_z, or spiral.");
		setMode(requested);
		return new Object[] { true, getModeName() };
	}

	@Callback(doc = "function():boolean, boolean -- Attempts to clear a jam and returns the previous jam state.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] unjam(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asMutatorError();
		boolean wasJammed = isJammed();
		if(wasJammed)
			attempUnjam();
		onAutomationTargetChanged();
		return new Object[] { true, wasJammed };
	}

	@Callback(doc = "function():table -- Returns structure, energy, redstone, target, mode, and run state.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		boolean complete = isComplete();
		boolean hasLens = glassPanel != null;
		boolean energy = hasEnergy();
		boolean powered = worldObj.isBlockIndirectlyGettingPowered(
				xCoord, yCoord, zCoord);
		boolean canSee = canMachineSeeEarth();
		boolean inWarp = worldObj.provider instanceof WorldProviderStation
				&& ((WorldProviderStation)worldObj.provider)
						.getDimensionProperties(xCoord, zCoord)
						.getParentPlanet() == SpaceObjectManager.WARPDIMID;
		boolean validTarget = worldObj.provider instanceof WorldProviderStation
				&& !inWarp;

		String state;
		if(!complete)
			state = "incomplete_multiblock";
		else if(!hasLens)
			state = "no_lens";
		else if(!energy)
			state = "no_energy";
		else if(inWarp)
			state = "in_warp";
		else if(!validTarget)
			state = "invalid_target";
		else if(isJammed())
			state = "jammed";
		else if(isFinished())
			state = "finished";
		else if(!powered)
			state = "redstone_off";
		else if(isRunning())
			state = "running";
		else
			state = "ready";

		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("x", laserX);
		status.put("z", laserZ);
		status.put("mode", getModeName());
		status.put("running", isRunning());
		status.put("finished", isFinished());
		status.put("jammed", isJammed());
		status.put("multiblockComplete", complete);
		status.put("hasLens", hasLens);
		status.put("hasEnergy", energy);
		status.put("redstonePowered", powered);
		status.put("canSeeTarget", canSee);
		status.put("state", state);
		return new Object[] { status };
	}
}
