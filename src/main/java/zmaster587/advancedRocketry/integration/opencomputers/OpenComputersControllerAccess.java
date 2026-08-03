package zmaster587.advancedRocketry.integration.opencomputers;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.world.provider.WorldProviderStation;

/**
 * Resolves the space station owned by a controller without retaining a
 * reference across callback invocations or chunk lifecycle changes.
 */
public final class OpenComputersControllerAccess {

	private OpenComputersControllerAccess() {
	}

	public static Result resolve(TileEntity tile) {
		if(tile == null || tile.getWorldObj() == null || tile.isInvalid())
			return Result.error("invalid_tile",
					"Controller tile is no longer valid.");

		World world = tile.getWorldObj();
		if(world.isRemote)
			return Result.error("not_server",
					"Callback is only available on the server.");
		if(!(world.provider instanceof WorldProviderStation))
			return Result.error("not_on_station",
					"Controller is not on a valid space station.");
		if(!world.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)
				|| world.getTileEntity(tile.xCoord, tile.yCoord,
						tile.zCoord) != tile)
			return Result.error("invalid_tile",
					"Controller tile is no longer valid.");

		ISpaceObject spaceObject = SpaceObjectManager.getSpaceManager()
				.getSpaceStationFromBlockCoords(tile.xCoord, tile.zCoord);
		if(spaceObject == null)
			return Result.error("not_on_station",
					"Controller is not on a valid space station.");
		return Result.success(spaceObject);
	}

	public static Object[] mutatorError(String code, String message) {
		return new Object[] { false, code, message };
	}

	public static Object[] getterError(String code, String message) {
		return new Object[] { null, code, message };
	}

	public static final class Result {
		private final ISpaceObject spaceObject;
		private final String errorCode;
		private final String errorMessage;

		private Result(ISpaceObject spaceObject, String errorCode,
				String errorMessage) {
			this.spaceObject = spaceObject;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
		}

		private static Result success(ISpaceObject spaceObject) {
			return new Result(spaceObject, null, null);
		}

		private static Result error(String code, String message) {
			return new Result(null, code, message);
		}

		public boolean isValid() {
			return spaceObject != null;
		}

		public ISpaceObject getSpaceObject() {
			return spaceObject;
		}

		public Object[] asMutatorError() {
			return mutatorError(errorCode, errorMessage);
		}

		public Object[] asGetterError() {
			return getterError(errorCode, errorMessage);
		}
	}
}
