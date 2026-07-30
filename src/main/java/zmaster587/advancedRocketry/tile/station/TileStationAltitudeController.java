package zmaster587.advancedRocketry.tile.station;

import io.netty.buffer.ByteBuf;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
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
import cpw.mods.fml.relauncher.Side;

public class TileStationAltitudeController extends TileEntity implements IModularInventory, INetworkMachine, ISliderBar {

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

		S35PacketUpdateTileEntity packet = new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
		return super.getDescriptionPacket();
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		super.onDataPacket(net, pkt);

		gravity = pkt.func_148857_g().getInteger("gravity");

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
		if(id == TARGET_ALTITUDE_SLIDER_ID
				|| id == ALTITUDE_CHANGE_RATE_SLIDER_ID) {
			setProgress(id, nbt.getShort(NETWORK_PROGRESS_NBT));
			if(side == Side.SERVER)
				markDirty();
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
		gravity = nbt.getShort("numRotations");
		if(nbt.hasKey("numRotations"))
			progress = Math.max(0, Math.min(
					getTotalProgress(TARGET_ALTITUDE_SLIDER_ID),
					gravity-10));
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
			this.progress = Math.max(0,
					Math.min(getTotalProgress(id), progress));
			gravity = this.progress + 10;
		}
		else if(id == ALTITUDE_CHANGE_RATE_SLIDER_ID)
			altitudeChangeRateProgress =
					StationAltitudeChangeRate.clampSliderProgress(
							progress);
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
}
