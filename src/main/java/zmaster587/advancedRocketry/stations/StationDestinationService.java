package zmaster587.advancedRocketry.stations;

import java.util.LinkedHashMap;
import java.util.Map;

import zmaster587.advancedRocketry.dimension.DimensionProperties;

/**
 * Canonical validation and mutation path for a space station destination.
 */
public final class StationDestinationService {

	private StationDestinationService() {
	}

	public static Result setDestination(SpaceObject station, int rawId) {
		Result result = validateDestination(station, rawId);
		if(result.isSuccess())
			station.setDestOrbitingBody(rawId);
		return result;
	}

	public static Result validateDestination(SpaceObject station, int rawId) {
		if(station == null)
			return Result.error("not_on_station",
					"Component is not on a valid space station.");
		if(station.getOrbitingPlanetId() == SpaceObjectManager.WARPDIMID)
			return Result.error("in_warp",
					"Station destination cannot be changed during warp.");

		StationTarget target = StationTargetResolver.getInstance()
				.resolve(rawId);
		if(!target.isDestination())
			return Result.error("invalid_destination",
					"Requested id is not a valid station destination.");
		if(rawId == station.getOrbitingPlanetId())
			return Result.error("same_destination",
					"Requested destination is the current orbit target.");
		if(!isKnown(station, target))
			return Result.error("unknown_destination",
					"Station has not discovered the requested destination.");
		return Result.success(target);
	}

	public static boolean isKnown(SpaceObject station, StationTarget target) {
		if(station == null || target == null)
			return false;
		if(target.getKind() == StationTarget.Kind.BLACK_HOLE_STAR)
			return target.getStellarBody() != null
					&& station.isStarKnown(target.getStellarBody());
		if(target.getKind() == StationTarget.Kind.DIMENSION)
			return target.getDimensionProperties() != null
					&& station.isPlanetKnown(target.getDimensionProperties());
		return false;
	}

	public static Map<String, Object> describe(SpaceObject station,
			StationTarget target) {
		Map<String, Object> info = new LinkedHashMap<String, Object>();
		info.put("id", target.getRawId());
		info.put("kind", target.getKind()
				== StationTarget.Kind.BLACK_HOLE_STAR
						? "black_hole" : "dimension");
		DimensionProperties properties = target.getDimensionProperties();
		String name = properties == null ? ""
				: properties.getName();
		if(target.getKind() == StationTarget.Kind.BLACK_HOLE_STAR
				&& target.getStellarBody() != null)
			name = target.getStellarBody().getName();
		info.put("name", name == null ? "" : name);
		info.put("known", isKnown(station, target));
		info.put("current", station != null
				&& target.getRawId() == station.getOrbitingPlanetId());
		info.put("destination", station != null
				&& target.getRawId() == station.getDestOrbitingBody());
		return info;
	}

	public static final class Result {
		private final StationTarget target;
		private final String errorCode;
		private final String errorMessage;

		private Result(StationTarget target, String errorCode,
				String errorMessage) {
			this.target = target;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
		}

		private static Result success(StationTarget target) {
			return new Result(target, null, null);
		}

		private static Result error(String code, String message) {
			return new Result(null, code, message);
		}

		public boolean isSuccess() {
			return target != null;
		}

		public StationTarget getTarget() {
			return target;
		}

		public String getErrorCode() {
			return errorCode;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}
}
