package zmaster587.advancedRocketry.tile.infrastructure;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.EntityRocketBase;
import zmaster587.advancedRocketry.api.IInfrastructure;
import zmaster587.advancedRocketry.api.StatsRocket;
import zmaster587.advancedRocketry.api.fuel.FuelRegistry.FuelType;
import zmaster587.advancedRocketry.api.satellite.SatelliteBase;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.entity.EntityRocket;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess.Result;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersFuelTypeCodec;
import zmaster587.advancedRocketry.api.IMission;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.client.util.IndicatorBarImage;
import zmaster587.libVulpes.client.util.ProgressBarImage;
import zmaster587.libVulpes.interfaces.ILinkableTile;
import zmaster587.libVulpes.inventory.modules.IButtonInventory;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.IProgressBar;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleButton;
import zmaster587.libVulpes.inventory.modules.ModuleProgress;
import zmaster587.libVulpes.inventory.modules.ModuleRedstoneOutputButton;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.items.ItemLinker;
import zmaster587.libVulpes.network.PacketEntity;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.util.IAdjBlockUpdate;
import zmaster587.libVulpes.util.INetworkMachine;
import zmaster587.libVulpes.util.ZUtils.RedstoneState;

@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")
public class TileEntityMoniteringStation extends TileEntity  implements IModularInventory, IAdjBlockUpdate, IInfrastructure, ILinkableTile, INetworkMachine, IButtonInventory, IProgressBar, SimpleComponent  {

	EntityRocketBase linkedRocket;
	IMission mission;
	ModuleText missionText;
	RedstoneState state;
	ModuleRedstoneOutputButton redstoneControl;

	int rocketHeight;
	int velocity;
	int fuelLevel, maxFuelLevel;


	public TileEntityMoniteringStation() {
		mission = null;
		missionText = new ModuleText(20, 90, LibVulpes.proxy.getLocalizedString("msg.monitoringStation.missionProgressNA"), 0x2b2b2b);
		redstoneControl = new ModuleRedstoneOutputButton(174, 4, -1, "", this);
		state = RedstoneState.ON;
	}

	@Override
	public void invalidate() {
		super.invalidate();

		if(linkedRocket != null) {
			linkedRocket.unlinkInfrastructure(this);
			unlinkRocket();
		}
		if(mission != null) {
			mission.unlinkInfrastructure(this);
			unlinkMission();
		}
	}

	public boolean getEquivilentPower() {
		if(state == RedstoneState.OFF)
			return false;

		boolean state2 = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);

		if(state == RedstoneState.INVERTED)
			state2 = !state2;
		return state2;
	}

	@Override
	public void onAdjacentBlockUpdated() {
		if(!worldObj.isRemote && getEquivilentPower() && linkedRocket != null) {
			linkedRocket.prepareLaunch();
		}
	}

	@Override
	public int getMaxLinkDistance() {
		return 300000;
	}

	@Override
	public boolean onLinkStart(ItemStack item, TileEntity entity,
			EntityPlayer player, World world) {

		ItemLinker.setMasterCoords(item, this.xCoord, this.yCoord, this.zCoord);

		if(player.worldObj.isRemote)
			Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage((new ChatComponentText(LibVulpes.proxy.getLocalizedString("msg.monitoringStation.link") + ": " + this.xCoord + " " + this.yCoord + " " + this.zCoord)));
		return true;
	}

	@Override
	public boolean onLinkComplete(ItemStack item, TileEntity entity,
			EntityPlayer player, World world) {
		if(player.worldObj.isRemote)
			Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage((new ChatComponentText(LibVulpes.proxy.getLocalizedString("msg.linker.error.firstMachine"))));
		return false;
	}

	@Override
	public void unlinkRocket() {
		linkedRocket = null;
	}

	@Override
	public boolean disconnectOnLiftOff() {
		return false;
	}

	@Override
	public boolean linkRocket(EntityRocketBase rocket) {
		this.linkedRocket = rocket;
		return true;
	}

	public EntityRocketBase getLinkedRocketForAutomation() {
		return linkedRocket != null && !linkedRocket.isDead
				? linkedRocket : null;
	}

	public IMission getLinkedMissionForAutomation() {
		return mission;
	}
	

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		state = RedstoneState.values()[nbt.getByte("redstoneState")];
		redstoneControl.setRedstoneState(state);

		if(nbt.hasKey("missionID")) {
			long id = nbt.getLong("missionID");
			int dimid = nbt.getInteger("missionDimId");

			SatelliteBase sat = DimensionManager.getInstance().getSatellite(id);

			if(sat instanceof IMission)
				mission = (IMission)sat;
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setByte("redstoneState", (byte) state.ordinal());
		if(mission != null) {
			nbt.setLong("missionID", mission.getMissionId());
			nbt.setInteger("missionDimId", mission.getOriginatingDimention());
		}
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == 1)
			out.writeLong(mission == null ? -1 : mission.getMissionId());
		else if(id == 2)
			out.writeByte(state.ordinal());
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {

		if(packetId == 1) {
			nbt.setLong("id", in.readLong());
		}
		else if(packetId == 2) {
			nbt.setByte("state", in.readByte());
		}
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(id == 1) {
			long idNum = nbt.getLong("id");
			if(idNum == -1) {
				mission = null;
				setMissionText();
			}
			else {
				SatelliteBase base = DimensionManager.getInstance().getSatellite(idNum);

				if(base instanceof IMission) {
					mission = (IMission)base;
					setMissionText();
				}
			}
		}
		else if(id == 2) {
			state = RedstoneState.values()[nbt.getByte("state")];
			redstoneControl.setRedstoneState(state);
		}
		if(id == 100) {
			if(linkedRocket != null)
				linkedRocket.prepareLaunch();
		}
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setByte("state", (byte)state.ordinal());
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		state = RedstoneState.values()[pkt.func_148857_g().getByte("state")];
		redstoneControl.setRedstoneState(state);
		super.onDataPacket(net, pkt);
	}

	@Override
	public List<ModuleBase> getModules(int ID, EntityPlayer player) {

		LinkedList<ModuleBase> modules = new LinkedList<ModuleBase>();

		modules.add(new ModuleButton(20, 40, 0, "Launch!", this,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild));
		modules.add(new ModuleProgress(98, 4, 0, new IndicatorBarImage(2, 7, 12, 81, 17, 0, 6, 6, 1, 0, ForgeDirection.UP, TextureResources.rocketHud), this));
		modules.add(new ModuleProgress(120, 14, 1, new IndicatorBarImage(2, 95, 12, 71, 17, 0, 6, 6, 1, 0, ForgeDirection.UP, TextureResources.rocketHud), this));
		modules.add(new ModuleProgress(142, 14, 2, new ProgressBarImage(2, 173, 12, 71, 17, 6, 3, 69, 1, 1, ForgeDirection.UP, TextureResources.rocketHud), this));

		modules.add(redstoneControl);
		setMissionText();

		modules.add(missionText);
		modules.add(new ModuleProgress(30, 110, 3, TextureResources.progressToMission, this));
		modules.add(new ModuleProgress(30, 120, 4, TextureResources.workMission, this));
		modules.add(new ModuleProgress(30, 130, 5, TextureResources.progressFromMission, this));

		if(!worldObj.isRemote) {
			PacketHandler.sendToPlayer(new PacketMachine(this, (byte)1), player);
		}

		return modules;
	}

	private void setMissionText() {
		if(mission != null) {
			int time = mission.getTimeRemainingInSeconds();
			int seconds = time % 60;
			int minutes = (time/60) % 60;
			int hours = time/3600;
			
			missionText.setText(((SatelliteBase)mission).getName() + LibVulpes.proxy.getLocalizedString("msg.monitoringStation.progress") + String.format("\n%02dhr:%02dm:%02ds", hours, minutes, seconds));
		}
		else
			missionText.setText(LibVulpes.proxy.getLocalizedString("msg.monitoringStation.missionProgressNA"));
	}

	@Override
	public void onInventoryButtonPressed(int buttonId) {
		if(buttonId != -1)
			PacketHandler.sendToServer(new PacketMachine(this, (byte) (buttonId + 100)) );
		else {
			state = redstoneControl.getState();
			PacketHandler.sendToServer(new PacketMachine(this, (byte)2));
		}

	}

	@Override
	public String getModularInventoryName() {
		return "container.monitoringstation";
	}

	@Override
	public float getNormallizedProgress(int id) {
		if(id == 1) {
			return Math.max(Math.min(0.5f + (getProgress(id)/(float)getTotalProgress(id)), 1), 0f);
		}
		else if(id == 3) {
			if(mission == null)
				return 0f;
			return (float) Math.min(3f*mission.getProgress(this.worldObj), 1f);
		}
		else if(id == 4) {
			if(mission == null)
				return 0f;
			return (float) Math.min(Math.max( 3f*(mission.getProgress(this.worldObj) - 0.333f), 0f), 1f);
		}
		else if(id == 5) {
			if(mission == null)
				return 0f;
			return (float) Math.min(Math.max( 3f*(mission.getProgress(this.worldObj) - 0.666f), 0f), 1f);
		}

		//keep text updated
		if(worldObj.isRemote && mission != null)
			setMissionText();

		return getProgress(id)/(float)getTotalProgress(id);
	}

	@Override
	public void setProgress(int id, int progress) {
		if(id == 0)
			rocketHeight = progress;
		else if(id == 1)
			velocity = progress;
		else if(id == 2)
			fuelLevel = progress;
	}

	@Override
	public int getProgress(int id) {
		//Try to keep client synced with server, this also allows us to put the monitor on a different world altogether
		if(worldObj.isRemote)
			if(mission != null && id == 0)
				return getTotalProgress(id);
			else if(id == 0)
				return rocketHeight;
			else if(id == 1)
				return velocity;
			else if(id == 2)
				return fuelLevel;

		if(linkedRocket == null)
			return 0;
		if(id == 0)
			return (int)linkedRocket.posY;
		else if(id == 1)
			return (int)(linkedRocket.motionY*100);
		else if (id == 2)
			return (int)(linkedRocket.getFuelAmount());

		return 0;
	}

	@Override
	public int getTotalProgress(int id) {
		if(id == 0)
			return Configuration.orbit;
		else if(id == 1)
			return 200;
		else if(id == 2)
			if(worldObj.isRemote)
				return maxFuelLevel;
			else
				if(linkedRocket == null)
					return 0;
				else
					return linkedRocket.getFuelCapacity();

		return 1;
	}

	@Override
	public void setTotalProgress(int id, int progress) {
		//Should only become an issue if configs are desynced or fuel
		if(id == 2)
			maxFuelLevel = progress;
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public boolean linkMission(IMission misson) {
		this.mission = misson;
		PacketHandler.sendToNearby(new PacketMachine(this, (byte)1), worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 16);
		return true;
	}

	@Override
	public void unlinkMission() {
		mission = null;
		setMissionText();
		PacketHandler.sendToNearby(new PacketMachine(this, (byte)1), worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 16);
	}

	@Override
	public boolean canRenderConnection() {
		return false;
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "monitoring_station";
	}

	private Object[] rocketNotFound() {
		return OpenComputersComponentAccess.getterError("rocket_not_found",
				"Monitoring station has no linked rocket.");
	}

	private Object[] missionNotFound() {
		return OpenComputersComponentAccess.getterError("mission_not_found",
				"Monitoring station has no linked mission.");
	}

	private StatsRocket getLinkedStats() {
		EntityRocketBase rocket = getLinkedRocketForAutomation();
		return rocket == null ? null : rocket.getRocketStats();
	}

	private FuelType getFuelType(Arguments args) {
		return OpenComputersFuelTypeCodec.parse(args.checkString(0));
	}

	@Callback(doc = "function():boolean -- Returns whether a live rocket is linked.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isRocketLinked(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { getLinkedRocketForAutomation() != null };
	}

	@Callback(doc = "function():boolean, string -- Requests launch through the normal pre-launch path.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] prepareLaunch(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asMutatorError();
		EntityRocketBase rocket = getLinkedRocketForAutomation();
		if(rocket == null)
			return OpenComputersComponentAccess.mutatorError(
					"rocket_not_found",
					"Monitoring station has no linked rocket.");
		rocket.prepareLaunch();
		return new Object[] { true, "requested" };
	}

	@Callback(doc = "function():boolean, string -- Safe alias for prepareLaunch; never calls raw launch.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] launch(Context context, Arguments args) {
		return prepareLaunch(context, args);
	}

	@Callback(doc = "function():number -- Returns linked rocket height or mission orbit estimate.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getRocketHeight(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		EntityRocketBase rocket = getLinkedRocketForAutomation();
		if(rocket != null)
			return new Object[] { rocket.posY };
		if(getLinkedMissionForAutomation() != null)
			return new Object[] { Configuration.orbit };
		return rocketNotFound();
	}

	@Callback(doc = "function():number -- Returns linked rocket vertical velocity.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getRocketVelocity(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		EntityRocketBase rocket = getLinkedRocketForAutomation();
		return rocket == null ? rocketNotFound()
				: new Object[] { rocket.motionY };
	}

	@Callback(doc = "function():number -- Returns linked rocket thrust.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getRocketThrust(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		return stats == null ? rocketNotFound()
				: new Object[] { stats.getThrust() };
	}

	@Callback(doc = "function():number -- Returns linked rocket weight.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getRocketWeight(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		return stats == null ? rocketNotFound()
				: new Object[] { stats.getWeight() };
	}

	@Callback(doc = "function():number -- Returns linked rocket drilling power.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getDrillingPower(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		return stats == null ? rocketNotFound()
				: new Object[] { stats.getDrillingPower() };
	}

	@Callback(doc = "function():number -- Returns linked rocket acceleration in native 1.7.10 units.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getAcceleration(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		return stats == null ? rocketNotFound()
				: new Object[] { stats.getAcceleration() };
	}

	@Callback(doc = "function(type:string):number -- Returns amount of a named rocket fuel type.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFuelAmount(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		if(stats == null)
			return rocketNotFound();
		FuelType type = getFuelType(args);
		if(type == null)
			return OpenComputersComponentAccess.getterError(
					"fuel_type_not_found", "Unknown rocket fuel type.");
		return new Object[] { stats.getFuelAmount(type) };
	}

	@Callback(doc = "function(type:string):number -- Returns capacity of a named rocket fuel type.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFuelCapacity(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		if(stats == null)
			return rocketNotFound();
		FuelType type = getFuelType(args);
		if(type == null)
			return OpenComputersComponentAccess.getterError(
					"fuel_type_not_found", "Unknown rocket fuel type.");
		return new Object[] { stats.getFuelCapacity(type) };
	}

	@Callback(doc = "function(type:string):number -- Returns per-tick rate of a named rocket fuel type.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFuelRate(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		if(stats == null)
			return rocketNotFound();
		FuelType type = getFuelType(args);
		if(type == null)
			return OpenComputersComponentAccess.getterError(
					"fuel_type_not_found", "Unknown rocket fuel type.");
		return new Object[] { stats.getFuelRate(type) };
	}

	@Callback(doc = "function(type:string):table -- Returns amount, capacity, and rate for a named rocket fuel type.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFuelStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		if(stats == null)
			return rocketNotFound();
		FuelType type = getFuelType(args);
		if(type == null)
			return OpenComputersComponentAccess.getterError(
					"fuel_type_not_found", "Unknown rocket fuel type.");
		Map<String, Object> fuel = new LinkedHashMap<String, Object>();
		fuel.put("type", OpenComputersFuelTypeCodec.name(type));
		fuel.put("amount", stats.getFuelAmount(type));
		fuel.put("capacity", stats.getFuelCapacity(type));
		fuel.put("rate", stats.getFuelRate(type));
		return new Object[] { fuel };
	}

	@Callback(doc = "function():boolean -- Returns whether the linked rocket has a pilot seat.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] hasSeat(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		StatsRocket stats = getLinkedStats();
		return stats == null ? rocketNotFound()
				: new Object[] { stats.hasSeat() };
	}

	@Callback(doc = "function():table -- Returns a linked rocket state and stats snapshot.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getRocketStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		EntityRocketBase rocket = getLinkedRocketForAutomation();
		StatsRocket stats = getLinkedStats();
		if(rocket == null || stats == null)
			return rocketNotFound();
		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("height", rocket.posY);
		status.put("velocity", rocket.motionY);
		status.put("thrust", stats.getThrust());
		status.put("weight", stats.getWeight());
		status.put("drillingPower", stats.getDrillingPower());
		status.put("acceleration", stats.getAcceleration());
		status.put("hasSeat", stats.hasSeat());
		status.put("alive", !rocket.isDead);
		return new Object[] { status };
	}

	@Callback(doc = "function():boolean -- Returns whether a mission is linked.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isMissionLinked(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { getLinkedMissionForAutomation() != null };
	}

	@Callback(doc = "function():number -- Returns normalized linked mission progress.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getMissionProgress(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		IMission linkedMission = getLinkedMissionForAutomation();
		if(linkedMission == null)
			return missionNotFound();
		double progress = linkedMission.getProgress(worldObj);
		return new Object[] { Math.max(0.0D, Math.min(1.0D, progress)) };
	}

	@Callback(doc = "function():number -- Returns linked mission remaining time in seconds.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getMissionRemainingTime(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		IMission linkedMission = getLinkedMissionForAutomation();
		return linkedMission == null ? missionNotFound()
				: new Object[] { Math.max(0,
						linkedMission.getTimeRemainingInSeconds()) };
	}

	@Callback(doc = "function():table -- Returns linked mission id, origin, progress, and remaining seconds.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getMissionStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		IMission linkedMission = getLinkedMissionForAutomation();
		if(linkedMission == null)
			return missionNotFound();
		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("missionId", String.valueOf(
				linkedMission.getMissionId()));
		status.put("originDimension",
				linkedMission.getOriginatingDimention());
		status.put("progress", Math.max(0.0D, Math.min(1.0D,
				linkedMission.getProgress(worldObj))));
		status.put("remainingSeconds", Math.max(0,
				linkedMission.getTimeRemainingInSeconds()));
		return new Object[] { status };
	}
}
