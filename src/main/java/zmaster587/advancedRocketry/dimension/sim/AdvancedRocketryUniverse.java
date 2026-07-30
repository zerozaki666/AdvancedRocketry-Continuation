package zmaster587.advancedRocketry.dimension.sim;

import java.util.ArrayList;
import java.util.List;

import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.dimension.solar.BlackHoleProperties;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;

/**
 * Converts AdvancedRocketry's persisted galaxy into simulation inputs.
 */
public final class AdvancedRocketryUniverse {

	private static final String STAR_PREFIX = "star:";
	private static final String PLANET_PREFIX = "planet:";

	private AdvancedRocketryUniverse() {
	}

	public static void refresh() {
		List<ISimStellar> bodies = new ArrayList<ISimStellar>();

		for(StellarBody star : DimensionManager.getInstance().getStars())
			bodies.add(new SimStar(star));

		for(Integer dimensionId : DimensionManager.getInstance().getRegisteredDimensions()) {
			if(dimensionId == null || dimensionId == Configuration.spaceDimId
					|| dimensionId == Configuration.freeSpaceDimId)
				continue;

			DimensionProperties properties =
					DimensionManager.getInstance().getDimensionProperties(dimensionId);
			if(properties != null)
				bodies.add(new SimPlanet(properties));
		}

		SimUniverse.getInstance().init(bodies);
	}

	public static void stop() {
		SimUniverse.getInstance().stop();
	}

	public static void tick(long worldTime) {
		SimUniverse.getInstance().tick(worldTime);
	}

	public static String starId(int id) {
		return STAR_PREFIX + id;
	}

	public static String planetId(int id) {
		return PLANET_PREFIX + id;
	}

	private static final class SimStar
			implements ITypedSimStellar, ISimHazard {
		private final StellarBody star;

		private SimStar(StellarBody star) {
			this.star = star;
		}

		@Override public String getID() { return starId(star.getId()); }
		@Override public String getName() { return star.getName(); }
		@Override public double getMass() {
			return Math.max(star.getSimulationMass(), 0.01D);
		}
		@Override public double getSize() { return Math.max(star.getSize(), 0.1F); }
		@Override public double getOrbitRadius() { return 0; }
		@Override public double getInitialAngle() { return 0; }
		@Override public double getInclination() { return 0; }
		@Override public double getStaticX() { return star.getPosX(); }
		@Override public double getStaticY() { return star.getPosY(); }
		@Override public double getStaticZ() { return star.getPosZ(); }
		@Override public String getParentID() { return null; }
		@Override public boolean isStar() { return true; }
		@Override public int getDimensionId() { return Integer.MIN_VALUE; }
		@Override public boolean isLandable() { return false; }
		@Override public SimBodyType getBodyType() {
			return star.isBlackHole()
					? SimBodyType.BLACK_HOLE : SimBodyType.STAR;
		}
		@Override public double getWarningRadius() {
			BlackHoleProperties properties = star.getBlackHoleProperties();
			return properties == null ? 0D : properties.getWarningRadius();
		}
		@Override public double getInfluenceRadius() {
			BlackHoleProperties properties = star.getBlackHoleProperties();
			return properties == null ? 0D : properties.getInfluenceRadius();
		}
		@Override public double getCaptureRadius() {
			BlackHoleProperties properties = star.getBlackHoleProperties();
			return properties == null ? 0D : properties.getCaptureRadius();
		}
	}

	private static final class SimPlanet implements ITypedSimStellar {
		private final DimensionProperties properties;

		private SimPlanet(DimensionProperties properties) {
			this.properties = properties;
		}

		@Override public String getID() { return planetId(properties.getId()); }
		@Override public String getName() { return properties.getName(); }
		@Override public double getMass() { return Math.max(properties.getMass(), 0.01F); }
		@Override public double getSize() {
			return properties.isGasGiant()
					? 2.0D
					: Math.max(0.5D, Math.sqrt(getMass()));
		}
		@Override public double getOrbitRadius() {
			return properties.getParentOrbitalDistance();
		}
		@Override public double getInitialAngle() {
			return properties.getBaseOrbitTheta();
		}
		@Override public double getInclination() {
			return Math.toRadians(properties.getOrbitPhi());
		}
		@Override public double getStaticX() { return 0; }
		@Override public double getStaticY() { return 0; }
		@Override public double getStaticZ() { return 0; }
		@Override public String getParentID() {
			return properties.isMoon()
					? planetId(properties.getParentPlanet())
					: starId(properties.getStarId());
		}
		@Override public boolean isStar() { return false; }
		@Override public int getDimensionId() { return properties.getId(); }
		@Override public boolean isLandable() { return !properties.isGasGiant(); }
		@Override public SimBodyType getBodyType() {
			return properties.isGasGiant()
					? SimBodyType.GAS_GIANT : SimBodyType.PLANET;
		}
	}
}
