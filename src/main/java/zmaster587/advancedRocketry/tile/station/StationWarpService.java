package zmaster587.advancedRocketry.tile.station;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import zmaster587.advancedRocketry.achievements.ARAchivements;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.stations.SpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationDestinationService;
import zmaster587.advancedRocketry.stations.StationTarget;
import zmaster587.advancedRocketry.stations.StationTargetResolver;
import zmaster587.advancedRocketry.tile.multiblock.TileWarpCore;
import zmaster587.libVulpes.util.BlockPosition;

/** Canonical, fail-closed station warp validation and execution. */
public final class StationWarpService {

	private StationWarpService() {
	}

	public static Status evaluate(TileWarpShipMonitor tile,
			SpaceObject station, Integer expectedDestination) {
		if(tile == null || station == null)
			return Status.error("not_on_station",
					"Warp controller is not on a valid space station.",
					station);
		if(station.getOrbitingPlanetId() == SpaceObjectManager.WARPDIMID)
			return Status.error("already_in_warp",
					"Station is already in warp.", station);

		int destinationId = station.getDestOrbitingBody();
		if(expectedDestination != null
				&& expectedDestination.intValue() != destinationId)
			return Status.error("destination_changed",
					"Station destination changed before warp began.", station);

		StationTarget destination = StationTargetResolver.getInstance()
				.resolve(destinationId);
		if(!destination.isDestination())
			return Status.error("invalid_destination",
					"Station destination is invalid.", station);
		if(destinationId == station.getOrbitingPlanetId())
			return Status.error("same_destination",
					"Station destination is the current orbit target.",
					station);
		if(!StationDestinationService.isKnown(station, destination))
			return Status.error("unknown_destination",
					"Station has not discovered the destination.", station);

		int cost = calculateTravelCost(station);
		if(cost <= 0 || cost == Integer.MAX_VALUE)
			return Status.error("invalid_travel_cost",
					"No finite positive travel cost is available.", station,
					destination, cost);
		if(!station.hasUsableWarpCore())
			return Status.error("no_usable_warp_core",
					"Station has no usable warp core.", station,
					destination, cost);
		if(station.getFuelAmount() < cost)
			return Status.error("insufficient_fuel",
					"Station does not have enough warp fuel.", station,
					destination, cost);

		DimensionProperties properties = destination.getDimensionProperties();
		if(properties == null)
			return Status.error("invalid_destination",
					"Destination has no usable dimension properties.", station,
					destination, cost);
		if(!tile.meetsArtifactRequirements(properties))
			return Status.error("missing_artifacts",
					"Required destination artifacts are missing.", station,
					destination, cost);

		return Status.ready(station, destination, cost);
	}

	public static Result tryWarp(TileWarpShipMonitor tile,
			SpaceObject station, Integer expectedDestination) {
		Status status = evaluate(tile, station, expectedDestination);
		if(!status.canWarp())
			return Result.error(status.getReason(), status.getMessage());

		int cost = status.getTravelCost();
		if(station.useFuel(cost) != cost)
			return Result.error("insufficient_fuel",
					"Warp fuel changed before it could be consumed.");

		int destinationId = status.getDestination().getRawId();
		int transitionDuration = (int)Math.min(5000L, (long)cost * 5L);
		SpaceObjectManager.getSpaceManager().moveStationToBody(station,
				destinationId, transitionDuration);

		for(Object object : tile.getWorldObj().playerEntities) {
			if(!(object instanceof EntityPlayer))
				continue;
			EntityPlayer player = (EntityPlayer)object;
			if(SpaceObjectManager.getSpaceManager()
					.getSpaceStationFromBlockCoords((int)player.posX,
							(int)player.posZ) == station) {
				player.triggerAchievement(ARAchivements.givingItAllShesGot);
				if(!DimensionManager.hasReachedWarp)
					player.triggerAchievement(
							ARAchivements.flightOfThePhoenix);
			}
		}
		DimensionManager.hasReachedWarp = true;

		for(BlockPosition position : station.getWarpCoreLocations()) {
			TileEntity core = tile.getWorldObj().getTileEntity(position.x,
					position.y, position.z);
			if(core instanceof TileWarpCore)
				((TileWarpCore)core).onInventoryUpdated();
		}
		tile.markDirty();
		tile.getWorldObj().markBlockForUpdate(tile.xCoord, tile.yCoord,
				tile.zCoord);

		long remaining = Math.max(0L, station.getTransitionTime()
				- tile.getWorldObj().getTotalWorldTime());
		return Result.success(destinationId, remaining);
	}

	public static int calculateTravelCost(SpaceObject station) {
		if(station == null)
			return Integer.MAX_VALUE;

		StationTarget source = StationTargetResolver.getInstance()
				.resolve(station.getOrbitingPlanetId());
		StationTarget destination = StationTargetResolver.getInstance()
				.resolve(station.getDestOrbitingBody());
		if(!source.isDestination() || !destination.isDestination()
				|| source.getRawId() == destination.getRawId())
			return Integer.MAX_VALUE;

		DimensionProperties sourceProperties = source.getDimensionProperties();
		DimensionProperties destinationProperties =
				destination.getDimensionProperties();
		if(sourceProperties == null || destinationProperties == null
				|| source.getStellarBody() == null
				|| destination.getStellarBody() == null)
			return Integer.MAX_VALUE;

		if(source.getStellarBody().getId()
				!= destination.getStellarBody().getId())
			return 500;

		if(source.getKind() == StationTarget.Kind.BLACK_HOLE_STAR
				|| destination.getKind()
						== StationTarget.Kind.BLACK_HOLE_STAR) {
			DimensionProperties planet = source.getKind()
					== StationTarget.Kind.BLACK_HOLE_STAR
							? destinationProperties : sourceProperties;
			while(planet.isMoon()) {
				DimensionProperties parent = planet.getParentProperties();
				if(parent == null)
					return Integer.MAX_VALUE;
				planet = parent;
			}
			return Math.max(planet.orbitalDist, 1);
		}

		while(destinationProperties.getParentProperties() != null
				&& destinationProperties.isMoon())
			destinationProperties = destinationProperties
					.getParentProperties();

		if((destinationProperties.isMoon()
				&& destinationProperties.getParentPlanet()
						== sourceProperties.getId())
				|| (sourceProperties.isMoon()
						&& sourceProperties.getParentPlanet()
								== destinationProperties.getId()))
			return 1;

		while(sourceProperties.isMoon()) {
			DimensionProperties parent = sourceProperties.getParentProperties();
			if(parent == null)
				return Integer.MAX_VALUE;
			sourceProperties = parent;
		}

		double x1 = sourceProperties.orbitalDist
				* MathHelper.cos((float)sourceProperties.orbitTheta);
		double y1 = sourceProperties.orbitalDist
				* MathHelper.sin((float)sourceProperties.orbitTheta);
		double x2 = destinationProperties.orbitalDist
				* MathHelper.cos((float)destinationProperties.orbitTheta);
		double y2 = destinationProperties.orbitalDist
				* MathHelper.sin((float)destinationProperties.orbitTheta);
		double cost = Math.sqrt(Math.pow(x1 - x2, 2)
				+ Math.pow(y1 - y2, 2));
		if(Double.isNaN(cost) || Double.isInfinite(cost))
			return Integer.MAX_VALUE;
		return Math.max((int)Math.min(Integer.MAX_VALUE, cost), 1);
	}

	public static final class Status {
		private final SpaceObject station;
		private final StationTarget destination;
		private final int travelCost;
		private final boolean canWarp;
		private final String reason;
		private final String message;

		private Status(SpaceObject station, StationTarget destination,
				int travelCost, boolean canWarp, String reason,
				String message) {
			this.station = station;
			this.destination = destination;
			this.travelCost = travelCost;
			this.canWarp = canWarp;
			this.reason = reason;
			this.message = message;
		}

		private static Status ready(SpaceObject station,
				StationTarget destination, int travelCost) {
			return new Status(station, destination, travelCost, true,
					"ready", "Station is ready to warp.");
		}

		private static Status error(String code, String message,
				SpaceObject station) {
			return error(code, message, station, null,
					Integer.MAX_VALUE);
		}

		private static Status error(String code, String message,
				SpaceObject station, StationTarget destination,
				int travelCost) {
			return new Status(station, destination, travelCost, false,
					code, message);
		}

		public SpaceObject getStation() {
			return station;
		}

		public StationTarget getDestination() {
			return destination;
		}

		public int getTravelCost() {
			return travelCost;
		}

		public boolean canWarp() {
			return canWarp;
		}

		public String getReason() {
			return reason;
		}

		public String getMessage() {
			return message;
		}
	}

	public static final class Result {
		private final boolean success;
		private final int destinationId;
		private final long remainingTicks;
		private final String errorCode;
		private final String errorMessage;

		private Result(boolean success, int destinationId,
				long remainingTicks, String errorCode, String errorMessage) {
			this.success = success;
			this.destinationId = destinationId;
			this.remainingTicks = remainingTicks;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
		}

		private static Result success(int destinationId,
				long remainingTicks) {
			return new Result(true, destinationId, remainingTicks, null,
					null);
		}

		private static Result error(String code, String message) {
			return new Result(false, 0, 0L, code, message);
		}

		public boolean isSuccess() {
			return success;
		}

		public int getDestinationId() {
			return destinationId;
		}

		public long getRemainingTicks() {
			return remainingTicks;
		}

		public String getErrorCode() {
			return errorCode;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}
}
