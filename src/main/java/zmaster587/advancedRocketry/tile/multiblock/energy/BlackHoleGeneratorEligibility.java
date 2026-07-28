package zmaster587.advancedRocketry.tile.multiblock.energy;

import net.minecraft.world.World;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationTarget;
import zmaster587.advancedRocketry.stations.StationTargetResolver;

/**
 * Server-authoritative environment check for a black hole generator.
 */
public final class BlackHoleGeneratorEligibility {

	public static final int NO_TARGET = Integer.MAX_VALUE;

	private BlackHoleGeneratorEligibility() {
	}

	/**
	 * Evaluates the station at the controller coordinates. Call this from the
	 * logical server only.
	 */
	public static Result evaluate(World world, int x, int z) {
		if(world == null || world.provider == null
				|| world.provider.dimensionId != Configuration.spaceDimId)
			return new Result(BlackHoleGeneratorState.WRONG_DIMENSION, NO_TARGET);

		ISpaceObject station = SpaceObjectManager.getSpaceManager()
				.getSpaceStationFromBlockCoords(x, z);
		if(station == null)
			return new Result(BlackHoleGeneratorState.NO_STATION, NO_TARGET);

		int rawTarget = station.getOrbitingPlanetId();
		StationTarget target = StationTargetResolver.getInstance().resolve(rawTarget);
		if(target.getKind() == StationTarget.Kind.WARP)
			return new Result(BlackHoleGeneratorState.IN_WARP, rawTarget);

		if(target.getKind() != StationTarget.Kind.BLACK_HOLE_STAR)
			return new Result(BlackHoleGeneratorState.NOT_ORBITING_BLACK_HOLE,
					rawTarget);

		StellarBody star = target.getStellarBody();
		if(star == null || !star.isBlackHole())
			return new Result(BlackHoleGeneratorState.NOT_ORBITING_BLACK_HOLE,
					rawTarget);

		return new Result(null, rawTarget);
	}

	public static final class Result {
		private final BlackHoleGeneratorState failureState;
		private final int targetId;

		private Result(BlackHoleGeneratorState failureState, int targetId) {
			this.failureState = failureState;
			this.targetId = targetId;
		}

		public boolean isEligible() {
			return failureState == null;
		}

		public BlackHoleGeneratorState getFailureState() {
			return failureState;
		}

		public int getTargetId() {
			return targetId;
		}
	}
}
