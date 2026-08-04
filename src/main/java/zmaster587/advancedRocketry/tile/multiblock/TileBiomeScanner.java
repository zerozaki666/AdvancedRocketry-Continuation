package zmaster587.advancedRocketry.tile.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.common.Optional;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.biome.BiomeGenBase;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess;
import zmaster587.advancedRocketry.integration.opencomputers.OpenComputersComponentAccess.Result;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.api.LibVulpesBlocks;
import zmaster587.libVulpes.inventory.modules.ModuleBase;
import zmaster587.libVulpes.inventory.modules.ModuleContainerPan;
import zmaster587.libVulpes.inventory.modules.ModuleImage;
import zmaster587.libVulpes.inventory.modules.ModuleText;
import zmaster587.libVulpes.tile.multiblock.TileMultiPowerConsumer;

@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")
public class TileBiomeScanner extends TileMultiPowerConsumer implements SimpleComponent {

	private static final Object[][][] structure = new Object[][][] {
			{	{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, 'c', null, null},
				{null, null, null, null, null},
				{null, null, null, null, null}},

			{	{null, null, null, null, null},
				{null, null, null, null, null},
				{null, null, LibVulpesBlocks.motors, null, null},
				{null, null, null, null, null},
				{null, null, null, null, null}},

			{	{null, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, null},
				{Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block},
				{Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block},
				{Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block},
				{null, Blocks.iron_block, Blocks.iron_block, Blocks.iron_block, null}},

			{	{Blocks.air, Blocks.air, Blocks.air, Blocks.air, Blocks.air},
				{Blocks.air, Blocks.air, Blocks.air, Blocks.air, Blocks.air},
				{Blocks.air, Blocks.air, Blocks.redstone_block, Blocks.air, Blocks.air},
				{Blocks.air, Blocks.air, Blocks.air, Blocks.air, Blocks.air},
				{Blocks.air, Blocks.air, Blocks.air, Blocks.air, Blocks.air}}
	};

	@Override
	public Object[][][] getStructure() {
		return structure;
	}

	@Override
	public List<ModuleBase> getModules(int id, EntityPlayer player) {
		List<ModuleBase> list = new LinkedList<ModuleBase>();
		if(worldObj.isRemote)
			list.add(new ModuleImage(24, 14,
					zmaster587.advancedRocketry.inventory.TextureResources.earthCandyIcon));

		ISpaceObject station = SpaceObjectManager.getSpaceManager()
				.getSpaceStationFromBlockCoords(xCoord, zCoord);
		BiomeScanService.Result scan = BiomeScanService.scan(this, station);
		if(scan.isSuccess()) {
			List<ModuleBase> biomeModules = new LinkedList<ModuleBase>();
			int index = 0;
			for(BiomeGenBase biome : scan.getBiomes())
				biomeModules.add(new ModuleText(32, 16 + 12*(index++),
						biome.biomeName, 0x202020));
			list.add(new ModuleContainerPan(0, 16, biomeModules,
					new LinkedList<ModuleBase>(), null, 148, 128, 0, -64,
					0, 1000));
		}
		else if("no_surface".equals(scan.getErrorCode()))
			list.add(new ModuleText(32, 16,
					LibVulpes.proxy.getLocalizedString("msg.biomescanner.gas"),
					0x202020));
		else
			list.add(new ModuleText(32, 16,
					EnumChatFormatting.OBFUSCATED + "Foxes, that is all",
					0x202020));
		return list;
	}

	public boolean hasClearScanPath() {
		if(worldObj == null)
			return false;
		for(int y = yCoord - 4; y > 0; y--)
			if(!worldObj.isAirBlock(xCoord, y, zCoord))
				return false;
		return true;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 5, yCoord - 3,
				zCoord - 5, xCoord + 5, yCoord + 3, zCoord + 5);
	}

	@Override
	public String getMachineName() {
		return "tile.biomeScanner.name";
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "biome_scanner";
	}

	@Callback(doc = "function():table -- Scans biome id/name/modId entries for the current orbit target.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] scan(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		BiomeScanService.Result scan = BiomeScanService.scan(this,
				access.getSpaceObject());
		if(!scan.isSuccess())
			return OpenComputersComponentAccess.getterError(
					scan.getErrorCode(), scan.getErrorMessage());
		return new Object[] { BiomeScanService.toComputerTable(
				scan.getBiomes()) };
	}

	@Callback(doc = "function():table -- Scans biome display names for the current orbit target.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] scanNames(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		BiomeScanService.Result scan = BiomeScanService.scan(this,
				access.getSpaceObject());
		if(!scan.isSuccess())
			return OpenComputersComponentAccess.getterError(
					scan.getErrorCode(), scan.getErrorMessage());
		List<String> names = new ArrayList<String>();
		for(BiomeGenBase biome : scan.getBiomes())
			names.add(biome.biomeName == null ? "" : biome.biomeName);
		return new Object[] { names };
	}

	@Callback(doc = "function():table -- Returns multiblock, observation path, station, and scan state.")
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStatus(Context context, Arguments args) {
		Result access = OpenComputersComponentAccess.resolveStation(this);
		if(!access.isValid())
			return access.asGetterError();
		BiomeScanService.Result scan = BiomeScanService.scan(this,
				access.getSpaceObject());
		Map<String, Object> status = new LinkedHashMap<String, Object>();
		status.put("stationId", access.getSpaceObject().getId());
		status.put("currentTargetId",
				access.getSpaceObject().getOrbitingPlanetId());
		status.put("multiblockComplete", isComplete());
		status.put("obstructed", !hasClearScanPath());
		status.put("scannable", scan.isSuccess());
		status.put("state", scan.isSuccess() ? "ready"
				: scan.getErrorCode());
		status.put("biomeCount", scan.getBiomes().size());
		return new Object[] { status };
	}
}
