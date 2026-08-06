package zmaster587.advancedRocketry.integration.opencomputers;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.world.provider.WorldProviderStation;

/**
 * Common server-side validity checks for native OpenComputers components.
 * Station-aware callers use {@link #resolveStation(TileEntity)} while blocks
 * that are useful in ordinary dimensions use {@link #resolveTile(TileEntity)}.
 */
public final class OpenComputersComponentAccess {

	private OpenComputersComponentAccess() {
	}

	public static Result resolveTile(TileEntity tile) {
		if(tile == null || tile.getWorldObj() == null || tile.isInvalid())
			return Result.error("invalid_tile",
					"Component tile is no longer valid.");

		World world = tile.getWorldObj();
		if(world.isRemote)
			return Result.error("not_server",
					"Callback is only available on the server.");
		if(!world.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)
				|| world.getTileEntity(tile.xCoord, tile.yCoord,
						tile.zCoord) != tile)
			return Result.error("invalid_tile",
					"Component tile is no longer valid.");

		return Result.success(world, null);
	}

	public static Result resolveStation(TileEntity tile) {
		Result tileResult = resolveTile(tile);
		if(!tileResult.isValid())
			return tileResult;
		if(!(tileResult.getWorld().provider instanceof WorldProviderStation))
			return Result.error("not_on_station",
					"Component is not on a valid space station.");

		ISpaceObject object = SpaceObjectManager.getSpaceManager()
				.getSpaceStationFromBlockCoords(tile.xCoord, tile.zCoord);
		if(object == null)
			return Result.error("not_on_station",
					"Component is not on a valid space station.");
		return Result.success(tileResult.getWorld(), object);
	}

	public static Object[] mutatorError(String code, String message) {
		return new Object[] { false, code, message };
	}

	public static Object[] getterError(String code, String message) {
		return new Object[] { null, code, message };
	}

	public static final class Result {
		private final World world;
		private final ISpaceObject spaceObject;
		private final String errorCode;
		private final String errorMessage;

		private Result(World world, ISpaceObject spaceObject,
				String errorCode, String errorMessage) {
			this.world = world;
			this.spaceObject = spaceObject;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
		}

		private static Result success(World world, ISpaceObject object) {
			return new Result(world, object, null, null);
		}

		private static Result error(String code, String message) {
			return new Result(null, null, code, message);
		}

		public boolean isValid() {
			return world != null;
		}

		public World getWorld() {
			return world;
		}

		public ISpaceObject getSpaceObject() {
			return spaceObject;
		}

		public String getErrorCode() {
			return errorCode;
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public Object[] asMutatorError() {
			return mutatorError(errorCode, errorMessage);
		}

		public Object[] asGetterError() {
			return getterError(errorCode, errorMessage);
		}
	}
}
