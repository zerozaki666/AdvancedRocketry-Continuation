package zmaster587.advancedRocketry.tile.station;

import io.netty.buffer.ByteBuf;

import java.util.LinkedList;
import java.util.List;

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
public class TileStationGravityController extends TileEntity implements IModularInventory, INetworkMachine, ISliderBar, SimpleComponent {

	private static final int SLIDER_ID = 0;
	private static final String NETWORK_PROGRESS_NBT = "progress";
	private static final int LEGACY_DEFAULT_GRAVITY_PERCENT = 15;

	int gravity;
	int progress;

	private ModuleText moduleGrav, numGravPylons, maxGravBuildSpeed, targetGrav;

	public TileStationGravityController() {
		gravity = LEGACY_DEFAULT_GRAVITY_PERCENT;
		progress = gravity-OpenComputersValueCodec.MINIMUM_GRAVITY_PERCENT;
		moduleGrav = new ModuleText(6, 15, LibVulpes.proxy.getLocalizedString("msg.stationgravctrl.alt"), 0xaa2020);
		//numGravPylons = new ModuleText(10, 25, "Number Of Thrusters: ", 0xaa2020);
		maxGravBuildSpeed = new ModuleText(6, 25, LibVulpes.proxy.getLocalizedString("msg.stationgravctrl.maxaltrate"), 0xaa2020);
		targetGrav = new ModuleText(6, 35, LibVulpes.proxy.getLocalizedString("msg.stationgravctrl.tgtalt"), 0x202020);
	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();
		modules.add(moduleGrav);
		//modules.add(numThrusters);
		modules.add(maxGravBuildSpeed);

		modules.add(targetGrav);
		modules.add(new ModuleSlider(6, 60, 0, TextureResources.doubleWarningSideBarIndicator, (ISliderBar)this));

		updateText();
		return modules;
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setInteger("gravity", gravity);

		S35PacketUpdateTileEntity packet = new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
		return packet;
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		int targetPercent = clampGravityPercent(
				pkt.func_148857_g().getInteger("gravity"));
		gravity = targetPercent;
		progress = gravity-OpenComputersValueCodec.MINIMUM_GRAVITY_PERCENT;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	private void updateText() {
		if(worldObj.isRemote) {
			ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(this.xCoord, this.zCoord);
			if(object != null) {
				moduleGrav.setText(String.format("%s%.2f", LibVulpes.proxy.getLocalizedString("msg.stationgravctrl.alt"), object.getProperties().getGravitationalMultiplier()));
				maxGravBuildSpeed.setText(String.format("%s%.1f",LibVulpes.proxy.getLocalizedString("msg.stationgravctrl.maxaltrate"), 7200D*object.getMaxRotationalAcceleration()));
			}

			//numThrusters.setText("Number Of Thrusters: 0");

			targetGrav.setText(String.format("%s%d", LibVulpes.proxy.getLocalizedString("msg.stationgravctrl.tgtalt"), gravity));
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if(this.worldObj.provider instanceof WorldProviderStation) {

			if(!worldObj.isRemote) {
				ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(this.xCoord, this.zCoord);

				if(object != null) {
					if(gravity == 0)
						setTargetGravityPercent(
								LEGACY_DEFAULT_GRAVITY_PERCENT);
					double targetGravity = gravity/100D;
					double angVel = object.getProperties().getGravitationalMultiplier();
					double acc = 0.001;

					double difference = targetGravity - angVel;

					if(difference != 0) {
						double finalVel = angVel;
						if(difference < 0) {
							finalVel = angVel + Math.max(difference, -acc);
						}
						else if(difference > 0) {
							finalVel = angVel + Math.min(difference, acc);
						}

						object.getProperties().setGravitationalMultiplier((float)finalVel);
						if(!worldObj.isRemote) {
							//PacketHandler.sendToNearby(new PacketStationUpdate(object, PacketStationUpdate.Type.ROTANGLE_UPDATE), this.worldObj.provider.dimensionId, this.xCoord, this.yCoord, this.zCoord, 1024);
							PacketHandler.sendToAll(new PacketStationUpdate(object, PacketStationUpdate.Type.DIM_PROPERTY_UPDATE));
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
		return "tile.gravityControl.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == SLIDER_ID) {
			out.writeShort(progress);
		}
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == SLIDER_ID)
			nbt.setShort(NETWORK_PROGRESS_NBT, in.readShort());
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(side == Side.SERVER && id == SLIDER_ID)
			setProgress(SLIDER_ID,
					nbt.getShort(NETWORK_PROGRESS_NBT));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setShort("numRotations", (short)gravity);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		gravity = nbt.getShort("numRotations");
		if(gravity == 0)
			gravity = LEGACY_DEFAULT_GRAVITY_PERCENT;
		gravity = clampGravityPercent(gravity);
		progress = gravity
				-OpenComputersValueCodec.MINIMUM_GRAVITY_PERCENT;
	}


	@Override
	public float getNormallizedProgress(int id) {
		return getProgress(0)/(float)getTotalProgress(0);
	}

	@Override
	public void setProgress(int id, int progress) {
		if(id == SLIDER_ID)
			setTargetGravityPercent(progress
					+OpenComputersValueCodec.MINIMUM_GRAVITY_PERCENT);
	}

	@Override
	public int getProgress(int id) {
		return this.progress;
	}

	@Override
	public int getTotalProgress(int id) {
		return 90;
	}

	@Override
	public void setTotalProgress(int id, int progress) {

	}

	@Override
	public void setProgressByUser(int id, int progress) {
		setProgress(id, progress);
		PacketHandler.sendToServer(new PacketMachine(this, (byte)SLIDER_ID));
	}

	int setTargetGravityPercent(int targetPercent) {
		int effectivePercent = clampGravityPercent(targetPercent);
		int effectiveProgress = effectivePercent
				-OpenComputersValueCodec.MINIMUM_GRAVITY_PERCENT;
		if(gravity != effectivePercent || progress != effectiveProgress) {
			gravity = effectivePercent;
			progress = effectiveProgress;
			onTargetChanged();
		}
		return effectivePercent;
	}

	private int clampGravityPercent(int targetPercent) {
		return Math.max(OpenComputersValueCodec.MINIMUM_GRAVITY_PERCENT,
				Math.min(OpenComputersValueCodec.MAXIMUM_GRAVITY_PERCENT,
						targetPercent));
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
		return "gravity_controller";
	}

	@Callback(doc = "function(multiplier:number):boolean, number|string -- Sets the target station gravity multiplier; valid range 0.10..1.00, quantized to 0.01.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setGravityMultiplier(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asMutatorError();

		double multiplier = args.checkDouble(0);
		if(!OpenComputersValueCodec.isFinite(multiplier))
			return OpenComputersControllerAccess.mutatorError(
					"not_finite", "Gravity multiplier must be finite.");
		if(!OpenComputersValueCodec.isInRange(multiplier,
				OpenComputersValueCodec.MINIMUM_GRAVITY_MULTIPLIER,
				OpenComputersValueCodec.MAXIMUM_GRAVITY_MULTIPLIER))
			return OpenComputersControllerAccess.mutatorError(
					"out_of_range",
					"Gravity multiplier must be between 0.10 and 1.00.");

		int effectivePercent = setTargetGravityPercent(
				OpenComputersValueCodec.gravityPercentFromMultiplier(
						multiplier));
		return new Object[] { true, OpenComputersValueCodec
				.gravityMultiplierFromPercent(effectivePercent) };
	}

	@Callback(doc = "function():number, number -- Returns the current and target station gravity multipliers.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getGravityMultiplier(Context context, Arguments args) {
		Result access = OpenComputersControllerAccess.resolve(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] {
				access.getSpaceObject().getProperties()
						.getGravitationalMultiplier(),
				OpenComputersValueCodec.gravityMultiplierFromPercent(
						gravity) };
	}
}
