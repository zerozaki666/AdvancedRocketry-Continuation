package zmaster587.advancedRocketry.tile.station;

import io.netty.buffer.ByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersControllerAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersControllerAccess.Result;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersValueCodec;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.network.PacketStationUpdate;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.world.provider.WorldProviderStation;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.ISliderBar;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleSlider;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.util.INetworkMachine;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;

@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")
public class TileStationAltitudeController extends TileEntity implements IModularInventory, INetworkMachine, ISliderBar, SimpleComponent {

	private static final int TARGET_ALTITUDE_SLIDER_ID = 0;
	private static final int ALTITUDE_CHANGE_RATE_SLIDER_ID = 1;
	private static final String ALTITUDE_CHANGE_RATE_NBT =
			"altitudeChangeRateProgress";
	private static final String NETWORK_PROGRESS_NBT = "progress";

	int gravity;
	int progress;
	int altitudeChangeRateProgress;

	private ModuleText moduleGrav, numGravPylons, maxGravBuildSpeed, targetGrav;

	public TileStationAltitudeController() {
		gravity = OpenComputersValueCodec.MINIMUM_ORBITAL_DISTANCE;
		moduleGrav = new ModuleText(6, 15, "Altitude: ", 0xaa2020);
		//numGravPylons = new ModuleText(10, 25, "Number Of Thrusters: ", 0xaa2020);
		targetGrav = new ModuleText(6, 30, LibVulpes.proxy.getLocalizedString("msg.stationaltctrl.tgtalt"), 0x202020);
		maxGravBuildSpeed = new ModuleText(6, 60, LibVulpes.proxy.getLocalizedString("msg.stationaltctrl.maxaltrate"), 0xaa2020);
	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();
		modules.add(moduleGrav);
		//modules.add(numThrusters);
		modules.add(maxGravBuildSpeed);

		modules.add(targetGrav);
		modules.add(new ModuleSlider(6, 40,
				TARGET_ALTITUDE_SLIDER_ID,
				TextureResources.doubleWarningSideBarIndicator,
				(ISliderBar)this));
		modules.add(new ModuleSlider(6, 70,
				ALTITUDE_CHANGE_RATE_SLIDER_ID,
				TextureResources.doubleWarningSideBarIndicator,
				(ISliderBar)this));

		updateText();
		return modules;
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setInteger("gravity", gravity);
		nbt.setInteger(ALTITUDE_CHANGE_RATE_NBT,
				altitudeChangeRateProgress);

		S35PacketUpdateTileEntity packet = new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
		return packet;
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		NBTTagCompound nbt = pkt.func_148857_g();
		progress = clampTargetAltitudeProgress(
				nbt.getInteger("gravity")
				-OpenComputersValueCodec.MINIMUM_ORBITAL_DISTANCE);
		gravity = progress
				+OpenComputersValueCodec.MINIMUM_ORBITAL_DISTANCE;
		altitudeChangeRateProgress =
				StationAltitudeChangeRate.clampSliderProgress(
						nbt.getInteger(ALTITUDE_CHANGE_RATE_NBT));
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	private void updateText() {
		if(worldObj.isRemote) {
			ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(xCoord, zCoord);
			if(object != null) {
				moduleGrav.setText(String.format("%s %.0fKm",LibVulpes.proxy.getLocalizedString("msg.stationaltctrl.alt"), object.getOrbitalDistance()*200 + 100 ));
			}

			//numThrusters.setText("Number Of Thrusters: 0");

			maxGravBuildSpeed.setText(String.format("%s%.1fx",
					LibVulpes.proxy.getLocalizedString(
							"msg.stationaltctrl.maxaltrate"),
					StationAltitudeChangeRate.getMultiplier(
							altitudeChangeRateProgress)));
			targetGrav.setText(String.format("%s %d", LibVulpes.proxy.getLocalizedString("msg.stationaltctrl.tgtalt"), gravity*200 + 100));
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if(this.worldObj.provider instanceof WorldProviderStation) {

			if(!worldObj.isRemote) {
				ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(this.xCoord, this.zCoord);

				if(object != null) {
					
					double targetGravity = gravity;
					double angVel = object.getOrbitalDistance();
					double legacyAltitudeStep =
							StationAltitudeChangeRate.getLegacyStep(
									getTotalProgress(
											TARGET_ALTITUDE_SLIDER_ID),
									angVel);
					double finalVel = StationAltitudeChangeRate
							.moveTowards(angVel, targetGravity,
									legacyAltitudeStep,
									altitudeChangeRateProgress);

					if(finalVel != angVel) {
						object.setOrbitalDistance((float)finalVel);
						if(!worldObj.isRemote) {
							//PacketHandler.sendToNearby(new PacketStationUpdate(object, PacketStationUpdate.Type.ROTANGLE_UPDATE), this.worldObj.provider.dimensionId, this.xCoord, this.yCoord, this.zCoord, 1024);
							PacketHandler.sendToAll(new PacketStationUpdate(object, PacketStationUpdate.Type.ALTITUDE_UPDATE));
						}
						else
							updateText();
					}
				}
			}
			else
				updateText();
		}
	}
	
	@Override
	public String getModularInventoryName() {
		return "tile.altitudeController.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == TARGET_ALTITUDE_SLIDER_ID
				|| id == ALTITUDE_CHANGE_RATE_SLIDER_ID)
			out.writeShort(getProgress(id));
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == TARGET_ALTITUDE_SLIDER_ID
				|| packetId == ALTITUDE_CHANGE_RATE_SLIDER_ID)
			nbt.setShort(NETWORK_PROGRESS_NBT, in.readShort());
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(side == Side.SERVER
				&& (id == TARGET_ALTITUDE_SLIDER_ID
				|| id == ALTITUDE_CHANGE_RATE_SLIDER_ID)) {
			setProgress(id, nbt.getShort(NETWORK_PROGRESS_NBT));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setShort("numRotations", (short)gravity);
		nbt.setShort(ALTITUDE_CHANGE_RATE_NBT,
				(short)altitudeChangeRateProgress);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		gravity = nbt.hasKey("numRotations")
				? nbt.getShort("numRotations")
				: OpenComputersValueCodec.MINIMUM_ORBITAL_DISTANCE;
		progress = clampTargetAltitudeProgress(gravity
				-OpenComputersValueCodec.MINIMUM_ORBITAL_DISTANCE);
		gravity = progress
				+OpenComputersValueCodec.MINIMUM_ORBITAL_DISTANCE;
		altitudeChangeRateProgress =
				StationAltitudeChangeRate.clampSliderProgress(
						nbt.getShort(ALTITUDE_CHANGE_RATE_NBT));
	}


	@Override
	public float getNormallizedProgress(int id) {
		return getProgress(id)/(float)getTotalProgress(id);
	}

	@Override
	public void setProgress(int id, int progress) {
		if(id == TARGET_ALTITUDE_SLIDER_ID) {
			setTargetAltitudeProgress(progress);
		}
		else if(id == ALTITUDE_CHANGE_RATE_SLIDER_ID)
			setAltitudeChangeRateProgress(progress);
	}

	@Override
	public int getProgress(int id) {
		if(id == TARGET_ALTITUDE_SLIDER_ID)
			return progress;
		if(id == ALTITUDE_CHANGE_RATE_SLIDER_ID)
			return altitudeChangeRateProgress;
		return 0;
	}

	@Override
	public int getTotalProgress(int id) {
		if(id == TARGET_ALTITUDE_SLIDER_ID)
			return 190;
		if(id == ALTITUDE_CHANGE_RATE_SLIDER_ID)
			return StationAltitudeChangeRate.SLIDER_STEPS;
		return 1;
	}

	@Override
	public void setTotalProgress(int id, int progress) {

	}

	@Override
	public void setProgressByUser(int id, int progress) {
		setProgress(id, progress);
		updateText();
		PacketHandler.sendToServer(new PacketMachine(this, (byte)id));
	}

	int setTargetAltitudeProgress(int targetProgress) {
		int effectiveProgress = clampTargetAltitudeProgress(targetProgress);
		int effectiveGravity = effectiveProgress
				+OpenComputersValueCodec.MINIMUM_ORBITAL_DISTANCE;
		if(progress != effectiveProgress || gravity != effectiveGravity) {
			progress = effectiveProgress;
			gravity = effectiveGravity;
			onTargetChanged();
		}
		return effectiveProgress;
	}

	int setAltitudeChangeRateProgress(int targetProgress) {
		int effectiveProgress =
				StationAltitudeChangeRate.clampSliderProgress(
						targetProgress);
		if(altitudeChangeRateProgress != effectiveProgress) {
			altitudeChangeRateProgress = effectiveProgress;
			onTargetChanged();
		}
		return effectiveProgress;
	}

	private int clampTargetAltitudeProgress(int targetProgress) {
		return Math.max(0, Math.min(
				getTotalProgress(TARGET_ALTITUDE_SLIDER_ID),
				targetProgress));
	}

	private void onTargetChanged() {
		if(worldObj != null && !worldObj.isRemote) {
			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "altitude_controller";
	}

	@Callback(doc = "function(targetKm:number):boolean, number|string -- Sets the target station altitude in km; valid range 2100..40100, quantized to 200 km.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setTargetAltitude(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asMutatorError();

		double targetKm = args.checkDouble(0);
		if(!OpenComputersValueCodec.isFinite(targetKm))
			return OpenComputersControllerAccess.mutatorError(
					"not_finite", "Target altitude must be finite.");
		if(!OpenComputersValueCodec.isInRange(targetKm,
				OpenComputersValueCodec.MINIMUM_ALTITUDE_KM,
				OpenComputersValueCodec.MAXIMUM_ALTITUDE_KM))
			return OpenComputersControllerAccess.mutatorError(
					"out_of_range",
					"Target altitude must be between 2100 and 40100 km.");

		int effectiveProgress = setTargetAltitudeProgress(
				OpenComputersValueCodec.altitudeProgressFromKm(targetKm));
		return new Object[] { true,
				OpenComputersValueCodec.altitudeKmFromProgress(
						effectiveProgress) };
	}

	@Callback(doc = "function():number -- Returns the saved target station altitude in km.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getTargetAltitude(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] {
				OpenComputersValueCodec.altitudeKmFromProgress(progress) };
	}

	@Callback(doc = "function():number -- Returns the current station altitude in km.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getCurrentAltitude(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { OpenComputersValueCodec.currentAltitudeKm(
				access.getSpaceObject().getOrbitalDistance()) };
	}

	@Callback(doc = "function(multiplier:number):boolean, number|string -- Sets the altitude change-rate multiplier; valid range 1.0..10.0, quantized to 0.5.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setChangeRateMultiplier(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asMutatorError();

		double multiplier = args.checkDouble(0);
		if(!OpenComputersValueCodec.isFinite(multiplier))
			return OpenComputersControllerAccess.mutatorError(
					"not_finite", "Change-rate multiplier must be finite.");
		if(!OpenComputersValueCodec.isInRange(multiplier,
				StationAltitudeChangeRate.MINIMUM_MULTIPLIER,
				StationAltitudeChangeRate.MAXIMUM_MULTIPLIER))
			return OpenComputersControllerAccess.mutatorError(
					"out_of_range",
					"Change-rate multiplier must be between 1.0 and 10.0.");

		int effectiveProgress = setAltitudeChangeRateProgress(
				OpenComputersValueCodec
						.changeRateProgressFromMultiplier(multiplier));
		return new Object[] { true,
				OpenComputersValueCodec
						.changeRateMultiplierFromProgress(
								effectiveProgress) };
	}

	@Callback(doc = "function():number -- Returns the saved altitude change-rate multiplier.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getChangeRateMultiplier(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { OpenComputersValueCodec
				.changeRateMultiplierFromProgress(
						altitudeChangeRateProgress) };
	}

	@Callback(doc = "function():table -- Returns station altitude, target, direction, rate multiplier, and station id.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStatus(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();

		ISpaceObject object = access.getSpaceObject();
		double currentAltitude = OpenComputersValueCodec.currentAltitudeKm(
				object.getOrbitalDistance());
		double targetAltitude =
				OpenComputersValueCodec.altitudeKmFromProgress(progress);
		String state = Math.abs(currentAltitude-targetAltitude) <= 0.01D
				? "idle"
				: currentAltitude < targetAltitude
						? "ascending" : "descending";

		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("stationId", object.getId());
		status.put("state", state);
		status.put("currentAltitude", currentAltitude);
		status.put("targetAltitude", targetAltitude);
		status.put("changeRateMultiplier", OpenComputersValueCodec
				.changeRateMultiplierFromProgress(
						altitudeChangeRateProgress));
		return new Object[] { status };
	}
}
