package zmaster587.advancedRocketry.tile.station;

import io.netty.buffer.ByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
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
import net.minecraftforge.common.util.ForgeDirection;
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

@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")
public class TileStationOrientationControl extends TileEntity implements IModularInventory, INetworkMachine, ISliderBar, SimpleComponent {

	private static final int NETWORK_PACKET_ID = 0;
	private static final String NETWORK_PROGRESS_X_NBT = "progressX";
	private static final String NETWORK_PROGRESS_Y_NBT = "progressY";
	private static final String NETWORK_PROGRESS_Z_NBT = "progressZ";

	int numRotationsPerHour[];
	int progress[];

	private ModuleText moduleAngularVelocity, numThrusters, maxAngularAcceleration, targetRotations;

	public TileStationOrientationControl() {
		moduleAngularVelocity = new ModuleText(6, 15, LibVulpes.proxy.getLocalizedString("msg.stationorientctrl.alt"), 0xaa2020);
		//numThrusters = new ModuleText(10, 25, "Number Of Thrusters: ", 0xaa2020);
		targetRotations = new ModuleText(6, 25, LibVulpes.proxy.getLocalizedString("msg.stationorientctrl.tgtalt"), 0x202020);
		progress = new int[3];
		numRotationsPerHour = new int[3];

		progress[0] = getTotalProgress(0)/2;
		progress[1] = getTotalProgress(1)/2;
		progress[2] = getTotalProgress(2)/2;
	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();
		modules.add(moduleAngularVelocity);
		//modules.add(numThrusters);
		//modules.add(maxAngularAcceleration);
		modules.add(targetRotations);
		
		modules.add(new ModuleText(10, 54, "X:", 0x202020));
		modules.add(new ModuleText(10, 69, "Y:", 0x202020)); //AYYYY
		
		modules.add(new ModuleSlider(24, 50, 0, TextureResources.doubleWarningSideBarIndicator, (ISliderBar)this));
		modules.add(new ModuleSlider(24, 65, 1, TextureResources.doubleWarningSideBarIndicator, (ISliderBar)this));
		//modules.add(new ModuleSlider(24, 35, 2, TextureResources.doubleWarningSideBarIndicator, (ISliderBar)this));

		updateText();
		return modules;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeTargetState(nbt);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0,
				nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net,
			S35PacketUpdateTileEntity pkt) {
		readTargetState(pkt.func_148857_g(), false);
	}

	private void updateText() {
		if(worldObj.isRemote) {
			ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(this.xCoord, this.zCoord);
			if(object != null) {
				moduleAngularVelocity.setText(String.format("%s%.1f %.1f %.1f", LibVulpes.proxy.getLocalizedString("msg.stationorientctrl.alt"), 72000D*object.getDeltaRotation(ForgeDirection.EAST), 72000D*object.getDeltaRotation(ForgeDirection.UP), 7200D*object.getDeltaRotation(ForgeDirection.NORTH)));
				//maxAngularAcceleration.setText(String.format("Maximum Angular Acceleration: %.1f", 7200D*object.getMaxRotationalAcceleration()));
			}

			//numThrusters.setText("Number Of Thrusters: 0");

			targetRotations.setText(String.format("%s%d %d %d", LibVulpes.proxy.getLocalizedString("msg.stationorientctrl.tgtalt"), numRotationsPerHour[0], numRotationsPerHour[1], numRotationsPerHour[2]));
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if(this.worldObj.provider instanceof WorldProviderStation) {
			if(!worldObj.isRemote) {
				ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(this.xCoord, this.zCoord);
				boolean update = false;

				if(object != null) {
					ForgeDirection dirs[] = { ForgeDirection.EAST, ForgeDirection.UP, ForgeDirection.NORTH };
					for(int i = 0; i < 3; i++) {
						double targetAngularVelocity = numRotationsPerHour[i]/72000D;
						double angVel = object.getDeltaRotation(dirs[i]);
						double acc = object.getMaxRotationalAcceleration();

						double difference = targetAngularVelocity - angVel;

						if(difference != 0) {
							double finalVel = angVel;
							if(difference < 0) {
								finalVel = angVel + Math.max(difference, -acc);
							}
							else if(difference > 0) {
								finalVel = angVel + Math.min(difference, acc);
							}

							object.setDeltaRotation(finalVel, dirs[i]);
							update = true;
						}
					}
					
					if(!worldObj.isRemote && update) {
						//PacketHandler.sendToNearby(new PacketStationUpdate(object, PacketStationUpdate.Type.ROTANGLE_UPDATE), this.worldObj.provider.dimensionId, this.xCoord, this.yCoord, this.zCoord, 1024);
						PacketHandler.sendToAll(new PacketStationUpdate(object, PacketStationUpdate.Type.ROTANGLE_UPDATE));
					}
				}
				else
					updateText();
			}
			else
				updateText();
		}
	}
	@Override
	public String getModularInventoryName() {
		return "tile.orientationControl.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == NETWORK_PACKET_ID) {
			out.writeShort(progress[0]);
			out.writeShort(progress[1]);
			out.writeShort(progress[2]);
		}
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == NETWORK_PACKET_ID) {
			nbt.setShort(NETWORK_PROGRESS_X_NBT, in.readShort());
			nbt.setShort(NETWORK_PROGRESS_Y_NBT, in.readShort());
			nbt.setShort(NETWORK_PROGRESS_Z_NBT, in.readShort());
		}
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(side == Side.SERVER && id == NETWORK_PACKET_ID)
			setTargetRotationRates(
					nbt.getShort(NETWORK_PROGRESS_X_NBT)
						-getTotalProgress(0)/2,
					nbt.getShort(NETWORK_PROGRESS_Y_NBT)
						-getTotalProgress(1)/2,
					nbt.getShort(NETWORK_PROGRESS_Z_NBT)
						-getTotalProgress(2)/2, true);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		writeTargetState(nbt);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		readTargetState(nbt, false);
	}


	@Override
	public float getNormallizedProgress(int id) {
		return getProgress(id)/(float)getTotalProgress(id);
	}

	@Override
	public void setProgress(int id, int progress) {
		if(isValidAxis(id))
			setTargetRotationRate(id,
					progress-getTotalProgress(id)/2);
	}

	@Override
	public int getProgress(int id) {
		return isValidAxis(id) ? this.progress[id] : 0;
	}

	@Override
	public int getTotalProgress(int id) {
		return 120;
	}

	@Override
	public void setTotalProgress(int id, int progress) {

	}

	@Override
	public void setProgressByUser(int id, int progress) {
		setProgress(id, progress);
		PacketHandler.sendToServer(new PacketMachine(this,
				(byte)NETWORK_PACKET_ID));
	}

	int setTargetRotationRate(int axis, int rotationsPerHour) {
		if(!isValidAxis(axis))
			throw new IllegalArgumentException("Invalid station rotation axis");
		int effectiveRate = clampRotationRate(rotationsPerHour);
		if(setTargetRotationRateValue(axis, effectiveRate))
			onTargetChanged();
		return effectiveRate;
	}

	private void setTargetRotationRates(int x, int y, int z,
			boolean notify) {
		boolean changed = setTargetRotationRateValue(0,
				clampRotationRate(x));
		changed |= setTargetRotationRateValue(1,
				clampRotationRate(y));
		changed |= setTargetRotationRateValue(2,
				clampRotationRate(z));
		if(changed && notify)
			onTargetChanged();
	}

	private boolean setTargetRotationRateValue(int axis,
			int rotationsPerHour) {
		int effectiveProgress = rotationsPerHour+getTotalProgress(axis)/2;
		if(numRotationsPerHour[axis] == rotationsPerHour
				&& progress[axis] == effectiveProgress)
			return false;
		numRotationsPerHour[axis] = rotationsPerHour;
		progress[axis] = effectiveProgress;
		return true;
	}

	private void writeTargetState(NBTTagCompound nbt) {
		nbt.setShort("numRotationsX", (short)numRotationsPerHour[0]);
		nbt.setShort("numRotationsY", (short)numRotationsPerHour[1]);
		nbt.setShort("numRotationsZ", (short)numRotationsPerHour[2]);
	}

	private void readTargetState(NBTTagCompound nbt, boolean notify) {
		setTargetRotationRates(nbt.getShort("numRotationsX"),
				nbt.getShort("numRotationsY"),
				nbt.getShort("numRotationsZ"), notify);
	}

	private int clampRotationRate(int rotationsPerHour) {
		return Math.max((int)OpenComputersValueCodec
				.MINIMUM_ROTATIONS_PER_HOUR,
				Math.min((int)OpenComputersValueCodec
						.MAXIMUM_ROTATIONS_PER_HOUR,
						rotationsPerHour));
	}

	private boolean isValidAxis(int axis) {
		return axis >= 0 && axis < numRotationsPerHour.length;
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
		return "orientation_controller";
	}

	@Callback(doc = "function(rotationsPerHour:number):boolean, number|string -- Sets the target Y-axis rotation rate; valid range -60..60 rotations/hour.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setYaw(Context context, Arguments args) {
		return setRotationRate(args, 1, "Yaw");
	}

	@Callback(doc = "function():number -- Returns the target Y-axis rotation rate in rotations/hour.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getYaw(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { numRotationsPerHour[1] };
	}

	@Callback(doc = "function(rotationsPerHour:number):boolean, number|string -- Sets the target X-axis rotation rate; valid range -60..60 rotations/hour.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setPitch(Context context, Arguments args) {
		return setRotationRate(args, 0, "Pitch");
	}

	@Callback(doc = "function():number -- Returns the target X-axis rotation rate in rotations/hour.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getPitch(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { numRotationsPerHour[0] };
	}

	@Callback(doc = "function():table -- Returns yaw/pitch angles, current rates, and target rates.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getOrientation(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();

		ISpaceObject object = access.getSpaceObject();
		Map<String, Object> orientation =
				new LinkedHashMap<String, Object>();
		orientation.put("yaw", OpenComputersValueCodec
				.angleDegreesFromRotationCycles(
						object.getRotation(ForgeDirection.UP)));
		orientation.put("pitch", OpenComputersValueCodec
				.angleDegreesFromRotationCycles(
						object.getRotation(ForgeDirection.EAST)));
		orientation.put("yawRate", OpenComputersValueCodec
				.rotationsPerHourFromCyclesPerTick(
						object.getDeltaRotation(ForgeDirection.UP)));
		orientation.put("pitchRate", OpenComputersValueCodec
				.rotationsPerHourFromCyclesPerTick(
						object.getDeltaRotation(ForgeDirection.EAST)));
		orientation.put("targetYawRate", numRotationsPerHour[1]);
		orientation.put("targetPitchRate", numRotationsPerHour[0]);
		return new Object[] { orientation };
	}

	@Optional.Method(modid = "OpenComputers")
	private Object[] setRotationRate(Arguments args, int axis,
			String label) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asMutatorError();

		double rotationsPerHour = args.checkDouble(0);
		if(!OpenComputersValueCodec.isFinite(rotationsPerHour))
			return OpenComputersControllerAccess.mutatorError(
					"not_finite", label+" rotation rate must be finite.");
		if(!OpenComputersValueCodec.isInRange(rotationsPerHour,
				OpenComputersValueCodec.MINIMUM_ROTATIONS_PER_HOUR,
				OpenComputersValueCodec.MAXIMUM_ROTATIONS_PER_HOUR))
			return OpenComputersControllerAccess.mutatorError(
					"out_of_range", label
					+" rotation rate must be between -60 and 60 rotations/hour.");

		int effectiveRate = setTargetRotationRate(axis,
				OpenComputersValueCodec.rotationRateFromInput(
						rotationsPerHour));
		return new Object[] { true, effectiveRate };
	}
}
