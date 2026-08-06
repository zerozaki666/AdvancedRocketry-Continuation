package zmaster587.advancedRocketry.tile;

import io.netty.buffer.ByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import zmaster587.advancedRocketry.api.IAtmosphere;
import zmaster587.advancedRocketry.atmosphere.AtmosphereHandler;
import zmaster587.advancedRocketry.atmosphere.AtmosphereType;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess.Result;
import zmaster587.advancedRocketry.util.SealableBlockHandler;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.IToggleButton;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.inventory.modules.ModuleToggleSwitch;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.util.BlockPosition;
import zmaster587.libVulpes.util.INetworkMachine;

/**
 * A zero-power, six-side breathability detector intended for airlock control.
 * Sealed adjacent cells are ignored; exposed cells are sampled from AR's
 * atmosphere graph and aggregated in either ANY or ALL mode.
 */
@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")
public class TileOxygenDetector extends TileEntity implements
		IModularInventory, IToggleButton, INetworkMachine, SimpleComponent {

	private static final byte PACKET_MODE = 0;
	private static final String NBT_REQUIRE_ALL = "requireAllSides";

	private boolean requireAllSides;
	private ModuleToggleSwitch requireAllSwitch;

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && worldObj.getTotalWorldTime() % 10 == 0)
			refreshRedstoneOutput();
	}

	public boolean isRequireAllSides() {
		return requireAllSides;
	}

	public void setRequireAllSides(boolean requireAll) {
		if(requireAllSides == requireAll)
			return;
		requireAllSides = requireAll;
		if(requireAllSwitch != null)
			requireAllSwitch.setToggleState(requireAll);
		if(worldObj != null && !worldObj.isRemote) {
			markDirty();
			refreshRedstoneOutput();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	private void refreshRedstoneOutput() {
		SideSnapshot snapshot = readAllSides();
		boolean detected = OxygenDetectorLogic.shouldEmit(requireAllSides,
				snapshot.exposed, snapshot.breathable);
		int metadata = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
		if((metadata == 1) != detected) {
			worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord,
					detected ? 1 : 0, 3);
			notifyIndirectRedstoneNeighbors();
		}
	}

	/**
	 * Metadata notification reaches the detector's immediate neighbours, but a
	 * strongly powered solid block does not forward that update to redstone on
	 * its opposite side. Notify around every adjacent block so indirect power
	 * consumers recalculate when the detector switches on or off.
	 */
	private void notifyIndirectRedstoneNeighbors() {
		Block detectorBlock = worldObj.getBlock(xCoord, yCoord, zCoord);
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
			worldObj.notifyBlocksOfNeighborChange(
					xCoord + direction.offsetX,
					yCoord + direction.offsetY,
					zCoord + direction.offsetZ, detectorBlock);
	}

	private SideSnapshot readAllSides() {
		SideSnapshot snapshot = new SideSnapshot();
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
			SideReading reading = readSide(direction);
			snapshot.readings[direction.ordinal()] = reading;
			snapshot.exposed[direction.ordinal()] = reading.exposed;
			snapshot.breathable[direction.ordinal()] = reading.breathable;
		}
		return snapshot;
	}

	private SideReading readSide(ForgeDirection direction) {
		int sampleX = xCoord + direction.offsetX;
		int sampleY = yCoord + direction.offsetY;
		int sampleZ = zCoord + direction.offsetZ;
		boolean exposed = !SealableBlockHandler.INSTANCE.isBlockSealed(worldObj,
				new BlockPosition(sampleX, sampleY, sampleZ));

		IAtmosphere atmosphere = AtmosphereType.AIR;
		AtmosphereHandler handler = AtmosphereHandler.getOxygenHandler(
				worldObj.provider.dimensionId);
		if(handler != null)
			atmosphere = handler.getAtmosphereType(sampleX, sampleY, sampleZ);

		return new SideReading(direction, exposed,
				exposed && atmosphere != null && atmosphere.isBreathable());
	}

	private boolean isEmittingRedstone() {
		return worldObj.getBlockMetadata(xCoord, yCoord, zCoord) == 1;
	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();
		modules.add(new ModuleText(18, 28,
				LibVulpes.proxy.getLocalizedString(
						"msg.oxygenDetector.requireAll"), 0x2d2d2d));
		modules.add(requireAllSwitch = new ModuleToggleSwitch(150, 20,
				PACKET_MODE, "", this,
				zmaster587.libVulpes.inventory.TextureResources.buttonToggleImage,
				LibVulpes.proxy.getLocalizedString(
						"msg.oxygenDetector.requireAll.tooltip"),
				11, 26, requireAllSides));
		return modules;
	}

	@Override
	public String getModularInventoryName() {
		return "tile.oxygenDetector.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer player) {
		return worldObj != null
				&& worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
				&& player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D,
						zCoord + 0.5D) <= 64D;
	}

	@Override
	public void onInventoryButtonPressed(int buttonId) {
		if(buttonId == PACKET_MODE && requireAllSwitch != null) {
			setRequireAllSides(requireAllSwitch.getState());
			PacketHandler.sendToServer(new PacketMachine(this, PACKET_MODE));
		}
	}

	@Override
	public void stateUpdated(ModuleBase module) {
		if(module == requireAllSwitch)
			setRequireAllSides(requireAllSwitch.getState());
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == PACKET_MODE)
			out.writeBoolean(requireAllSwitch == null ? requireAllSides
					: requireAllSwitch.getState());
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == PACKET_MODE)
			nbt.setBoolean(NBT_REQUIRE_ALL, in.readBoolean());
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(id == PACKET_MODE)
			setRequireAllSides(nbt.getBoolean(NBT_REQUIRE_ALL));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		writeDetectorState(nbt);
	}

	void writeDetectorState(NBTTagCompound nbt) {
		nbt.setBoolean(NBT_REQUIRE_ALL, requireAllSides);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		readDetectorState(nbt);
	}

	void readDetectorState(NBTTagCompound nbt) {
		requireAllSides = nbt.getBoolean(NBT_REQUIRE_ALL);
		if(requireAllSwitch != null)
			requireAllSwitch.setToggleState(requireAllSides);
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager network,
			S35PacketUpdateTileEntity packet) {
		readFromNBT(packet.func_148857_g());
	}

	private Object[] validateSide(Arguments args) {
		int side = args.checkInteger(0);
		if(side < 0 || side >= OxygenDetectorLogic.SIDE_COUNT)
			return OpenComputersComponentAccess.getterError("invalid_side",
					"Oxygen detector side must be between 0 and 5.");
		return null;
	}

	private Map<String, Object> sideInfo(SideReading reading) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("direction", reading.direction.name().toLowerCase(
				Locale.ENGLISH));
		result.put("exposed", reading.exposed);
		result.put("breathable", reading.breathable);
		return result;
	}

	private Map<String, Object> sidesInfo(SideSnapshot snapshot) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
			result.put(direction.name().toLowerCase(Locale.ENGLISH),
					sideInfo(snapshot.readings[direction.ordinal()]));
		return result;
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "oxygen_detector";
	}

	@Callback(doc = "function(side:number):table -- Returns exposure and breathability for one adjacent side (0..5).")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getSideStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		Object[] error = validateSide(args);
		if(error != null)
			return error;
		return new Object[] { sideInfo(readSide(ForgeDirection.getOrientation(
				args.checkInteger(0)))) };
	}

	@Callback(doc = "function():table -- Returns exposure and breathability for down, up, north, south, west, and east.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getSides(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { sidesInfo(readAllSides()) };
	}

	@Callback(doc = "function(side:number):boolean -- Returns whether one exposed adjacent side is breathable; blocked sides return false.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isSideBreathable(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		Object[] error = validateSide(args);
		if(error != null)
			return error;
		return new Object[] { readSide(ForgeDirection.getOrientation(
				args.checkInteger(0))).breathable };
	}

	@Callback(doc = "function():string -- Returns the redstone aggregation mode: any or all.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getDetectionMode(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { requireAllSides ? "all" : "any" };
	}

	@Callback(doc = "function(mode:string):boolean, string -- Sets the redstone aggregation mode to any or all.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setDetectionMode(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asMutatorError();
		String mode = args.checkString(0).toLowerCase(Locale.ENGLISH);
		if(!"any".equals(mode) && !"all".equals(mode))
			return OpenComputersComponentAccess.mutatorError("invalid_mode",
					"Oxygen detector mode must be any or all.");
		setRequireAllSides("all".equals(mode));
		return new Object[] { true, mode };
	}

	@Callback(doc = "function():boolean -- Returns whether the block is currently emitting redstone.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isDetected(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { isEmittingRedstone() };
	}

	@Callback(doc = "function():table -- Returns mode, redstone state, and all six side readings.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		SideSnapshot snapshot = readAllSides();
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("mode", requireAllSides ? "all" : "any");
		result.put("detected", isEmittingRedstone());
		result.put("sides", sidesInfo(snapshot));
		return new Object[] { result };
	}

	private static final class SideSnapshot {
		private final boolean[] exposed = new boolean[OxygenDetectorLogic.SIDE_COUNT];
		private final boolean[] breathable = new boolean[OxygenDetectorLogic.SIDE_COUNT];
		private final SideReading[] readings = new SideReading[OxygenDetectorLogic.SIDE_COUNT];
	}

	private static final class SideReading {
		private final ForgeDirection direction;
		private final boolean exposed;
		private final boolean breathable;

		private SideReading(ForgeDirection direction, boolean exposed,
				boolean breathable) {
			this.direction = direction;
			this.exposed = exposed;
			this.breathable = breathable;
		}
	}
}
