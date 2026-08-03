package zmaster587.advancedRocketry.tile;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.IAtmosphere;
import zmaster587.advancedRocketry.api.atmosphere.AtmosphereRegister;
import zmaster587.advancedRocketry.atmosphere.AtmosphereHandler;
import zmaster587.advancedRocketry.atmosphere.AtmosphereType;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess.Result;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.inventory.modules.IButtonInventory;
import zmaster587.libVulpes.inventory.modules.IModularInventory;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleButton;
import zmaster587.libVulpes.inventory.modules.ModuleContainerPan;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.util.INetworkMachine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")
public class TileAtmosphereDetector extends TileEntity implements IModularInventory, IButtonInventory, INetworkMachine, SimpleComponent {

	IAtmosphere atmosphereToDetect;

	public TileAtmosphereDetector() {
		atmosphereToDetect = AtmosphereType.AIR;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && worldObj.getWorldTime() % 10 == 0) {
			int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
			boolean detectedAtm = false;

			//TODO: Galacticcraft support
			if(AtmosphereHandler.getOxygenHandler(worldObj.provider.dimensionId) == null) {
				detectedAtm = atmosphereToDetect == AtmosphereType.AIR;
			}
			else {
				for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
					detectedAtm = (!worldObj.getBlock(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ).isOpaqueCube() && atmosphereToDetect == AtmosphereHandler.getOxygenHandler(worldObj.provider.dimensionId).getAtmosphereType(xCoord + direction.offsetX, yCoord + direction.offsetY, zCoord + direction.offsetZ));
					if(detectedAtm) break;
				}
			}

			if((meta == 1) != detectedAtm) {
				worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, detectedAtm ? 1 : 0, 3);
			}
		}
	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();
		List<ModuleBase> btns = new LinkedList<ModuleBase>();

		Iterator<IAtmosphere> atmIter = AtmosphereRegister.getInstance().getAtmosphereList().iterator();

		int i = 0;
		while(atmIter.hasNext()) {
			IAtmosphere atm = atmIter.next();
			btns.add(new ModuleButton(60, 4 + i*24, i, LibVulpes.proxy.getLocalizedString(atm.getUnlocalizedName()), this,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild));
			i++;
		}

		ModuleContainerPan panningContainer = new ModuleContainerPan(5, 20, btns, new LinkedList<ModuleBase>(), zmaster587.libVulpes.inventory.TextureResources.starryBG, 165, 120, 0, 500);
		modules.add(panningContainer);
		return modules;
	}

	@Override
	public String getModularInventoryName() {
		return "atmosphereDetector";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public void onInventoryButtonPressed(int buttonId) {
		List<IAtmosphere> atmospheres = AtmosphereRegister.getInstance()
				.getAtmosphereList();
		if(buttonId >= 0 && buttonId < atmospheres.size()) {
			setTargetAtmosphere(atmospheres.get(buttonId));
			PacketHandler.sendToServer(new PacketMachine(this, (byte)0));
		}
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		//Send the unlocalized name over the net to reduce chances of foulup due to client/server inconsistencies
		if(id == 0) {
			PacketBuffer buf = new PacketBuffer(out);
			try {
				buf.writeShort(atmosphereToDetect.getUnlocalizedName().length());
				buf.writeStringToBuffer(atmosphereToDetect.getUnlocalizedName());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == 0) {
			PacketBuffer buf = new PacketBuffer(in);
			try {

				nbt.setString("uName", buf.readStringFromBuffer(buf.readShort()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(id == 0) {
			String name = nbt.getString("uName");
			IAtmosphere atmosphere = findRegisteredAtmosphere(name);
			if(atmosphere != null)
				setTargetAtmosphere(atmosphere);
		}
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setString("atmName", atmosphereToDetect.getUnlocalizedName());
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net,
			S35PacketUpdateTileEntity packet) {
		IAtmosphere atmosphere = findRegisteredAtmosphere(
				packet.func_148857_g().getString("atmName"));
		atmosphereToDetect = atmosphere == null
				? AtmosphereType.AIR : atmosphere;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setString("atmName", atmosphereToDetect.getUnlocalizedName());
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		IAtmosphere atmosphere = nbt.hasKey("atmName")
				? findRegisteredAtmosphere(nbt.getString("atmName")) : null;
		atmosphereToDetect = atmosphere == null
				? AtmosphereType.AIR : atmosphere;
	}

	private void setTargetAtmosphere(IAtmosphere atmosphere) {
		if(atmosphere == null)
			atmosphere = AtmosphereType.AIR;
		if(atmosphereToDetect != atmosphere) {
			atmosphereToDetect = atmosphere;
			if(worldObj != null && !worldObj.isRemote) {
				markDirty();
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}
		}
	}

	private IAtmosphere findRegisteredAtmosphere(String id) {
		if(id == null)
			return null;
		for(IAtmosphere atmosphere : AtmosphereRegister.getInstance()
				.getAtmosphereList())
			if(id.equals(atmosphere.getUnlocalizedName()))
				return atmosphere;
		return null;
	}

	private IAtmosphere getAtmosphereAtSide(int side) {
		ForgeDirection direction = ForgeDirection.getOrientation(side);
		AtmosphereHandler handler = AtmosphereHandler.getOxygenHandler(
				worldObj.provider.dimensionId);
		if(handler == null)
			return AtmosphereType.AIR;
		return handler.getAtmosphereType(xCoord + direction.offsetX,
				yCoord + direction.offsetY, zCoord + direction.offsetZ);
	}

	private Map<String, Object> atmosphereInfo(IAtmosphere atmosphere) {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("id", atmosphere.getUnlocalizedName());
		result.put("breathable", atmosphere.isBreathable());
		result.put("allowsCombustion", atmosphere.allowsCombustion());
		return result;
	}

	private Object[] validateSide(Arguments args) {
		int side = args.checkInteger(0);
		if(side < 0 || side > 5)
			return OpenComputersComponentAccess.getterError("invalid_side",
					"Atmosphere side must be between 0 and 5.");
		return null;
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "atmosphere_detector";
	}

	@Callback(doc = "function():table -- Returns registered atmosphere ids in stable order.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] listAtmospheres(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		List<String> ids = new ArrayList<String>();
		for(IAtmosphere atmosphere : AtmosphereRegister.getInstance()
				.getAtmosphereList())
			ids.add(atmosphere.getUnlocalizedName());
		Collections.sort(ids);
		return new Object[] { ids };
	}

	@Callback(doc = "function(side:number):table -- Returns atmosphere information for an adjacent side (0..5).")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getAtmosphere(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		Object[] error = validateSide(args);
		if(error != null)
			return error;
		return new Object[] { atmosphereInfo(
				getAtmosphereAtSide(args.checkInteger(0))) };
	}

	@Callback(doc = "function(side:number):string -- Returns the stable atmosphere id for an adjacent side.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getAtmosphereType(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		Object[] error = validateSide(args);
		if(error != null)
			return error;
		return new Object[] { getAtmosphereAtSide(args.checkInteger(0))
				.getUnlocalizedName() };
	}

	@Callback(doc = "function(side:number):boolean -- Returns whether an adjacent atmosphere is breathable.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isBreathable(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		Object[] error = validateSide(args);
		if(error != null)
			return error;
		return new Object[] { getAtmosphereAtSide(args.checkInteger(0))
				.isBreathable() };
	}

	@Callback(doc = "function(side:number):boolean -- Returns whether an adjacent atmosphere allows combustion.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] allowsCombustion(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		Object[] error = validateSide(args);
		if(error != null)
			return error;
		return new Object[] { getAtmosphereAtSide(args.checkInteger(0))
				.allowsCombustion() };
	}

	@Callback(doc = "function():string -- Returns the detector target atmosphere id.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getTargetAtmosphere(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { atmosphereToDetect.getUnlocalizedName() };
	}

	@Callback(doc = "function(id:string):boolean, string -- Sets the exact registered detector target.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] setTargetAtmosphere(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asMutatorError();
		String id = args.checkString(0);
		IAtmosphere atmosphere = findRegisteredAtmosphere(id);
		if(atmosphere == null)
			return OpenComputersComponentAccess.mutatorError(
					"unknown_atmosphere",
					"Atmosphere id is not registered: " + id);
		setTargetAtmosphere(atmosphere);
		return new Object[] { true, atmosphere.getUnlocalizedName() };
	}

	@Callback(doc = "function():boolean -- Returns the detector's current redstone detection state.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] isDetected(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		return new Object[] { worldObj.getBlockMetadata(xCoord, yCoord,
				zCoord) == 1 };
	}

	@Callback(doc = "function():table -- Returns detector target, detection state, power state, and six adjacent atmospheres.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveTile(this);
		if(!access.isValid())
			return access.asGetterError();
		Map<String, Object> sides = new LinkedHashMap<String, Object>();
		for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
			sides.put(direction.name().toLowerCase(Locale.ENGLISH),
					atmosphereInfo(getAtmosphereAtSide(direction.ordinal())));
		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("target", atmosphereToDetect.getUnlocalizedName());
		status.put("detected", worldObj.getBlockMetadata(xCoord, yCoord,
				zCoord) == 1);
		status.put("powered", worldObj.isBlockIndirectlyGettingPowered(
				xCoord, yCoord, zCoord));
		status.put("atmospheres", sides);
		return new Object[] { status };
	}
}
