package zmaster587.advancedRocketry.tile.station;

import io.netty.buffer.ByteBuf;

import java.util.Iterator;
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
import zmaster587.advancedRocketry.achievements.ARAchivements;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.DataStorage.DataType;
import zmaster587.advancedRocketry.api.dimension.IDimensionProperties;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.inventory.modules.ModuleData;
import zmaster587.advancedRocketry.inventory.modules.ModulePanetImage;
import zmaster587.advancedRocketry.inventory.modules.ModulePlanetSelector;
import zmaster587.advancedRocketry.inventory.IPlanetDefiner;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.integration.CompatibilityMgr;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess.Result;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersNetworkNodeSupport;
import zmaster587.advancedRocketry.item.ItemData;
import zmaster587.advancedRocketry.item.ItemPlanetIdentificationChip;
import zmaster587.advancedRocketry.network.PacketSpaceStationInfo;
import zmaster587.advancedRocketry.stations.SpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationDestinationService;
import zmaster587.advancedRocketry.stations.StationTarget;
import zmaster587.advancedRocketry.stations.StationTargetResolver;
import zmaster587.advancedRocketry.tile.multiblock.TileWarpCore;
import zmaster587.advancedRocketry.util.IDataInventory;
import zmaster587.advancedRocketry.world.util.MultiData;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.client.util.IndicatorBarImage;
import zmaster587.libVulpes.inventory.GuiHandler;
import zmaster587.libVulpes.inventory.GuiHandler.guiId;
import zmaster587.libVulpes.inventory.modules.IButtonInventory;
import zmaster587.libVulpes.inventory.modules.IDataSync;
import zmaster587.libVulpes.inventory.modules.IGuiCallback;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.IProgressBar;
import zmaster587.libVulpes.inventory.modules.ISelectionNotify;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleButton;
import zmaster587.libVulpes.inventory.modules.ModuleProgress;
import zmaster587.libVulpes.inventory.modules.ModuleScaledImage;
import zmaster587.libVulpes.inventory.modules.ModuleSlotArray;
import zmaster587.libVulpes.inventory.modules.ModuleSync;
import zmaster587.libVulpes.inventory.modules.ModuleTab;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.inventory.modules.ModuleTexturedSlotArray;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.util.BlockPosition;
import zmaster587.libVulpes.util.EmbeddedInventory;
import zmaster587.libVulpes.util.INetworkMachine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.Interface(iface = "li.cil.oc.api.network.Environment", modid = "OpenComputers")
public class TileWarpShipMonitor extends TileEntity implements IModularInventory, ISelectionNotify, INetworkMachine, IButtonInventory, IProgressBar, IDataSync, IGuiCallback, IDataInventory, IPlanetDefiner, Environment {


	protected ModulePlanetSelector container;
	private ModuleText canWarp;
	DimensionProperties dimCache;
	private SpaceObject station;
	private static final int ARTIFACT_BEGIN_RANGE = 4, ARTIFACT_END_RANGE = 8;
	ModulePanetImage srcPlanetImg, dstPlanetImg;
	ModuleSync sync1, sync2, sync3, sync4, sync5, sync6;
	ModuleText srcPlanetText, dstPlanetText, warpFuel, status, warpCapacity;
	int warpCost = -1;
	int dstPlanet, srcPlanet;
	private ModuleTab tabModule;
	private static final byte TAB_SWITCH = 4, STORE_DATA = 10, LOAD_DATA = 20, SEARCH = 5, PROGRAMFROMCHIP = 6;
	private MultiData data;
	private EmbeddedInventory inv;
	private static final int DISTANCESLOT = 0, MASSSLOT = 1, COMPOSITION = 2, PLANETSLOT = 3, MAX_PROGRESS = 1000;
	private ModuleProgress programmingProgress;
	private int progress;
	private Object openComputersNode;
	private NBTTagCompound pendingOpenComputersNodeData;
	private boolean openComputersSignalBaseline;
	private int openComputersLastOrbit;

	public TileWarpShipMonitor() {
		tabModule = new ModuleTab(4,0,0,this, 3, new String[]{LibVulpes.proxy.getLocalizedString("msg.warpmon.tab.warp"), LibVulpes.proxy.getLocalizedString("msg.warpmon.tab.data"), LibVulpes.proxy.getLocalizedString("msg.warpmon.tab.tracking")}, new ResourceLocation[][] { TextureResources.tabWarp, TextureResources.tabData, TextureResources.tabPlanetTracking} );
		data = new MultiData();
		data.setMaxData(10000);
		inv = new EmbeddedInventory(9);
		programmingProgress = new ModuleProgress(35, 80, 3, TextureResources.terraformProgressBar, this);
		progress = -1;
	}

	@Override
	public void invalidate() {
		detachOpenComputersNode();
		super.invalidate();
	}

	@Override
	public void onChunkUnload() {
		detachOpenComputersNode();
		super.onChunkUnload();
	}


	private SpaceObject getSpaceObject() {
		if(station == null && worldObj.provider.dimensionId == Configuration.spaceDimId) {
			ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(xCoord, zCoord);
			if(object instanceof SpaceObject)
				station = (SpaceObject) object;
		}
		return station;
	}


	protected int getTravelCost() {
		return StationWarpService.calculateTravelCost(getSpaceObject());
	}

	@Override
	public int addData(int maxAmount, DataType type, ForgeDirection dir,
			boolean commit) {
		return data.addData(maxAmount, type, dir, commit);
	}

	@Override
	public int extractData(int maxAmount, DataType type, ForgeDirection dir,
			boolean commit) {
		return data.extractData(maxAmount, type, dir, commit);
	}

	@Override
	public List<ModuleBase> getModules(int ID, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();

		if(ID == guiId.MODULARNOINV.ordinal()) {

			//Front page
			if(tabModule.getTab() == 0) {
				modules.add(tabModule);
				//Don't keep recreating it otherwise data is stale
					if(sync1 == null) {
						sync1 = new ModuleSync(0, this);
						sync2 = new ModuleSync(1, this);
						sync3 = new ModuleSync(2, this);
						sync4 = new ModuleSync(3, this);
						sync5 = new ModuleSync(4, this);
						sync6 = new ModuleSync(5, this);

					}
					modules.add(sync1);
					modules.add(sync2);
					modules.add(sync3);
					modules.add(sync4);
					modules.add(sync5);
					modules.add(sync6);

				ISpaceObject station = getSpaceObject();
				boolean isOnStation = station != null;
				if(worldObj.isRemote)
					setPlanetModuleInfo();

				//Source planet
				int baseX = 10;
				int baseY = 20;
				int sizeX = 70;
				int sizeY = 70;

				if(worldObj.isRemote) {
					modules.add(new ModuleScaledImage(baseX,baseY,sizeX,sizeY, zmaster587.libVulpes.inventory.TextureResources.starryBG));
					modules.add(srcPlanetImg);

					
					ModuleText text = new ModuleText(baseX + 4, baseY + 4, "Orbiting:", 0xFFFFFF);
					text.setAlwaysOnTop(true);
					modules.add(text);
					
					modules.add(srcPlanetText);

					//Border
					modules.add(new ModuleScaledImage(baseX - 3,baseY,3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX + sizeX, baseY, -3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX,baseY,70,3, TextureResources.horizontalBar));
					modules.add(new ModuleScaledImage(baseX,baseY + sizeY - 3,70,-3, TextureResources.horizontalBar));
				}
				modules.add(new ModuleButton(baseX - 3, baseY + sizeY, 0, LibVulpes.proxy.getLocalizedString("msg.warpmon.selectplanet"), this,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild, sizeX + 6, 16));


				//Status text
				modules.add(new ModuleText(baseX, baseY + sizeY + 20, LibVulpes.proxy.getLocalizedString("msg.warpmon.corestatus"), 0x1b1b1b));
				int travelCost = getTravelCost();
				boolean flag = isOnStation
						&& travelCost != Integer.MAX_VALUE
						&& getSpaceObject().getFuelAmount() >= travelCost
						&& getSpaceObject().hasUsableWarpCore();
				flag = flag && !(isOnStation && (getSpaceObject().getDestOrbitingBody() == -1 || getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody()));
				boolean artifactFlag = (dimCache != null && meetsArtifactRequirements(dimCache));
				canWarp = new ModuleText(baseX, baseY + sizeY + 30, (isOnStation && (getSpaceObject().getDestOrbitingBody() == -1 || getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody())) ? LibVulpes.proxy.getLocalizedString("msg.warpmon.nowhere") : 
					(!artifactFlag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.missingart") : (flag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.ready") : LibVulpes.proxy.getLocalizedString("msg.warpmon.notready"))), flag && artifactFlag ? 0x1baa1b : 0xFF1b1b);
				modules.add(canWarp);
				modules.add(new ModuleProgress(baseX, baseY + sizeY + 40, 10, new IndicatorBarImage(70, 58, 53, 8, 122, 58, 5, 8, ForgeDirection.EAST, TextureResources.progressBars), this));
				//modules.add(new ModuleText(baseX + 82, baseY + sizeY + 20, "Fuel Cost:", 0x1b1b1b));

				warpCost = getTravelCost();
				
				


				//DEST planet
				baseX = 94;
				baseY = 20;
				sizeX = 70;
				sizeY = 70;
				ModuleButton warp = new ModuleButton(baseX - 3, baseY + sizeY,1, LibVulpes.proxy.getLocalizedString("msg.warpmon.warp"), this ,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild, sizeX + 6, 16);

				modules.add(warp);

				if(dimCache == null && isOnStation) {
					StationTarget destination = StationTargetResolver
							.getInstance().resolve(
									station.getDestOrbitingBody());
					dimCache = destination.getDimensionProperties();
				}

				if(!worldObj.isRemote && isOnStation) {
					PacketHandler.sendToPlayer(new PacketSpaceStationInfo(getSpaceObject().getId(), getSpaceObject()), player);
				}

				if(worldObj.isRemote) {
					warpFuel.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuelcost") + (flag ? String.valueOf(warpCost) : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
					warpCapacity.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuel") + (isOnStation ? getSpaceObject().getFuelAmount() : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
					modules.add(warpFuel);
					modules.add(warpCapacity);

					if(dimCache != null && worldObj.isRemote) {
						modules.add(dstPlanetImg);
					}
					
					ModuleText text = new ModuleText(baseX + 4, baseY + 4, LibVulpes.proxy.getLocalizedString("msg.warpmon.dest"), 0xFFFFFF);
					text.setAlwaysOnTop(true);
					modules.add(text);
					modules.add(dstPlanetText);


					//Border
					modules.add(new ModuleScaledImage(baseX - 3,baseY,3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX + sizeX, baseY, -3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX,baseY,70,3, TextureResources.horizontalBar));
					modules.add(new ModuleScaledImage(baseX,baseY + sizeY - 3,70,-3, TextureResources.horizontalBar));
				}
			}
			else if(tabModule.getTab() == 1) {
				modules.add(tabModule);
				modules.add(new ModuleData(35, 20, 0, this, data.getDataStorageForType(DataType.DISTANCE)));
				modules.add(new ModuleData(75, 20, 1, this, data.getDataStorageForType(DataType.MASS)));
				modules.add(new ModuleData(115, 20, 2, this, data.getDataStorageForType(DataType.COMPOSITION)));
			}
			else {
				modules.add(tabModule);
				modules.add(new ModuleText(65, 20, LibVulpes.proxy.getLocalizedString("msg.warpmon.artifact"), 0x202020));
				modules.add(new ModuleSlotArray(30, 35, this, 4, 5));
				modules.add(new ModuleSlotArray(55, 60, this, 5, 6));
				modules.add(new ModuleSlotArray(80, 35, this, 6, 7));
				modules.add(new ModuleSlotArray(105, 60, this, 7, 8));
				modules.add(new ModuleSlotArray(130, 35, this, 8, 9));

				modules.add(new ModuleButton(50, 117, 3, LibVulpes.proxy.getLocalizedString("msg.warpmon.search"), this,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild, LibVulpes.proxy.getLocalizedString("msg.warpmon.datareq"), 100, 10));
				modules.add(new ModuleButton(50, 127, 4, LibVulpes.proxy.getLocalizedString("msg.warpmon.chip"), this, zmaster587.libVulpes.inventory.TextureResources.buttonBuild,100, 10));
				modules.add(new ModuleTexturedSlotArray(30, 120, this, 3, 4, TextureResources.idChip));
				modules.add(programmingProgress);
			}
		}
		else if (ID == guiId.MODULARFULLSCREEN.ordinal()) {
			//Open planet selector menu
			SpaceObject station = getSpaceObject();
			int starId = 0;
			if(station != null) {
				StationTarget source = StationTargetResolver.getInstance()
						.resolve(station.getOrbitingPlanetId());
				if(source.getStellarBody() != null)
					starId = source.getStellarBody().getId();
			}
			container = new ModulePlanetSelector(starId,
					zmaster587.libVulpes.inventory.TextureResources.starryBG,
					this, this, true, true);
			container.setOffset(1000, 1000);
			modules.add(container);
		}
		return modules;
	}

	private void setPlanetModuleInfo() {

		ISpaceObject station = getSpaceObject();
		boolean isOnStation = station != null;
		DimensionProperties location;
		String planetName;

		if(isOnStation) {
			StationTarget source = StationTargetResolver.getInstance()
					.resolve(station.getOrbitingPlanetId());
			location = source.getDimensionProperties();
			planetName = location == null ? "???" : location.getName();
			if(location == null)
				location = DimensionManager.defaultSpaceDimensionProperties;
		}
		else {
			location = DimensionManager.getInstance().getDimensionProperties(worldObj.provider.dimensionId);
			planetName = DimensionManager.getInstance().getDimensionProperties(worldObj.provider.dimensionId).getName();

			if(planetName.isEmpty())
				planetName = "???";
		}

		boolean flag = isOnStation && warpCost >= 0
				&& warpCost < Integer.MAX_VALUE
				&& getSpaceObject().getFuelAmount() >= warpCost
				&& getSpaceObject().hasUsableWarpCore();

		if(canWarp != null) {
			flag = flag && !(isOnStation && (getSpaceObject().getDestOrbitingBody() == -1 || getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody()));
			boolean artifactFlag = (dimCache != null && meetsArtifactRequirements(dimCache));
			canWarp.setText(isOnStation && (getSpaceObject().getDestOrbitingBody() == -1 || getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody()) ? LibVulpes.proxy.getLocalizedString("msg.warpmon.nowhere") : 
				(!artifactFlag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.missingart") : (flag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.ready") : LibVulpes.proxy.getLocalizedString("msg.warpmon.notready"))));
			canWarp.setColor(flag && artifactFlag ? 0x1baa1b : 0xFF1b1b);
		}

		if(worldObj.isRemote) {
			if(srcPlanetImg == null ) {
				//Source planet
				int baseX = 10;
				int baseY = 20;
				int sizeX = 65;
				int sizeY = 65;

				srcPlanetImg = new ModulePanetImage(baseX + 10,baseY + 10,sizeX - 20, location);
				srcPlanetText = new ModuleText(baseX + 4, baseY + 56, "", 0xFFFFFF);
				srcPlanetText.setAlwaysOnTop(true);
				warpFuel = new ModuleText(baseX + 82, baseY + sizeY + 25, "", 0x1b1b1b);
				warpCapacity = new ModuleText(baseX + 82, baseY + sizeY + 35, "", 0x1b1b1b);

				//DEST planet
				baseX = 94;
				baseY = 20;
				sizeX = 65;
				sizeY = 65;

				dstPlanetImg = new ModulePanetImage(baseX + 10,baseY + 10,sizeX - 20, location);
				dstPlanetText = new ModuleText(baseX + 4, baseY + 56, "", 0xFFFFFF);
				dstPlanetText.setAlwaysOnTop(true);

			}

			srcPlanetImg.setDimProperties(location);
			srcPlanetText.setText(planetName);


			warpFuel.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuelcost") + (warpCost < Integer.MAX_VALUE ? String.valueOf(warpCost) : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
			warpCapacity.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuel") + (isOnStation ? ((SpaceObject)station).getFuelAmount() : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));



			DimensionProperties dstProps = null;
			if(isOnStation) {
				StationTarget destination = StationTargetResolver.getInstance()
						.resolve(dstPlanet);
				if(destination.isDestination())
					dstProps = destination.getDimensionProperties();
			}

			if(dstProps != null) {
				planetName = dstProps.getName();
				location = dstProps;


				dstPlanetImg.setDimProperties(location);
				dstPlanetText.setText(planetName);

				dstPlanetImg.setVisible(true);

			}
			else {
				dstPlanetText.setText("???");
				dstPlanetImg.setVisible(false);
			}
		}
	}

	@Override
	public String getModularInventoryName() {
		return "tile.stationmonitor.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return isAuthorizedActor(entity);
	}

	@Override
	public void onInventoryButtonPressed(int buttonId) {
		if(getSpaceObject() != null) {
			if(buttonId == 0)
				PacketHandler.sendToServer(new PacketMachine(this, (byte)0));
			else if(buttonId == 1) {
				PacketHandler.sendToServer(new PacketMachine(this, (byte)2));
			}
			else if(buttonId == 3) {
				PacketHandler.sendToServer(new PacketMachine(this, (byte)SEARCH));
			}
			else if(buttonId == 4) {
				PacketHandler.sendToServer(new PacketMachine(this, (byte)PROGRAMFROMCHIP));
			}
		}
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == 1 || id == 3)
			out.writeInt(container.getSelectedSystem());
		else if(id == TAB_SWITCH)
			out.writeShort(tabModule.getTab());
		else if(id >= 10 && id < 20) {
			out.writeByte(id - 10);
		}
		else if(id >= 20 && id < 30) {
			out.writeByte(id - 20);
		}
	}

	//TODO fix warp controller not sending 

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == 1 || packetId == 3)
			nbt.setInteger("id", in.readInt());
		else if(packetId == TAB_SWITCH)
			nbt.setShort("tab", in.readShort());
		else if(packetId >= 10 && packetId < 20) {
			nbt.setByte("id", (byte)(in.readByte() - 10));
		}
		else if(packetId >= 20 && packetId < 30) {
			nbt.setByte("id", (byte)(in.readByte() - 20));
		}
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(!isAuthorizedActor(player))
			return;

		if(id == 0)
			player.openGui(LibVulpes.instance, guiId.MODULARFULLSCREEN.ordinal(), worldObj, this.xCoord, this.yCoord, this.zCoord);
		else if(id == 1 || id == 3) {
			int rawId = nbt.getInteger("id");
			if(!(player instanceof EntityPlayerMP)
					|| !StationTargetResolver.getInstance()
							.isAllowedDestination(rawId,
									(EntityPlayerMP)player))
				return;
			StationTarget target = StationTargetResolver.getInstance()
					.resolve(rawId);
			if(!isTargetKnown(target))
				return;

			if(container != null)
				container.setSelectedSystem(rawId);
			selectSystem(rawId);

			//Update known planets
			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			if(id == 3)
				player.openGui(LibVulpes.instance, guiId.MODULARNOINV.ordinal(), worldObj, this.xCoord, this.yCoord, this.zCoord);
		}
		else if(id == 2) {
			SpaceObject station = getSpaceObject();
			if(station == null || !(player instanceof EntityPlayerMP))
				return;

			int destinationId = station.getDestOrbitingBody();
			if(!StationTargetResolver.getInstance().isAllowedDestination(
						destinationId, (EntityPlayerMP)player))
				return;
			StationWarpService.tryWarp(this, station,
					Integer.valueOf(destinationId));
		}
		else if(id == TAB_SWITCH && !worldObj.isRemote) {
			tabModule.setTab(nbt.getShort("tab"));
			player.openGui(LibVulpes.instance, GuiHandler.guiId.MODULARNOINV.ordinal(), worldObj, xCoord, yCoord, zCoord);
		}
		else if(id >= 10 && id < 20) {
			storeData(nbt.getByte("id") + 10);
		}
		else if(id >= 20 && id < 30) {
			loadData(nbt.getByte("id") + 20);
		}
		else if(id == SEARCH) {
			if(progress == -1 && data.getDataAmount(DataType.COMPOSITION) >= 100 && 
					data.getDataAmount(DataType.DISTANCE) >= 100 &&
					data.getDataAmount(DataType.MASS) >= 100)
				progress = 0;
		}
		else if(id == PROGRAMFROMCHIP) {
			SpaceObject obj = getSpaceObject();
			if(obj != null) {
				ItemStack stack = getStackInSlot(PLANETSLOT);
				if(stack != null && stack.getItem() instanceof ItemPlanetIdentificationChip) {
					if(DimensionManager.getInstance().isDimensionCreated(((ItemPlanetIdentificationChip)stack.getItem()).getDimensionId(stack)));
					obj.discoverPlanet(((ItemPlanetIdentificationChip)stack.getItem()).getDimensionId(stack));
				}
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound compound) {
		inv.writeToNBT(compound);
		data.writeToNBT(compound);
		compound.setInteger("progress", progress);
		writeOpenComputersNode(compound);
		super.writeToNBT(compound);
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		inv.readFromNBT(compound);
		data.readFromNBT(compound);
		progress = compound.getInteger("progress");
		pendingOpenComputersNodeData = compound.hasKey("openComputersNode")
				? compound.getCompoundTag("openComputersNode") : null;
		openComputersSignalBaseline = false;
	}

	@Override
	public void onSelectionConfirmed(Object sender) {
		//Container Cannot be null at this time
		onSelected(sender);
		PacketHandler.sendToServer(new PacketMachine(this, (byte)3));
	}

	@Override
	public void onSelected(Object sender) {
		selectSystem(container.getSelectedSystem());
	}

	private void selectSystem(int id) {
		SpaceObject object = getSpaceObject();
		if(object == null)
			return;
		StationTarget target = StationTargetResolver.getInstance()
				.resolve(id);
		if(!target.isDestination()) {
			dimCache = null;
			return;
		}
		StationDestinationService.Result result =
				StationDestinationService.setDestination(object, id);
		if(result.isSuccess())
			dimCache = target.getDimensionProperties();
	}

	private boolean isAuthorizedActor(EntityPlayer player) {
		if(player == null || worldObj == null || player.worldObj != worldObj
				|| worldObj.provider.dimensionId
						!= Configuration.spaceDimId
				|| worldObj.getTileEntity(xCoord, yCoord, zCoord) != this
				|| player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D,
						zCoord + 0.5D) > 64D)
			return false;
		ISpaceObject actorStation = SpaceObjectManager.getSpaceManager()
				.getSpaceStationFromBlockCoords((int)player.posX,
						(int)player.posZ);
		return actorStation != null && actorStation == getSpaceObject();
	}

	private boolean isTargetKnown(StationTarget target) {
		SpaceObject object = getSpaceObject();
		return StationDestinationService.isKnown(object, target);
	}

	@Override
	public void onSystemFocusChanged(Object sender) {
		PacketHandler.sendToServer(new PacketMachine(this, (byte)1));
	}


	@Override
	public float getNormallizedProgress(int id) {
		//Screw it, the darn thing will stop updating inv in certain circumstances
		if(worldObj.isRemote) {
			setPlanetModuleInfo();
		}

		return getProgress(id)/(float)getTotalProgress(id);
	}

	@Override
	public void setProgress(int id, int progress) {
		if(id == 10) {
			if(getSpaceObject() != null)
				getSpaceObject().setFuelAmount(progress);
		}
		else if(id == 3) {
			this.progress = progress;
		}
	}

	@Override
	public int getProgress(int id) {
		if(id == 10) {
			if(getSpaceObject() != null)
				return getSpaceObject().getFuelAmount();
		}

		if(id == 0)
			return 30;
		else if(id == 1)
			return 30;
		else if(id == 2)
			return (int) 30;
		else if(id == 3) {
			return progress == -1 ? 0 : progress;
		}
		return 0;
	}

	@Override
	public int getTotalProgress(int id) {
		if(id == 10) {
			if(getSpaceObject() != null)
				return getSpaceObject().getMaxFuelAmount();
		}
		if(dimCache == null)
			return 0;
		if(id == 0)
			return dimCache.getAtmosphereDensity()/16;
		else if(id == 1)
			return dimCache.orbitalDist/16;
		else if(id == 2)
			return (int) (dimCache.gravitationalMultiplier*50);
		else if(id == 3) {
			return MAX_PROGRESS;
		}

		return 0;
	}

	@Override
	public void setTotalProgress(int id, int progress) {
	}


	@Override
	public void setData(int id, int value) {
		// Vanilla's progress update payload is a signed short. Split each
		// full target/cost integer into low and high unsigned halves.
		if(id == 0)
			dstPlanet = mergeLowHalf(dstPlanet, value);
		else if(id == 1)
			srcPlanet = mergeLowHalf(srcPlanet, value);
		else if(id == 2)
			warpCost = mergeLowHalf(warpCost, value);
		else if(id == 3)
			dstPlanet = mergeHighHalf(dstPlanet, value);
		else if(id == 4)
			srcPlanet = mergeHighHalf(srcPlanet, value);
		else if(id == 5)
			warpCost = mergeHighHalf(warpCost, value);

		if(id == 0 || id == 1 || id == 3 || id == 4)
			setPlanetModuleInfo();
	}


	@Override
	public int getData(int id) {
		ISpaceObject station = getSpaceObject();
		int source = station == null ? 0 : station.getOrbitingPlanetId();
		int destination = station == null
				? 0 : station.getDestOrbitingBody();
		int cost = getTravelCost();
		if(id == 0)
			return destination & 0xFFFF;
		if(id == 1)
			return source & 0xFFFF;
		if(id == 2)
			return cost & 0xFFFF;
		if(id == 3)
			return destination >>> 16;
		if(id == 4)
			return source >>> 16;
		if(id == 5)
			return cost >>> 16;
		return 0;
	}

	private static int mergeLowHalf(int current, int lowHalf) {
		return (current & 0xFFFF0000) | (lowHalf & 0xFFFF);
	}

	private static int mergeHighHalf(int current, int highHalf) {
		return (current & 0xFFFF) | ((highHalf & 0xFFFF) << 16);
	}

	@Override
	public void onModuleUpdated(ModuleBase module) {
		//ReopenUI on server
		PacketHandler.sendToServer(new PacketMachine(this, TAB_SWITCH));
	}


	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		return inv.getStackInSlotOnClosing(slot);
	}


	@Override
	public String getInventoryName() {
		return getModularInventoryName();
	}


	@Override
	public boolean hasCustomInventoryName() {
		return false;
	}


	@Override
	public void openInventory() {
		inv.openInventory();
	}


	@Override
	public void closeInventory() {
		inv.closeInventory();
	}
	
	@Override
	public int getSizeInventory() {
		return inv.getSizeInventory();
	}


	@Override
	public ItemStack getStackInSlot(int index) {
		return inv.getStackInSlot(index);
	}


	@Override
	public ItemStack decrStackSize(int index, int count) {
		return inv.decrStackSize(index, count);
	}

	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		inv.setInventorySlotContents(index, stack);

	}


	@Override
	public int getInventoryStackLimit() {
		return inv.getInventoryStackLimit();
	}


	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		return isAuthorizedActor(player);
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return inv.isItemValidForSlot(index, stack);
	}

	@Override
	public void loadData(int id) {
		ItemStack stack = null;
		
		//Use an unused datatype for now
		DataType type = DataType.HUMIDITY;
		
		if(id == 0) 
		{
			stack = inv.getStackInSlot(DISTANCESLOT);
			type = DataType.DISTANCE;
		}
		else if (id == 1)
		{
			stack = inv.getStackInSlot(MASSSLOT);
			type = DataType.MASS;
		}
		else if(id == 2)
		{
			stack = inv.getStackInSlot(COMPOSITION);
			type = DataType.COMPOSITION;
		}

		if(stack != null && stack.getItem() instanceof ItemData) {
			ItemData item = (ItemData) stack.getItem();
			if(item.getDataType(stack) == type)
				item.removeData(stack, this.addData(item.getData(stack), item.getDataType(stack), ForgeDirection.UP, true), type);
		}

		if(worldObj.isRemote) {
			PacketHandler.sendToServer(new PacketMachine(this, (byte)(LOAD_DATA + id)));
		}
	}


	@Override
	public void storeData(int id) {
		ItemStack stack = null;
		DataType type = null;
		if(id == 0) {
			stack = inv.getStackInSlot(DISTANCESLOT);
			type = DataType.DISTANCE;
		}
		else if (id == 1) {
			stack = inv.getStackInSlot(MASSSLOT);
			type = DataType.MASS;
		}
		else if(id == 2) {
			stack = inv.getStackInSlot(COMPOSITION);
			type = DataType.COMPOSITION;
		}

		if(stack != null && stack.getItem() instanceof ItemData) {
			ItemData item = (ItemData) stack.getItem();
			data.extractData(item.addData(stack, data.getDataAmount(type), type), type, ForgeDirection.UP, true);
		}

		if(worldObj.isRemote) {
			PacketHandler.sendToServer(new PacketMachine(this, (byte)(STORE_DATA + id)));
		}
	}

	boolean meetsArtifactRequirements(DimensionProperties properties) {
		//Make sure we have all the artifacts
		if(properties == null)
			return false;
		if(properties.getRequiredArtifacts().isEmpty())
			return true;
		
		List<ItemStack> list = new LinkedList<ItemStack>(properties.getRequiredArtifacts());
		for(int i = ARTIFACT_BEGIN_RANGE; i <= ARTIFACT_END_RANGE; i++) {
			ItemStack stack2 = getStackInSlot(i);
			if(stack2 != null) {
				Iterator<ItemStack> itr = list.iterator();
				while(itr.hasNext()) {
					ItemStack stackInList = itr.next();
					if(stackInList.getItem().equals(stack2.getItem()) && stackInList.getItemDamage() == stack2.getItemDamage()
							&& ItemStack.areItemStackTagsEqual(stackInList, stack2) && stack2.stackSize >= stackInList.stackSize)
						itr.remove();
				}
			}
		}
		
		return list.isEmpty();
	}
	
	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && CompatibilityMgr.openComputersLoaded) {
			ensureOpenComputersNode();
			updateOpenComputersWarpSignals();
		}
		if(!worldObj.isRemote && progress != -1) {
			progress++;
			if(progress >= MAX_PROGRESS) {
				//Do the thing
				SpaceObject obj = getSpaceObject();
				if(Math.abs(worldObj.rand.nextInt()) % Configuration.planetDiscoveryChance == 0 && obj != null) {
					ItemStack stack = getStackInSlot(PLANETSLOT);
					if(stack != null && stack.getItem() instanceof ItemPlanetIdentificationChip) {
						ItemPlanetIdentificationChip item = (ItemPlanetIdentificationChip)stack.getItem();
						List<Integer> unknownPlanets = new LinkedList<Integer>();
						
						//Check to see if any planets with artifacts can be discovered
						for(int id : DimensionManager.getInstance().getLoadedDimensions()) {
							DimensionProperties props = DimensionManager.getInstance().getDimensionProperties(id);
							if(!isPlanetKnown(props) && !props.getRequiredArtifacts().isEmpty()) {
								//If all artifacts are met, then add
								if(meetsArtifactRequirements(props))
									unknownPlanets.add(id);
							}
						}

						//if there are not any planets requiring artifacts then get the regular planets
						if(unknownPlanets.isEmpty()) {
							for(int id : DimensionManager.getInstance().getLoadedDimensions()) {
								DimensionProperties props = DimensionManager.getInstance().getDimensionProperties(id);
								if(!isPlanetKnown(props) && props.getRequiredArtifacts().isEmpty()) {
									unknownPlanets.add(id);
								}
							}
						}

						if(!unknownPlanets.isEmpty()) {
							int newId = (int)(worldObj.rand.nextFloat()*unknownPlanets.size());
							newId = unknownPlanets.get(newId);
							item.setDimensionId(stack, newId);
							obj.discoverPlanet(newId);
						}
					}
				}
				data.extractData(100, DataType.COMPOSITION, ForgeDirection.UP, true);
				data.extractData(100, DataType.DISTANCE, ForgeDirection.UP, true);
				data.extractData(100, DataType.MASS, ForgeDirection.UP, true);

				progress = -1;
			}
		}

	}

	@Optional.Method(modid = "OpenComputers")
	private void ensureOpenComputersNode() {
		if(openComputersNode == null) {
			openComputersNode = OpenComputersNetworkNodeSupport.create(this,
					"warp_controller", pendingOpenComputersNodeData);
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
	private void updateOpenComputersWarpSignals() {
		SpaceObject object = getSpaceObject();
		if(object == null) {
			openComputersSignalBaseline = false;
			return;
		}
		int orbit = object.getOrbitingPlanetId();
		if(!openComputersSignalBaseline) {
			openComputersLastOrbit = orbit;
			openComputersSignalBaseline = true;
			return;
		}
		if(orbit == openComputersLastOrbit)
			return;

		if(openComputersLastOrbit != SpaceObjectManager.WARPDIMID
				&& orbit == SpaceObjectManager.WARPDIMID) {
			long remaining = Math.max(0L, object.getTransitionTime()
					- worldObj.getTotalWorldTime());
			OpenComputersNetworkNodeSupport.sendSignal(openComputersNode,
					"warp_started", object.getId(),
					object.getDestOrbitingBody(), remaining);
		}
		else if(openComputersLastOrbit == SpaceObjectManager.WARPDIMID
				&& orbit != SpaceObjectManager.WARPDIMID)
			OpenComputersNetworkNodeSupport.sendSignal(openComputersNode,
					"warp_finished", object.getId(), orbit);
		openComputersLastOrbit = orbit;
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

	@Callback(doc = "function():number -- Returns the current orbit target; unavailable during warp.")
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

	@Callback(doc = "function():table -- Returns the current orbit target id, kind and display name; unavailable during warp.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getCurrentTargetInfo(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		SpaceObject station = getAutomationStation(access);
		if(station == null)
			return OpenComputersComponentAccess.getterError(
					"not_on_station", "Unsupported station implementation.");
		return describeCurrentTarget(station);
	}

	static Object[] describeCurrentTarget(SpaceObject station) {
		if(station == null)
			return OpenComputersComponentAccess.getterError(
					"not_on_station",
					"Component is not on a valid space station.");
		int currentTargetId = station.getOrbitingPlanetId();
		if(currentTargetId == SpaceObjectManager.WARPDIMID)
			return OpenComputersComponentAccess.getterError("in_warp",
					"Station is currently in warp.");
		StationTarget target = StationTargetResolver.getInstance()
				.resolve(currentTargetId);
		if(!target.isDestination())
			return OpenComputersComponentAccess.getterError(
					"invalid_target",
					"Current orbit target is invalid.");
		Map<String, Object> info = StationDestinationService.describe(
				station, target);
		Object name = info.get("name");
		if(!(name instanceof String) || ((String)name).trim().isEmpty())
			return OpenComputersComponentAccess.getterError(
					"invalid_target",
					"Current orbit target has no display name.");
		return new Object[] { info };
	}

	@Callback(doc = "function():number -- Returns the committed station destination.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getDestination(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { access.getSpaceObject().getDestOrbitingBody() };
	}

	@Callback(doc = "function(id:number):boolean, number|string -- Sets a known valid station destination.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setDestination(Context context, Arguments args) {
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
		dimCache = result.getTarget().getDimensionProperties();
		markDirty();
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		return new Object[] { true, id };
	}

	@Callback(doc = "function():number -- Returns the current station warp-fuel travel cost.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getTravelCost(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		SpaceObject station = getAutomationStation(access);
		int cost = StationWarpService.calculateTravelCost(station);
		if(cost <= 0 || cost == Integer.MAX_VALUE)
			return OpenComputersComponentAccess.getterError(
					"invalid_travel_cost",
					"No finite positive travel cost is available.");
		return new Object[] { cost };
	}

	@Callback(doc = "function():number -- Returns current station warp fuel.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFuelAmount(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		SpaceObject station = getAutomationStation(access);
		if(station == null)
			return OpenComputersComponentAccess.getterError(
					"not_on_station", "Unsupported station implementation.");
		return new Object[] { station.getFuelAmount() };
	}

	@Callback(doc = "function():boolean -- Returns whether the station is in warp.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isInWarp(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { access.getSpaceObject().getOrbitingPlanetId()
				== SpaceObjectManager.WARPDIMID };
	}

	@Callback(doc = "function():boolean, string -- Returns warp readiness and a stable reason code.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] canWarp(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return new Object[] { false, access.getErrorCode() };
		StationWarpService.Status status = StationWarpService.evaluate(this,
				getAutomationStation(access), null);
		return new Object[] { status.canWarp(), status.getReason() };
	}

	@Callback(doc = "function(expectedDestinationId?:number):boolean, number|string, number|string -- Starts one validated atomic station warp.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] warp(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asMutatorError();
		SpaceObject station = getAutomationStation(access);
		if(station == null)
			return OpenComputersComponentAccess.mutatorError(
					"not_on_station", "Unsupported station implementation.");
		Integer expected = args.count() == 0 ? null
				: Integer.valueOf(args.checkInteger(0));
		StationWarpService.Result result = StationWarpService.tryWarp(this,
				station, expected);
		if(!result.isSuccess())
			return OpenComputersComponentAccess.mutatorError(
					result.getErrorCode(), result.getErrorMessage());
		return new Object[] { true, result.getDestinationId(),
				result.getRemainingTicks() };
	}

	@Callback(doc = "function():table -- Returns a complete station warp-state snapshot.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		SpaceObject station = getAutomationStation(access);
		if(station == null)
			return OpenComputersComponentAccess.getterError(
					"not_on_station", "Unsupported station implementation.");
		StationWarpService.Status readiness = StationWarpService.evaluate(
				this, station, null);
		StationTarget destination = StationTargetResolver.getInstance()
				.resolve(station.getDestOrbitingBody());
		int cost = StationWarpService.calculateTravelCost(station);
		boolean inWarp = station.getOrbitingPlanetId()
				== SpaceObjectManager.WARPDIMID;
		long remaining = inWarp ? Math.max(0L,
				station.getTransitionTime()-worldObj.getTotalWorldTime()) : 0L;
		boolean artifacts = destination.getDimensionProperties() != null
				&& meetsArtifactRequirements(
						destination.getDimensionProperties());

		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("stationId", station.getId());
		status.put("currentTargetId", station.getOrbitingPlanetId());
		status.put("destinationTargetId",
				station.getDestOrbitingBody());
		status.put("inWarp", inWarp);
		status.put("travelCost", cost == Integer.MAX_VALUE ? -1 : cost);
		status.put("fuelAmount", station.getFuelAmount());
		status.put("fuelCapacity", station.getMaxFuelAmount());
		status.put("hasUsableWarpCore", station.hasUsableWarpCore());
		status.put("hasRequiredArtifacts", artifacts);
		status.put("canWarp", readiness.canWarp());
		status.put("reason", readiness.getReason());
		status.put("remainingTicks", remaining);
		return new Object[] { status };
	}


	@Override
	public boolean isPlanetKnown(IDimensionProperties properties) {
		if(properties == null)
			return false;
		SpaceObject obj = getSpaceObject();
		if(obj != null)
			return obj.isPlanetKnown(properties);
		return false;
	}


	@Override
	public boolean isStarKnown(StellarBody body) {
		if(body == null)
			return false;
		SpaceObject obj = getSpaceObject();
		if(obj != null)
			return obj.isStarKnown(body);
		return false;
	}
}
