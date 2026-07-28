package zmaster587.advancedRocketry.dimension.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import zmaster587.advancedRocketry.AdvancedRocketry;

/**
 * Deterministic hierarchy used for free-space travel and LoD sky rendering.
 *
 * <p>Positions are calculated from absolute world time rather than accumulated
 * client ticks, so a remote client and the server converge on the same orbit
 * even when their initialization times differ.</p>
 */
public final class SimUniverse {

	private static final SimUniverse INSTANCE = new SimUniverse();
	public static final double GRAVITY_CONSTANT = 0.01D;
	public static final double SPACE_Y_ORIGIN = 1024D;
	public static final double MIN_BODY_Y = 128D;
	private static final double TICK_SCALE = 0.1D;

	private final Map<String, SimBody> bodies = new HashMap<String, SimBody>();
	private final List<SimBody> roots = new ArrayList<SimBody>();

	public static SimUniverse getInstance() {
		return INSTANCE;
	}

	public synchronized void init(List<? extends ISimStellar> configs) {
		bodies.clear();
		roots.clear();

		for(ISimStellar config : configs) {
			if(config == null) {
				AdvancedRocketry.logger.warn(
						"Skipping null simulated-universe configuration");
				continue;
			}
			String id = config.getID();
			if(id == null || id.trim().length() == 0) {
				AdvancedRocketry.logger.warn(
						"Skipping simulated body with an empty ID");
				continue;
			}
			if(bodies.containsKey(id)) {
				AdvancedRocketry.logger.warn(
						"Skipping duplicate simulated body ID " + id);
				continue;
			}
			bodies.put(id, new SimBody(config));
		}

		Set<SimBody> invalid = new HashSet<SimBody>();
		for(SimBody body : bodies.values()) {
			String parentId = normalizedParentId(body.config);
			if(parentId != null && parentId.equals(body.config.getID())) {
				AdvancedRocketry.logger.warn("Skipping simulated body "
						+ body.config.getID() + ": it cannot parent itself");
				invalid.add(body);
			}
			else if(parentId != null && !bodies.containsKey(parentId)) {
				AdvancedRocketry.logger.warn("Skipping simulated body "
						+ body.config.getID() + ": missing parent " + parentId);
				invalid.add(body);
			}
		}

		for(SimBody start : bodies.values()) {
			if(invalid.contains(start))
				continue;
			List<SimBody> path = new ArrayList<SimBody>();
			Map<SimBody, Integer> pathIndexes =
					new HashMap<SimBody, Integer>();
			SimBody current = start;
			while(current != null) {
				if(invalid.contains(current)) {
					if(!path.isEmpty())
						AdvancedRocketry.logger.warn("Skipping simulated body "
								+ start.config.getID()
								+ ": its parent chain is invalid");
					invalid.addAll(path);
					break;
				}

				Integer previousIndex = pathIndexes.get(current);
				if(previousIndex != null) {
					StringBuilder cycle = new StringBuilder();
					for(int i = previousIndex; i < path.size(); i++) {
						if(cycle.length() > 0)
							cycle.append(" -> ");
						cycle.append(path.get(i).config.getID());
					}
					cycle.append(" -> ").append(current.config.getID());
					AdvancedRocketry.logger.warn(
							"Skipping simulated parent cycle: " + cycle);
					invalid.addAll(path);
					break;
				}

				pathIndexes.put(current, path.size());
				path.add(current);
				String parentId = normalizedParentId(current.config);
				current = parentId == null ? null : bodies.get(parentId);
			}
		}

		Iterator<Map.Entry<String, SimBody>> iterator =
				bodies.entrySet().iterator();
		while(iterator.hasNext()) {
			if(invalid.contains(iterator.next().getValue()))
				iterator.remove();
		}

		for(SimBody body : bodies.values()) {
			String parentId = normalizedParentId(body.config);
			SimBody parent = bodies.get(parentId);
			if(parentId == null) {
				roots.add(body);
			}
			else {
				body.parent = parent;
				parent.children.add(body);
				body.calculateOrbitPhysics();
			}
		}
	}

	private static String normalizedParentId(ISimStellar config) {
		String parentId = config.getParentID();
		return parentId == null || parentId.trim().length() == 0
				? null
				: parentId;
	}

	public synchronized void stop() {
		bodies.clear();
		roots.clear();
	}

	public synchronized boolean isInitialized() {
		return !bodies.isEmpty();
	}

	public synchronized void tick(long worldTime) {
		for(SimBody root : roots)
			root.update(worldTime);
	}

	public synchronized SimBody getBody(String id) {
		return bodies.get(id);
	}

	public synchronized List<SimBody> getAllBodies() {
		return new ArrayList<SimBody>(bodies.values());
	}

	public static final class SimBody {
		private final ISimStellar config;
		private final List<SimBody> children = new ArrayList<SimBody>();
		private SimBody parent;
		private double angularVelocity;

		public double x;
		public double y;
		public double z;

		private SimBody(ISimStellar config) {
			this.config = config;
		}

		public ISimStellar getConfig() {
			return config;
		}

		private void calculateOrbitPhysics() {
			double radius = Math.max(config.getOrbitRadius(), 0.1D);
			double parentMass = Math.max(parent.config.getMass(), 0.01D);
			angularVelocity = Math.sqrt(
					(GRAVITY_CONSTANT*parentMass)/(radius*radius*radius));
		}

		private void update(long worldTime) {
			if(parent == null) {
				x = config.getStaticX();
				y = SPACE_Y_ORIGIN + config.getStaticY();
				z = config.getStaticZ();
			}
			else {
				double angle = normalizeAngle(config.getInitialAngle()
						+ angularVelocity*worldTime*TICK_SCALE);
				double radius = config.getOrbitRadius();
				double localX = Math.cos(angle)*radius;
				double localZ = Math.sin(angle)*radius;
				double inclination = config.getInclination();

				x = parent.x + localX;
				y = parent.y - localZ*Math.sin(inclination);
				z = parent.z + localZ*Math.cos(inclination);
			}

			if(Double.isNaN(y) || Double.isInfinite(y) || y < MIN_BODY_Y)
				y = MIN_BODY_Y;

			for(SimBody child : children)
				child.update(worldTime);
		}

		private static double normalizeAngle(double angle) {
			double fullTurn = Math.PI*2D;
			angle %= fullTurn;
			return angle < 0 ? angle + fullTurn : angle;
		}
	}
}
