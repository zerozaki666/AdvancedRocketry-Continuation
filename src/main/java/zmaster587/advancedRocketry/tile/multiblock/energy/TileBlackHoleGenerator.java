package zmaster587.advancedRocketry.tile.multiblock.energy;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationTarget;
import zmaster587.advancedRocketry.stations.StationTargetResolver;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.api.LibVulpesBlocks;
import zmaster587.libVulpes.block.BlockMeta;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.tile.multiblock.TileMultiBlock;
import zmaster587.libVulpes.tile.multiblock.TileMultiPowerProducer;
import zmaster587.libVulpes.util.Vector3F;

/**
 * Turns matter into RF while its containing station directly orbits a
 * top-level black hole.
 */
public class TileBlackHoleGenerator extends TileMultiPowerProducer {

	public static final byte PACKET_STATUS = 2;

	private static final int STATUS_SYNC_INTERVAL = 20;
	private static final int STATUS_SYNC_RADIUS = 128;

	private static final String NBT_BURN_REMAINING = "burnTicksRemaining";
	private static final String NBT_STATE = "bhGeneratorState";
	private static final String NBT_POWER = "bhGeneratorPower";
	private static final String NBT_BURN_TOTAL = "bhGeneratorBurnTotal";
	private static final String NBT_PROGRESS = "bhGeneratorProgressBucket";
	private static final String NBT_TARGET = "bhGeneratorTarget";

	private static final Object[][][] STRUCTURE = new Object[][][] {
		{
			{null, null, null},
			{null, LibVulpesBlocks.blockAdvStructureBlock, null},
			{null, null, null}
		},
		{
			{null, Character.valueOf('c'), null},
			{Character.valueOf('*'), LibVulpesBlocks.blockAdvStructureBlock,
					Character.valueOf('*')},
			{null, Character.valueOf('*'), null}
		},
		{
			{null, LibVulpesBlocks.blockAdvStructureBlock, null},
			{null, LibVulpesBlocks.blockAdvStructureBlock, null},
			{null, null, null}
		},
		{
			{null, null, null},
			{null, LibVulpesBlocks.blockAdvStructureBlock, null},
			{null, null, null}
		},
		{
			{null, null, null},
			{null, LibVulpesBlocks.blockAdvStructureBlock, null},
			{null, null, null}
		}
	};

	private BlackHoleGeneratorState generatorState;
	private int burnTicksRemaining;
	private int currentBurnTotal;
	private int powerMadeLastTick;
	private int currentTargetId;

	private boolean initialCheck;
	private int ticksSinceStatusSync;
	private BlackHoleGeneratorState lastSyncedState;
	private int lastSyncedPower;
	private int lastSyncedProgress;
	private int lastSyncedTarget;

	private ModuleText statusText;

	public TileBlackHoleGenerator() {
		generatorState = BlackHoleGeneratorState.INCOMPLETE;
		currentTargetId = BlackHoleGeneratorEligibility.NO_TARGET;
		ticksSinceStatusSync = STATUS_SYNC_INTERVAL;
		lastSyncedPower = Integer.MIN_VALUE;
		lastSyncedProgress = Integer.MIN_VALUE;
		lastSyncedTarget = Integer.MIN_VALUE;
		statusText = new ModuleText(36, 20,
				localize("msg.blackholegenerator.state.incomplete"),
				0x2b2b2b, 0.8f);
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public Object[][][] getStructure() {
		return STRUCTURE;
	}

	@Override
	public List<BlockMeta> getAllowableWildCardBlocks(Character wildCard) {
		List<BlockMeta> blocks = super.getAllowableWildCardBlocks(wildCard);
		if(wildCard == null || wildCard.charValue() != '*')
			return blocks;

		List<BlockMeta> inputBlocks = TileMultiBlock.getMapping('I');
		if(inputBlocks != null)
			blocks.addAll(inputBlocks);

		blocks.add(new BlockMeta(LibVulpesBlocks.blockAdvStructureBlock));

		List<BlockMeta> powerOutputs = TileMultiBlock.getMapping('p');
		if(powerOutputs != null)
			blocks.addAll(powerOutputs);

		return blocks;
	}

	@Override
	public boolean shouldHideBlock(World world, int x, int y, int z,
			Block block) {
		return block == getBlockType();
	}

	@Override
	public String getMachineName() {
		return "tile.blackholegenerator.name";
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		// The OBJ extends below the controller and rotates through X/Z.
		return AxisAlignedBB.getBoundingBox(xCoord - 3, yCoord - 5, zCoord - 3,
				xCoord + 4, yCoord + 3, zCoord + 4);
	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = super.getModules(id, player);
		statusText = new ModuleText(36, 20, buildStatusText(), 0x2b2b2b,
				0.8f);
		modules.add(statusText);
		return modules;
	}

	public BlackHoleGeneratorState getGeneratorState() {
		return generatorState;
	}

	public int getPowerMadeLastTick() {
		return powerMadeLastTick;
	}

	public int getBurnTicksRemaining() {
		return burnTicksRemaining;
	}

	public int getCurrentTargetId() {
		return currentTargetId;
	}

	public boolean isProducingPower() {
		return generatorState == BlackHoleGeneratorState.RUNNING
				&& powerMadeLastTick > 0;
	}

	@Override
	public void setMachineEnabled(boolean enabled) {
		super.setMachineEnabled(enabled);
		if(!enabled && worldObj != null && !worldObj.isRemote) {
			setProductionState(isComplete()
							? BlackHoleGeneratorState.DISABLED
							: BlackHoleGeneratorState.INCOMPLETE,
					0,
					currentTargetId);
			syncStatusIfNeeded(true);
		}
	}

	@Override
	public void invalidateComponent(TileEntity tile) {
		super.invalidateComponent(tile);
		if(worldObj != null && !worldObj.isRemote) {
			canRender = false;
			resetCache();
			setProductionState(BlackHoleGeneratorState.INCOMPLETE, 0,
					currentTargetId);
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			syncStatusIfNeeded(true);
		}
	}

	@Override
	public void deconstructMultiBlock(World world, int destroyedX,
			int destroyedY, int destroyedZ, boolean blockBroken) {
		super.deconstructMultiBlock(world, destroyedX, destroyedY, destroyedZ,
				blockBroken);
		if(worldObj != null && !worldObj.isRemote) {
			setProductionState(BlackHoleGeneratorState.INCOMPLETE, 0,
					currentTargetId);
			syncStatusIfNeeded(true);
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if(worldObj == null)
			return;

		if(worldObj.isRemote) {
			updateClient();
			return;
		}

		if(ticksSinceStatusSync < STATUS_SYNC_INTERVAL)
			++ticksSinceStatusSync;

		validateOrRetryStructure();
		if(!isComplete()) {
			setProductionState(BlackHoleGeneratorState.INCOMPLETE, 0,
					currentTargetId);
			syncStatusIfNeeded(false);
			return;
		}

		if(!enabled) {
			setProductionState(BlackHoleGeneratorState.DISABLED, 0,
					currentTargetId);
			syncStatusIfNeeded(false);
			return;
		}

		BlackHoleGeneratorEligibility.Result eligibility =
				BlackHoleGeneratorEligibility.evaluate(worldObj, xCoord, zCoord);
		if(!eligibility.isEligible()) {
			setProductionState(eligibility.getFailureState(), 0,
					eligibility.getTargetId());
			syncStatusIfNeeded(false);
			return;
		}

		int targetId = eligibility.getTargetId();
		int requestedRf =
				BlackHoleGeneratorFuelRegistry.getRequestedPowerPerTick();
		if(requestedRf <= 0) {
			setProductionState(BlackHoleGeneratorState.CONFIG_DISABLED, 0,
					targetId);
			syncStatusIfNeeded(false);
			return;
		}

		if(getBatteries().getMaxEnergyStored() <= 0) {
			setProductionState(BlackHoleGeneratorState.NO_OUTPUT, 0, targetId);
			syncStatusIfNeeded(false);
			return;
		}

		int acceptedRf = getBatteries().acceptEnergy(requestedRf, true);
		if(acceptedRf < 0)
			acceptedRf = 0;
		else if(acceptedRf > requestedRf)
			acceptedRf = requestedRf;

		if(acceptedRf == 0) {
			setProductionState(BlackHoleGeneratorState.OUTPUT_FULL, 0,
					targetId);
			syncStatusIfNeeded(false);
			return;
		}

		if(burnTicksRemaining <= 0) {
			int newBurnTime = consumeOneFuelItem();
			if(newBurnTime <= 0) {
				setProductionState(BlackHoleGeneratorState.NO_VALID_INPUT, 0,
						targetId);
				syncStatusIfNeeded(false);
				return;
			}

			burnTicksRemaining = newBurnTime;
			currentBurnTotal = newBurnTime;
			markDirty();
		}

		producePower(acceptedRf);
		powerMadeLastTick = acceptedRf;
		generatorState = BlackHoleGeneratorState.RUNNING;
		currentTargetId = targetId;
		setMachineRunning(true);

		--burnTicksRemaining;
		if(burnTicksRemaining < 0)
			burnTicksRemaining = 0;
		// Persist every decrement. Unloading a chunk therefore pauses rather
		// than rewinds or advances an active burn.
		markDirty();

		syncStatusIfNeeded(false);
	}

	private void updateClient() {
		if(generatorState == BlackHoleGeneratorState.RUNNING
				&& burnTicksRemaining > 0)
			--burnTicksRemaining;

		if(statusText != null)
			statusText.setText(buildStatusText());
	}

	private void validateOrRetryStructure() {
		boolean wasRenderable = canRender;
		if(!initialCheck) {
			initialCheck = true;
			if(isEntireStructureLoaded())
				attemptCompleteStructure();
			else {
				completeStructure = false;
				canRender = false;
				resetCache();
			}
			if(wasRenderable != canRender)
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			return;
		}

		if(!isComplete() && worldObj.getTotalWorldTime() % 20L == 0L
				&& isEntireStructureLoaded()) {
			attemptCompleteStructure();
			if(wasRenderable != canRender)
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	private boolean isEntireStructureLoaded() {
		Vector3F<Integer> offset = getControllerOffset(STRUCTURE);
		if(offset == null)
			return false;

		ForgeDirection front = getFrontDirection();
		for(int y = 0; y < STRUCTURE.length; ++y) {
			for(int z = 0; z < STRUCTURE[y].length; ++z) {
				for(int x = 0; x < STRUCTURE[y][z].length; ++x) {
					int globalX = xCoord + (x - offset.x) * front.offsetZ
							- (z - offset.z) * front.offsetX;
					int globalY = yCoord - y + offset.y;
					int globalZ = zCoord - (x - offset.x) * front.offsetX
							- (z - offset.z) * front.offsetZ;
					if(!worldObj.blockExists(globalX, globalY, globalZ))
						return false;
				}
			}
		}
		return true;
	}

	private int consumeOneFuelItem() {
		for(IInventory inventory : getItemInPorts()) {
			if(inventory == null)
				continue;

			for(int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
				ItemStack stack = inventory.getStackInSlot(slot);
				int burnTime =
						BlackHoleGeneratorFuelRegistry.getBurnTime(stack);
				if(burnTime <= 0)
					continue;

				ItemStack consumed = inventory.decrStackSize(slot, 1);
				if(consumed == null || consumed.stackSize <= 0)
					continue;

				inventory.markDirty();
				markDirty();
				return burnTime;
			}
		}
		return 0;
	}

	private void setProductionState(BlackHoleGeneratorState state, int power,
			int targetId) {
		generatorState = state == null
				? BlackHoleGeneratorState.INCOMPLETE : state;
		powerMadeLastTick = Math.max(0, power);
		currentTargetId = targetId;
		setMachineRunning(generatorState == BlackHoleGeneratorState.RUNNING
				&& powerMadeLastTick > 0);
	}

	private int getProgressBucket() {
		if(burnTicksRemaining <= 0 || currentBurnTotal <= 0)
			return 0;

		long scaled = (long)burnTicksRemaining * 20L;
		return Math.max(1, Math.min(20,
				(int)((scaled + currentBurnTotal - 1L) / currentBurnTotal)));
	}

	private void syncStatusIfNeeded(boolean force) {
		if(worldObj == null || worldObj.isRemote)
			return;

		int progress = getProgressBucket();
		boolean importantChange = generatorState != lastSyncedState
				|| powerMadeLastTick != lastSyncedPower
				|| currentTargetId != lastSyncedTarget;
		boolean progressChanged = progress != lastSyncedProgress;
		boolean progressMaySync = progressChanged
				&& ticksSinceStatusSync >= STATUS_SYNC_INTERVAL;

		if(!force && !importantChange && !progressMaySync)
			return;

		PacketHandler.sendToNearby(new PacketMachine(this, PACKET_STATUS),
				worldObj.provider.dimensionId, xCoord, yCoord, zCoord,
				STATUS_SYNC_RADIUS);
		lastSyncedState = generatorState;
		lastSyncedPower = powerMadeLastTick;
		lastSyncedProgress = progress;
		lastSyncedTarget = currentTargetId;
		ticksSinceStatusSync = 0;
	}

	private String buildStatusText() {
		String stateName = localize(generatorState.getLocalizationKey());
		String targetName = getTargetDisplayName();

		return format("msg.blackholegenerator.gui.state", stateName)
				+ "\n"
				+ format("msg.blackholegenerator.gui.power",
						Integer.valueOf(powerMadeLastTick))
				+ "\n"
				+ format("msg.blackholegenerator.gui.remaining",
						Integer.valueOf(Math.max(0, burnTicksRemaining)),
						Double.valueOf(Math.max(0, burnTicksRemaining) / 20D))
				+ "\n"
				+ format("msg.blackholegenerator.gui.target", targetName);
	}

	private String getTargetDisplayName() {
		if(currentTargetId == BlackHoleGeneratorEligibility.NO_TARGET)
			return localize("msg.blackholegenerator.target.none");
		if(currentTargetId == SpaceObjectManager.WARPDIMID)
			return localize("msg.blackholegenerator.target.warp");

		StationTarget target =
				StationTargetResolver.getInstance().resolve(currentTargetId);
		if(target.getKind() == StationTarget.Kind.BLACK_HOLE_STAR
				&& target.getStellarBody() != null)
			return target.getStellarBody().getName();
		if(target.getKind() == StationTarget.Kind.DIMENSION
				&& target.getDimensionProperties() != null)
			return target.getDimensionProperties().getName();

		return format("msg.blackholegenerator.target.invalid",
				Integer.valueOf(currentTargetId));
	}

	private static String localize(String key) {
		return LibVulpes.proxy.getLocalizedString(key);
	}

	private static String format(String key, Object... arguments) {
		String pattern = localize(key);
		try {
			return String.format(pattern, arguments);
		}
		catch(RuntimeException ex) {
			return pattern;
		}
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		super.writeDataToNetwork(out, id);
		if(id == PACKET_STATUS) {
			out.writeByte(generatorState.getNetworkCode());
			out.writeInt(powerMadeLastTick);
			out.writeInt(Math.max(0, burnTicksRemaining));
			out.writeInt(Math.max(0, currentBurnTotal));
			out.writeByte(getProgressBucket());
			out.writeInt(currentTargetId);
		}
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		super.readDataFromNetwork(in, packetId, nbt);
		if(packetId == PACKET_STATUS) {
			nbt.setByte(NBT_STATE, in.readByte());
			nbt.setInteger(NBT_POWER, in.readInt());
			nbt.setInteger(NBT_BURN_REMAINING, in.readInt());
			nbt.setInteger(NBT_BURN_TOTAL, in.readInt());
			nbt.setByte(NBT_PROGRESS, in.readByte());
			nbt.setInteger(NBT_TARGET, in.readInt());
		}
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		super.useNetworkData(player, side, id, nbt);
		if(id == PACKET_STATUS && side == Side.CLIENT)
			readGeneratorNetworkData(nbt);
	}

	@Override
	protected void writeNetworkData(NBTTagCompound nbt) {
		super.writeNetworkData(nbt);
		nbt.setByte(NBT_STATE, generatorState.getNetworkCode());
		nbt.setInteger(NBT_POWER, Math.max(0, powerMadeLastTick));
		nbt.setInteger(NBT_BURN_REMAINING,
				Math.max(0, burnTicksRemaining));
		nbt.setInteger(NBT_BURN_TOTAL, Math.max(0, currentBurnTotal));
		nbt.setByte(NBT_PROGRESS, (byte)getProgressBucket());
		nbt.setInteger(NBT_TARGET, currentTargetId);
	}

	@Override
	protected void readNetworkData(NBTTagCompound nbt) {
		super.readNetworkData(nbt);
		readGeneratorNetworkData(nbt);
	}

	private void readGeneratorNetworkData(NBTTagCompound nbt) {
		generatorState = BlackHoleGeneratorState.fromNetworkCode(
				nbt.getByte(NBT_STATE));
		powerMadeLastTick = Math.max(0, nbt.getInteger(NBT_POWER));
		burnTicksRemaining =
				Math.max(0, nbt.getInteger(NBT_BURN_REMAINING));
		currentBurnTotal = Math.max(burnTicksRemaining,
				nbt.getInteger(NBT_BURN_TOTAL));
		currentTargetId = nbt.hasKey(NBT_TARGET)
				? nbt.getInteger(NBT_TARGET)
				: BlackHoleGeneratorEligibility.NO_TARGET;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		// These fields are runtime/network projections. Only relative burn
		// progress is authoritative across chunk unloads.
		nbt.removeTag(NBT_STATE);
		nbt.removeTag(NBT_POWER);
		nbt.removeTag(NBT_PROGRESS);
		nbt.removeTag(NBT_TARGET);
		nbt.setInteger(NBT_BURN_REMAINING,
				Math.max(0, burnTicksRemaining));
		nbt.setInteger(NBT_BURN_TOTAL,
				Math.max(burnTicksRemaining, currentBurnTotal));
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		burnTicksRemaining =
				Math.max(0, nbt.getInteger(NBT_BURN_REMAINING));
		int persistedTotal = nbt.hasKey(NBT_BURN_TOTAL)
				? nbt.getInteger(NBT_BURN_TOTAL)
				: burnTicksRemaining;
		currentBurnTotal = Math.max(1,
				Math.max(burnTicksRemaining, persistedTotal));

		// Recalculate all derived state on the first server tick.
		generatorState = BlackHoleGeneratorState.INCOMPLETE;
		powerMadeLastTick = 0;
		currentTargetId = BlackHoleGeneratorEligibility.NO_TARGET;
		initialCheck = false;
		ticksSinceStatusSync = STATUS_SYNC_INTERVAL;
		lastSyncedState = null;
		lastSyncedPower = Integer.MIN_VALUE;
		lastSyncedProgress = Integer.MIN_VALUE;
		lastSyncedTarget = Integer.MIN_VALUE;
	}
}
