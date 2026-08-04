package zmaster587.advancedRocketry.tile.multiblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeManager.BiomeEntry;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationTarget;
import zmaster587.advancedRocketry.stations.StationTargetResolver;

/** Shared GUI and automation scan rules for the orbital biome scanner. */
public final class BiomeScanService {

	private BiomeScanService() {
	}

	public static Result scan(TileBiomeScanner scanner,
			ISpaceObject station) {
		if(scanner == null || station == null)
			return Result.error("not_on_station",
					"Biome scanner is not on a valid space station.");
		if(!scanner.isComplete())
			return Result.error("incomplete_multiblock",
					"Biome scanner multiblock is incomplete.");
		if(!scanner.hasClearScanPath())
			return Result.error("obstructed",
					"Biome scanner observation path is obstructed.");
		if(station.getOrbitingPlanetId() == SpaceObjectManager.WARPDIMID)
			return Result.error("in_warp",
					"Biome scanner cannot scan while the station is in warp.");

		StationTarget target = StationTargetResolver.getInstance()
				.resolve(station.getOrbitingPlanetId());
		if(target.getKind() == StationTarget.Kind.BLACK_HOLE_STAR)
			return Result.error("no_surface",
					"Black holes have no biome surface to scan.");
		if(target.getKind() != StationTarget.Kind.DIMENSION
				|| target.getDimensionProperties() == null)
			return Result.error("invalid_target",
					"Station is not orbiting a scannable AR dimension.");

		DimensionProperties properties = target.getDimensionProperties();
		if(properties.isGasGiant())
			return Result.error("no_surface",
					"Gas giants have no biome surface to scan.");

		TreeMap<Integer, BiomeGenBase> byId =
				new TreeMap<Integer, BiomeGenBase>();
		if(properties.getId() == 0) {
			for(BiomeGenBase biome : BiomeGenBase.getBiomeGenArray())
				if(biome != null)
					byId.put(biome.biomeID, biome);
		}
		else {
			for(BiomeEntry entry : properties.getBiomes())
				if(entry != null && entry.biome != null)
					byId.put(entry.biome.biomeID, entry.biome);
		}

		return Result.success(target, new ArrayList<BiomeGenBase>(
				byId.values()));
	}

	public static List<Map<String, Object>> toComputerTable(
			List<BiomeGenBase> biomes) {
		List<Map<String, Object>> result =
				new ArrayList<Map<String, Object>>();
		for(BiomeGenBase biome : biomes) {
			Map<String, Object> entry =
					new LinkedHashMap<String, Object>();
			entry.put("id", biome.biomeID);
			entry.put("name", biome.biomeName == null
					? "" : biome.biomeName);
			entry.put("modId", BiomeModIdResolver.resolve(biome));
			result.add(entry);
		}
		return result;
	}

	public static final class Result {
		private final StationTarget target;
		private final List<BiomeGenBase> biomes;
		private final String errorCode;
		private final String errorMessage;

		private Result(StationTarget target, List<BiomeGenBase> biomes,
				String errorCode, String errorMessage) {
			this.target = target;
			this.biomes = biomes;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
		}

		private static Result success(StationTarget target,
				List<BiomeGenBase> biomes) {
			return new Result(target,
					Collections.unmodifiableList(biomes), null, null);
		}

		private static Result error(String code, String message) {
			return new Result(null, Collections.<BiomeGenBase>emptyList(),
					code, message);
		}

		public boolean isSuccess() {
			return errorCode == null;
		}

		public StationTarget getTarget() {
			return target;
		}

		public List<BiomeGenBase> getBiomes() {
			return biomes;
		}

		public String getErrorCode() {
			return errorCode;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}
}
