package zmaster587.advancedRocketry.stations;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.Constants;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.stations.StationTarget.Kind;

/**
 * The only resolver for station orbit/destination integers.
 *
 * <p>DimensionManager deliberately retains its legacy "unknown means
 * overworld" behavior for addon compatibility.  This resolver is fail-closed
 * and must therefore be used for every station-controlled target.</p>
 */
public final class StationTargetResolver {

	private static final StationTargetResolver INSTANCE =
			new StationTargetResolver();
	private static final int ALTERNATE_STAR_ID_BASE = Integer.MIN_VALUE + 1;

	private final Map<Integer, DimensionProperties> starProxyCache =
			new HashMap<Integer, DimensionProperties>();

	private StationTargetResolver() {
	}

	public static StationTargetResolver getInstance() {
		return INSTANCE;
	}

	public StationTarget resolve(int rawId) {
		if(rawId == SpaceObjectManager.WARPDIMID)
			return new StationTarget(rawId, Kind.WARP,
					DimensionManager.defaultSpaceDimensionProperties, null);

		// Existing real dimensions always win.  This preserves worlds and
		// modpacks that legitimately use IDs in the old synthetic target band.
		DimensionProperties properties = DimensionManager.getInstance()
				.getDimensionPropertiesExact(rawId);
		if(properties != null) {
			if(rawId == Configuration.spaceDimId
					|| rawId == Configuration.freeSpaceDimId)
				return invalid(rawId);
			return new StationTarget(rawId, Kind.DIMENSION, properties,
					properties.getStar());
		}
		// A Forge dimension not owned by AR has no safe DimensionProperties
		// contract. Never reinterpret its ID as a synthetic stellar target.
		if(net.minecraftforge.common.DimensionManager
				.isDimensionRegistered(rawId))
			return invalid(rawId);

		Integer decodedId = decodeSelectorStarId(rawId);
		if(decodedId != null) {
			int starId = decodedId.intValue();
			StellarBody star = DimensionManager.getInstance().getStar(starId);
			if(star == null || !star.isBlackHole())
				return invalid(rawId);

			DimensionProperties proxy;
			synchronized(starProxyCache) {
				proxy = starProxyCache.get(rawId);
				if(proxy == null || proxy.getStarData() != star) {
					proxy = DimensionProperties.createStarProxy(star, rawId);
					starProxyCache.put(rawId, proxy);
				}
			}
			return new StationTarget(rawId, Kind.BLACK_HOLE_STAR,
					proxy, star);
		}

		return invalid(rawId);
	}

	/**
	 * Resolves the real orbit body represented by coordinates in a world.
	 * Space-station coordinates are translated through their owning station;
	 * all other worlds use their provider dimension directly.
	 */
	public StationTarget resolveCurrentBody(World world, int x, int z) {
		if(world == null || world.provider == null)
			return invalid(Integer.MIN_VALUE);
		return resolveCurrentBody(world.provider.dimensionId, x, z);
	}

	/**
	 * Resolves the real orbit body represented by coordinates in a dimension.
	 * This overload is used by links whose destination world may be unloaded.
	 */
	public StationTarget resolveCurrentBody(int dimensionId, int x, int z) {
		int rawId = dimensionId;
		if(dimensionId == Configuration.spaceDimId) {
			zmaster587.advancedRocketry.api.stations.ISpaceObject station =
					SpaceObjectManager.getSpaceManager()
							.getSpaceStationFromBlockCoords(x, z);
			if(station == null)
				return invalid(rawId);
			rawId = station.getOrbitingPlanetId();
		}
		return resolve(rawId);
	}

	/**
	 * Returns true only for an unclaimed value in the UI's stellar selector
	 * band.  A real dimension with the same integer is never reinterpreted.
	 */
	public boolean isStellarSelectorId(int rawId) {
		return rawId != SpaceObjectManager.WARPDIMID
				&& DimensionManager.getInstance()
						.getDimensionPropertiesExact(rawId) == null
				&& !net.minecraftforge.common.DimensionManager
						.isDimensionRegistered(rawId)
				&& decodeSelectorStarId(rawId) != null;
	}

	public StellarBody getSelectorStar(int rawId) {
		if(!isStellarSelectorId(rawId))
			return null;
		Integer decodedId = decodeSelectorStarId(rawId);
		return decodedId == null ? null
				: DimensionManager.getInstance().getStar(decodedId.intValue());
	}

	/**
	 * Encodes a stellar selector without shadowing an existing real dimension.
	 * The legacy positive encoding remains the default for save compatibility;
	 * a disjoint negative encoding is used only when a modpack already owns
	 * that positive dimension ID.
	 */
	public int getSelectorId(int starId) {
		if(starId < 0 || starId > Integer.MAX_VALUE
				- Constants.STAR_ID_OFFSET)
			throw new IllegalArgumentException(
					"Star id cannot be represented as a station target");

		int primary = Constants.STAR_ID_OFFSET + starId;
		if(isUnclaimedDimensionId(primary))
			return primary;

		int alternate = ALTERNATE_STAR_ID_BASE + starId;
		if(isUnclaimedDimensionId(alternate)
				&& alternate != SpaceObjectManager.WARPDIMID)
			return alternate;

		throw new IllegalStateException("No collision-free station target ID "
				+ "is available for star " + starId);
	}

	public boolean isAllowedDestination(int rawId, EntityPlayerMP actor) {
		if(actor == null || actor.worldObj == null
				|| actor.worldObj.provider.dimensionId
						!= Configuration.spaceDimId)
			return false;
		return resolve(rawId).isDestination();
	}

	public void invalidateCache() {
		synchronized(starProxyCache) {
			starProxyCache.clear();
		}
	}

	private static StationTarget invalid(int rawId) {
		return new StationTarget(rawId, Kind.INVALID, null, null);
	}

	private boolean isUnclaimedDimensionId(int rawId) {
		return rawId != Configuration.spaceDimId
				&& rawId != Configuration.freeSpaceDimId
				&& DimensionManager.getInstance()
						.getDimensionPropertiesExact(rawId) == null
				&& !net.minecraftforge.common.DimensionManager
						.isDimensionRegistered(rawId);
	}

	private static Integer decodeSelectorStarId(int rawId) {
		long decodedId;
		if(rawId >= Constants.STAR_ID_OFFSET)
			decodedId = (long)rawId - Constants.STAR_ID_OFFSET;
		else if(rawId > Integer.MIN_VALUE
				&& rawId <= -Constants.STAR_ID_OFFSET)
			decodedId = (long)rawId - ALTERNATE_STAR_ID_BASE;
		else
			return null;

		if(decodedId < 0L || decodedId > Integer.MAX_VALUE
				- Constants.STAR_ID_OFFSET)
			return null;
		return Integer.valueOf((int)decodedId);
	}
}
