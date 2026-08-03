package zmaster587.advancedRocketry.tile.station;

import io.netty.buffer.ByteBuf;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import zmaster587.advancedRocketry.api.dimension.IDimensionProperties;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.entity.EntityUIButton;
import zmaster587.advancedRocketry.entity.EntityUIPlanet;
import zmaster587.advancedRocketry.entity.EntityUIStar;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.integration.CompatibilityMgr;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess.Result;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersNetworkNodeSupport;
import zmaster587.advancedRocketry.stations.SpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationDestinationService;
import zmaster587.advancedRocketry.stations.StationTarget;
import zmaster587.advancedRocketry.stations.StationTargetResolver;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.inventory.modules.IButtonInventory;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.ISliderBar;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleRedstoneOutputButton;
import zmaster587.libVulpes.inventory.modules.ModuleSlider;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.util.INetworkMachine;
import zmaster587.libVulpes.util.ZUtils.RedstoneState;

@Optional.Interface(iface = "li.cil.oc.api.network.Environment", modid = "OpenComputers")
public class TilePlanetaryHologram extends TileEntity implements IButtonInventory, IModularInventory, ISliderBar, INetworkMachine, Environment {

	private List<EntityUIPlanet> entities;
	private List<EntityUIStar> starEntities;
	private EntityUIPlanet centeredEntity;
	private EntityUIPlanet selectedPlanet;
	private EntityUIStar currentStar;
	private EntityUIButton backButton;
	private StellarBody currentStarBody;

	private ModuleRedstoneOutputButton redstoneControl;
	private RedstoneState state;
	private int selectedId;
	private float onTime;
	private ModuleText targetGrav;
	private float size;
	private static final byte SCALEPACKET = 0;
	private static final byte STATEUPDATE = 1;
	private static final double MINIMUM_HOLOGRAM_SCALE = 0.8D;
	private static final double MAXIMUM_HOLOGRAM_SCALE = 10.8D;
	private static final double HOLOGRAM_SCALE_STEP = 0.1D;
	private boolean allowUpdate = true;  //Hack to get around the delay in entity position
	private boolean stellarMode;
	private Object openComputersNode;
	private NBTTagCompound pendingOpenComputersNodeData;
	private boolean openComputersSignalBaseline;
	private int openComputersLastDestination;

	public TilePlanetaryHologram() {
		entities = new LinkedList<EntityUIPlanet>();
		starEntities = new LinkedList<EntityUIStar>();
		targetGrav = new ModuleText(6, 45, LibVulpes.proxy.getLocalizedString("msg.planetholo.size"), 0x202020);
		selectedPlanet = null;
		stellarMode = false;
		selectedId = -1;
		onTime = 1f;
		size = 0.02f;
		redstoneControl = new ModuleRedstoneOutputButton(174, 4, 1, "", this);
		state = RedstoneState.OFF;
		redstoneControl.setRedstoneState(state);
	}

	@Override
	public void invalidate() {
		detachOpenComputersNode();
		super.invalidate();
		cleanup();
	}

	@Override
	public void onChunkUnload() {
		detachOpenComputersNode();
		super.onChunkUnload();
	}

	private void cleanup() {
		for(EntityUIPlanet planet : entities) {
			planet.setDead();
		}
		entities.clear();

		for(EntityUIStar star : starEntities) star.setDead();
		starEntities.clear();

		selectedPlanet = null;
		centeredEntity = null;
		//currentStarBody = null;
		selectedId = -1;


		if(currentStar != null) {
			currentStar.setDead();
			currentStar = null;
		}
		if(backButton != null) {
			backButton.setDead();
			backButton = null;
		}
	}

	public boolean isEnabled() {
		boolean powered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
		return (!powered && state == RedstoneState.INVERTED) || (powered && state == RedstoneState.ON) || state == RedstoneState.OFF;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}
	
	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) {
			if(CompatibilityMgr.openComputersLoaded) {
				ensureOpenComputersNode();
				updateOpenComputersDestinationSignal();
			}
			if(isEnabled()) {

				if(onTime < 1)
					onTime += .5f/getHologramSize();//0.02f;

				if(allowUpdate) {
					for(EntityUIPlanet entity : entities) {
						DimensionProperties properties = entity.getProperties();
						if(entity != centeredEntity)
							entity.setPositionPolar(this.xCoord + .5, this.yCoord + 1, this.zCoord + .5, getInterpHologramSize()*(.1 + properties.orbitalDist/100f), properties.orbitTheta);
						entity.setScale(getInterpHologramSize());
					}

					if(stellarMode) {
						for(EntityUIStar entity : starEntities) {
							entity.setPosition(this.xCoord + .5 + getInterpHologramSize()*entity.getStarProperties().getPosX()/100f, this.yCoord + 1, this.zCoord + .5 + getInterpHologramSize()*entity.getStarProperties().getPosZ()/100f);
							entity.setScale(getInterpHologramSize());
						}
					}
					else {
						if(!starEntities.isEmpty()) {
							float phaseInc = 4*360f/starEntities.size();
							float phase = 0;
							for(EntityUIStar entity : starEntities) {
								double deltaX, deltaY;
								deltaX = (entity.getStarProperties().getStarSeperation()*MathHelper.cos(phase)*0.01);
								deltaY = (entity.getStarProperties().getStarSeperation()*MathHelper.sin(phase)*0.01);

								entity.setPosition(xCoord + .5 + getInterpHologramSize()*deltaX, yCoord + 1, zCoord + .5 + getInterpHologramSize()*deltaY);
								entity.setScale(getInterpHologramSize()*entity.getStarProperties().getSize());
								phase += phaseInc;
							}
						}
					}

					if(currentStar != null) {
						currentStar.setScale(getInterpHologramSize());
						currentStar.setPositionPolar(this.xCoord + .5, this.yCoord + 1, this.zCoord + .5, 0, 0);
					}

					if(centeredEntity != null) {
						centeredEntity.setPositionPolar(this.xCoord + .5, this.yCoord + 1, this.zCoord + .5, 0, 0);
					}

					if(entities.isEmpty() && starEntities.isEmpty()) {
						rebuildSystem();
					}

					if(backButton != null) {
						backButton.setPosition(this.xCoord + .5, this.yCoord + 1.5 + getInterpHologramSize()/10f, this.zCoord + .5);
					}
				}
				else
					allowUpdate = true;
			} else { //isenabled
				if(backButton != null )
					cleanup();
			}
		}
	}

	public void selectSystem(int id) {

		StationTargetResolver targetResolver =
				StationTargetResolver.getInstance();
		if(targetResolver.isStellarSelectorId(id)) {
			if(stellarMode) {
				if(selectedId != id) {
					for(EntityUIStar entity : starEntities) {
						if(targetResolver.getSelectorId(entity.getPlanetID())
								== id) {
							entity.setSelected(true);
							selectedPlanet = entity;
						}
						else
							entity.setSelected(false);
					}
					selectedId = id;
				}
				else {
					stellarMode = false;
					currentStarBody = targetResolver.getSelectorStar(id);
					rebuildSystem();
					selectedId = -1;
				}
			}

		}
			else {
				ISpaceObject station = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(this.xCoord, this.zCoord);
				StationTarget target = targetResolver.resolve(id);
				if(station instanceof SpaceObject
						&& station.getOrbitingPlanetId()
								!= SpaceObjectManager.WARPDIMID
						&& target.getKind()
								== StationTarget.Kind.DIMENSION) {
					StationDestinationService.Result selection =
							StationDestinationService.setDestination(
									(SpaceObject)station, id);
					if(!selection.isSuccess())
						return;

				if(selectedPlanet != null && selectedPlanet.getPlanetID() == id) {
					centeredEntity = selectedPlanet;
					stellarMode = false;
					rebuildSystem();
				}
				else
					for(EntityUIPlanet entity : entities) {
						if(entity.getPlanetID() == id) {
							entity.setSelected(true);
							selectedPlanet = entity;
						}
						else
							entity.setSelected(false);
					}
			}
		}
	}

	private void rebuildSystem() {
		onTime = 0;
		for(EntityUIPlanet entity : entities)
			entity.setDead();

		for(EntityUIStar body : starEntities)
			body.setDead();

		starEntities.clear();
		entities.clear();
		selectedPlanet = null;

		if(backButton == null) {
			backButton = new EntityUIButton(worldObj, 0, this);
			backButton.setPosition(this.xCoord + .5, this.yCoord + 1.5, this.zCoord + .5);
			this.worldObj.spawnEntityInWorld(backButton);
		}

		if(!stellarMode) {
			List<IDimensionProperties> planetList = currentStarBody == null ? DimensionManager.getSol().getPlanets() : currentStarBody.getPlanets();
			if(centeredEntity != null) {
				planetList = new LinkedList<IDimensionProperties>();
				planetList.add(centeredEntity.getProperties());

				for(int id : centeredEntity.getProperties().getChildPlanets())
					planetList.add(DimensionManager.getInstance().getDimensionProperties(id));

				if(currentStar != null) {
					currentStar.setDead();
					currentStar = null;
				}

			}
			else {
				if(currentStarBody == null)
					currentStarBody = DimensionManager.getSol();
				currentStar = new EntityUIStar(worldObj, currentStarBody, this, xCoord + .5, yCoord + 1, zCoord + .5);
				this.worldObj.spawnEntityInWorld(currentStar);

				//Spawn substars
				if(currentStarBody.getSubStars() != null && !currentStarBody.getSubStars().isEmpty()) {
					float phaseInc = 360f/currentStarBody.getSubStars().size();
					float phase = 0;
					int count = 0;
					Collection<StellarBody> starList = currentStarBody.getSubStars();
					for(StellarBody body : starList) {

						int deltaX, deltaY;
						deltaX = (int)(body.getStarSeperation()*MathHelper.cos(phase)*0.05);
						deltaY = (int)(body.getStarSeperation()*MathHelper.sin(phase)*0.05);
						EntityUIStar entity = new EntityUIStar(worldObj, body, count++, this, xCoord + .5 + deltaX, yCoord + 1, zCoord + .5 + deltaY);

						this.worldObj.spawnEntityInWorld(entity);
						starEntities.add(entity);
						phase += phaseInc;
					}
				}
			}

			for(IDimensionProperties properties : planetList) {
				EntityUIPlanet entity = new EntityUIPlanet(worldObj, (DimensionProperties)properties, this, this.xCoord + .5, this.yCoord + 1, this.zCoord + .5);
				//entity.setPositionPolar(this.xCoord + .5, this.yCoord + 10, this.zCoord + .5,  ((DimensionProperties)properties).orbitalDist/100f, ( (DimensionProperties)properties).orbitTheta);
				this.worldObj.spawnEntityInWorld(entity);
				entities.add(entity);

				if(centeredEntity != null && properties == centeredEntity.getProperties())
					centeredEntity = entity;
			}
		}
		else {

			if(currentStar != null) {
				currentStar.setDead();
				currentStar = null;
			}

			Collection<StellarBody> starList = DimensionManager.getInstance().getStars();

			for(StellarBody body : starList) {
				EntityUIStar entity = new EntityUIStar(worldObj, body, this, this.xCoord + .5, this.yCoord + 1, this.zCoord + .5);

				this.worldObj.spawnEntityInWorld(entity);
				starEntities.add(entity);
			}
		}
		//Hack to delay position updates by a tick
		allowUpdate = false;
	}


	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();

		modules.add(targetGrav);
		modules.add(new ModuleSlider(6, 60, 0, TextureResources.doubleWarningSideBarIndicator, (ISliderBar)this));
		modules.add(redstoneControl);

		updateText();
		return modules;
	}

	private void updateText() {
		if(worldObj != null && worldObj.isRemote) {

			//numThrusters.setText("Number Of Thrusters: 0");
			targetGrav.setText(String.format("%s %f", LibVulpes.proxy.getLocalizedString("msg.planetholo.size"), getHologramSize()));
		}
	}

	private float getHologramSize() {
		return (size*10 + 0.8f);
	}

	private double setHologramScale(double requestedScale) {
		double clamped = Math.max(MINIMUM_HOLOGRAM_SCALE,
				Math.min(MAXIMUM_HOLOGRAM_SCALE, requestedScale));
		double quantized = Math.round(clamped/HOLOGRAM_SCALE_STEP)
				* HOLOGRAM_SCALE_STEP;
		float newSize = (float)((quantized-MINIMUM_HOLOGRAM_SCALE)/10.0D);
		if(size != newSize) {
			size = newSize;
			updateText();
			if(worldObj != null && !worldObj.isRemote) {
				markDirty();
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}
		}
		return Math.round(getHologramSize()*10.0D)/10.0D;
	}

	private float getInterpHologramSize() {
		return getHologramSize()*onTime;
	}

	@Override
	public String getModularInventoryName() {
		return "tile.planetHoloSelector.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public float getNormallizedProgress(int id) {
		return size;
	}

	@Override
	public void setProgress(int id, int progress) {
		setHologramScale(MINIMUM_HOLOGRAM_SCALE
				+ Math.max(0, Math.min(100, progress))/10.0D);
	}

	@Override
	public int getProgress(int id) {
		return (int)(size*100);
	}

	@Override
	public int getTotalProgress(int id) {
		return 100;
	}

	@Override
	public void setTotalProgress(int id, int progress) {

	}

	@Override
	public void setProgressByUser(int id, int progress) {
		setProgress(id, progress);
		PacketHandler.sendToServer(new PacketMachine(this, SCALEPACKET));
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == SCALEPACKET) {
			out.writeFloat(size);
		}
		if(id == STATEUPDATE) {
			out.writeByte(state.ordinal());
		}

	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == SCALEPACKET) {
			nbt.setFloat("scale", in.readFloat());
		}
		else if(packetId == STATEUPDATE) {
			nbt.setByte("state", in.readByte());
		}

	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(id == SCALEPACKET) {
			setHologramScale(nbt.getFloat("scale")*10.0D
					+ MINIMUM_HOLOGRAM_SCALE);
		}
		else if (id == STATEUPDATE) {
			state = RedstoneState.values()[nbt.getByte("state")];
		}
	}

	public void onInventoryButtonPressed(int buttonId) {
		//Back button
		if(buttonId == 0) {
			if(currentStar != null)
				stellarMode = true;
			selectedPlanet = null; centeredEntity = null;
			rebuildSystem();
		}
		else if(buttonId == 1) {
			state = redstoneControl.getState();
			PacketHandler.sendToServer(new PacketMachine(this, (byte)STATEUPDATE));
		}
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		state.writeToNBT(nbt);
		nbt.setFloat("hologramSize", size);
		
		return new S35PacketUpdateTileEntity(xCoord,yCoord,zCoord,0, nbt);
	}
	
	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		super.onDataPacket(net, pkt);
		
		
		state = RedstoneState.createFromNBT(pkt.func_148857_g());
		redstoneControl.setRedstoneState(state);
		if(pkt.func_148857_g().hasKey("hologramSize"))
			setHologramScale(pkt.func_148857_g()
					.getFloat("hologramSize")*10.0D
					+ MINIMUM_HOLOGRAM_SCALE);
	}
	
	@Override
	public void writeToNBT(NBTTagCompound compound) {
		super.writeToNBT(compound);
		state.writeToNBT(compound);
		compound.setFloat("hologramSize", size);
		writeOpenComputersNode(compound);
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		state = RedstoneState.createFromNBT(compound);
		redstoneControl.setRedstoneState(state);
		if(compound.hasKey("hologramSize"))
			setHologramScale(compound.getFloat("hologramSize")*10.0D
					+ MINIMUM_HOLOGRAM_SCALE);
		else
			setHologramScale(1.0D);
		pendingOpenComputersNodeData = compound.hasKey("openComputersNode")
				? compound.getCompoundTag("openComputersNode") : null;
		openComputersSignalBaseline = false;
	}

	@Optional.Method(modid = "OpenComputers")
	private void ensureOpenComputersNode() {
		if(openComputersNode == null) {
			openComputersNode = OpenComputersNetworkNodeSupport.create(this,
					"planet_selector", pendingOpenComputersNodeData);
			pendingOpenComputersNodeData = null;
		}
		OpenComputersNetworkNodeSupport.join(this, openComputersNode);
	}

	private void detachOpenComputersNode() {
		if(CompatibilityMgr.openComputersLoaded && openComputersNode != null)
			detachLoadedOpenComputersNode();
	}

	@Optional.Method(modid = "OpenComputers")
	private void detachLoadedOpenComputersNode() {
		pendingOpenComputersNodeData =
				OpenComputersNetworkNodeSupport.save(openComputersNode);
		OpenComputersNetworkNodeSupport.remove(openComputersNode);
		openComputersNode = null;
	}

	private void writeOpenComputersNode(NBTTagCompound compound) {
		if(pendingOpenComputersNodeData != null)
			compound.setTag("openComputersNode",
					pendingOpenComputersNodeData);
		if(CompatibilityMgr.openComputersLoaded && openComputersNode != null)
			writeLoadedOpenComputersNode(compound);
	}

	@Optional.Method(modid = "OpenComputers")
	private void writeLoadedOpenComputersNode(NBTTagCompound compound) {
		compound.setTag("openComputersNode",
				OpenComputersNetworkNodeSupport.save(openComputersNode));
	}

	@Optional.Method(modid = "OpenComputers")
	private void updateOpenComputersDestinationSignal() {
		ISpaceObject object = SpaceObjectManager.getSpaceManager()
				.getSpaceStationFromBlockCoords(xCoord, zCoord);
		if(object == null) {
			openComputersSignalBaseline = false;
			return;
		}
		int destination = object.getDestOrbitingBody();
		if(!openComputersSignalBaseline) {
			openComputersLastDestination = destination;
			openComputersSignalBaseline = true;
			return;
		}
		if(destination != openComputersLastDestination) {
			openComputersLastDestination = destination;
			OpenComputersNetworkNodeSupport.sendSignal(openComputersNode,
					"planet_selected", object.getId(), destination);
		}
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public Node node() {
		return openComputersNode instanceof Node
				? (Node)openComputersNode : null;
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public void onConnect(Node node) {
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public void onDisconnect(Node node) {
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public void onMessage(Message message) {
	}

	private SpaceObject getAutomationStation(Result access) {
		return access.getSpaceObject() instanceof SpaceObject
				? (SpaceObject)access.getSpaceObject() : null;
	}

	@Callback(doc = "function():number -- Returns the current station orbit target; unavailable during warp.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getCurrentPlanet(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		if(access.getSpaceObject().getOrbitingPlanetId()
				== SpaceObjectManager.WARPDIMID)
			return OpenComputersComponentAccess.getterError("in_warp",
					"Station is currently in warp.");
		return new Object[] { access.getSpaceObject().getOrbitingPlanetId() };
	}

	@Callback(doc = "function():number -- Returns the committed station destination.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getDestination(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { access.getSpaceObject().getDestOrbitingBody() };
	}

	@Callback(doc = "function(id?:number):table -- Describes a station target; defaults to the committed destination.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getTargetInfo(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		SpaceObject station = getAutomationStation(access);
		if(station == null)
			return OpenComputersComponentAccess.getterError(
					"not_on_station", "Unsupported station implementation.");
		int id = args.count() == 0 ? station.getDestOrbitingBody()
				: args.checkInteger(0);
		StationTarget target = StationTargetResolver.getInstance().resolve(id);
		if(!target.isDestination())
			return OpenComputersComponentAccess.getterError("invalid_target",
					"Requested id is not a valid station target.");
		return new Object[] { StationDestinationService.describe(station,
				target) };
	}

	@Callback(doc = "function(id:number):boolean, number|string -- Selects a known planet or black-hole target.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] selectTarget(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asMutatorError();
		SpaceObject station = getAutomationStation(access);
		if(station == null)
			return OpenComputersComponentAccess.mutatorError(
					"not_on_station", "Unsupported station implementation.");
		int id = args.checkInteger(0);
		StationDestinationService.Result result =
				StationDestinationService.setDestination(station, id);
		if(!result.isSuccess())
			return OpenComputersComponentAccess.mutatorError(
					result.getErrorCode(), result.getErrorMessage());
		markDirty();
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		return new Object[] { true, id };
	}

	@Callback(doc = "function():number -- Returns the actual hologram scale multiplier.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getScale(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { Math.round(getHologramSize()*10.0D)/10.0D };
	}

	@Callback(doc = "function(multiplier:number):boolean, number|string -- Sets hologram scale from 0.8 to 10.8 in 0.1 steps.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setScale(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asMutatorError();
		double scale = args.checkDouble(0);
		if(Double.isNaN(scale) || Double.isInfinite(scale))
			return OpenComputersComponentAccess.mutatorError("not_finite",
					"Hologram scale must be finite.");
		if(scale < MINIMUM_HOLOGRAM_SCALE
				|| scale > MAXIMUM_HOLOGRAM_SCALE)
			return OpenComputersComponentAccess.mutatorError("out_of_range",
					"Hologram scale must be between 0.8 and 10.8.");
		return new Object[] { true, setHologramScale(scale) };
	}

	@Callback(doc = "function():boolean -- Returns whether the hologram is enabled by its redstone mode.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isEnabled(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { isEnabled() };
	}

	@Callback(doc = "function():table -- Returns station, target, scale, enabled, and warp state.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("stationId", access.getSpaceObject().getId());
		status.put("currentTargetId",
				access.getSpaceObject().getOrbitingPlanetId());
		status.put("destinationTargetId",
				access.getSpaceObject().getDestOrbitingBody());
		status.put("scale", Math.round(getHologramSize()*10.0D)/10.0D);
		status.put("enabled", isEnabled());
		status.put("inWarp", access.getSpaceObject().getOrbitingPlanetId()
				== SpaceObjectManager.WARPDIMID);
		return new Object[] { status };
	}
}
