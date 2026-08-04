package zmaster587.advancedRocketry.tile.station;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Test;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.stations.SpaceObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.stations.StationTargetResolver;

public class WarpCurrentTargetInfoTest {

	private static final int PLANET_ID = 720042;
	private static final int BLACK_HOLE_ID = 42042;

	@AfterClass
	@SuppressWarnings("unchecked")
	public static void cleanUpTargets() throws Exception {
		DimensionManager.getInstance().removeStar(BLACK_HOLE_ID);
		Field dimensionList = DimensionManager.class
				.getDeclaredField("dimensionList");
		dimensionList.setAccessible(true);
		((Map<Integer, DimensionProperties>)dimensionList.get(
				DimensionManager.getInstance())).remove(PLANET_ID);
		StationTargetResolver.getInstance().invalidateCache();
	}

	@Test
	public void describesDimensionTargetByName() {
		DimensionProperties planet = new DimensionProperties(PLANET_ID);
		planet.setName("Test Earth");
		DimensionManager.getInstance().setDimProperties(PLANET_ID, planet);
		SpaceObject station = stationOrbiting(PLANET_ID);

		Map<?, ?> info = successfulInfo(
				TileWarpShipMonitor.describeCurrentTarget(station));
		assertEquals(PLANET_ID, ((Number)info.get("id")).intValue());
		assertEquals("dimension", info.get("kind"));
		assertEquals("Test Earth", info.get("name"));
		assertEquals(Boolean.TRUE, info.get("current"));
	}

	@Test
	public void describesTopLevelBlackHoleTargetByName() {
		StellarBody blackHole = new StellarBody();
		blackHole.setId(BLACK_HOLE_ID);
		blackHole.setName("Test Gargantua");
		blackHole.setBlackHole(true);
		DimensionManager.getInstance().addStar(blackHole);
		int targetId = StationTargetResolver.getInstance()
				.getSelectorId(BLACK_HOLE_ID);

		Map<?, ?> info = successfulInfo(
				TileWarpShipMonitor.describeCurrentTarget(
						stationOrbiting(targetId)));
		assertEquals(targetId, ((Number)info.get("id")).intValue());
		assertEquals("black_hole", info.get("kind"));
		assertEquals("Test Gargantua", info.get("name"));
	}

	@Test
	public void returnsStableSoftErrorDuringWarp() {
		Object[] result = TileWarpShipMonitor.describeCurrentTarget(
				stationOrbiting(SpaceObjectManager.WARPDIMID));
		assertNull(result[0]);
		assertEquals("in_warp", result[1]);
	}

	@Test
	public void rejectsUnknownTargetInsteadOfFallingBackToEarth() {
		Object[] result = TileWarpShipMonitor.describeCurrentTarget(
				stationOrbiting(777777777));
		assertNull(result[0]);
		assertEquals("invalid_target", result[1]);
	}

	private static SpaceObject stationOrbiting(int targetId) {
		SpaceObject station = new SpaceObject();
		station.beginTransition(1L);
		station.setOrbitingBody(targetId);
		return station;
	}

	@SuppressWarnings("unchecked")
	private static Map<?, ?> successfulInfo(Object[] result) {
		assertEquals(1, result.length);
		assertTrue(result[0] instanceof Map);
		return (Map<String, Object>)result[0];
	}
}
