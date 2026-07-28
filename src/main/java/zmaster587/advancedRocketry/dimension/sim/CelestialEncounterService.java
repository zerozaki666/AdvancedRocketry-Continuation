package zmaster587.advancedRocketry.dimension.sim;

import java.util.List;

/**
 * Selects the earliest server-authoritative encounter along a rocket's
 * relative swept segment. Both the rocket and celestial body may move during
 * the tick.
 */
public final class CelestialEncounterService {

	private static final double EPSILON = 1.0E-9D;

	private CelestialEncounterService() {
	}

	public enum EncounterType {
		NONE,
		LAND,
		BLACK_HOLE_CAPTURE
	}

	public static final class Encounter {
		public static final Encounter NONE =
				new Encounter(EncounterType.NONE, null, Double.POSITIVE_INFINITY);

		private final EncounterType type;
		private final SimBodySnapshot body;
		private final double timeOfImpact;

		private Encounter(EncounterType type, SimBodySnapshot body,
				double timeOfImpact) {
			this.type = type;
			this.body = body;
			this.timeOfImpact = timeOfImpact;
		}

		public EncounterType getType() { return type; }
		public SimBodySnapshot getBody() { return body; }
		public double getTimeOfImpact() { return timeOfImpact; }
	}

	public static Encounter findEarliest(List<SimBodySnapshot> bodies,
			double rocketPreviousX, double rocketPreviousY,
			double rocketPreviousZ, double rocketX, double rocketY,
			double rocketZ, boolean captureEnabled) {
		if(bodies == null || !finite(rocketPreviousX)
				|| !finite(rocketPreviousY) || !finite(rocketPreviousZ)
				|| !finite(rocketX) || !finite(rocketY) || !finite(rocketZ))
			return Encounter.NONE;

		Encounter best = Encounter.NONE;
		for(SimBodySnapshot body : bodies) {
			if(body == null)
				continue;

			EncounterType type;
			double radius;
			if(body.getBodyType() == SimBodyType.BLACK_HOLE) {
				if(!captureEnabled)
					continue;
				type = EncounterType.BLACK_HOLE_CAPTURE;
				radius = body.getCaptureRadius();
			}
			else if(body.isLandable()) {
				type = EncounterType.LAND;
				radius = Math.max(2D, body.getSize()*4D);
			}
			else {
				continue;
			}

			double time = sweptSphereTime(
					rocketPreviousX-body.getPreviousX(),
					rocketPreviousY-body.getPreviousY(),
					rocketPreviousZ-body.getPreviousZ(),
					rocketX-body.getX(), rocketY-body.getY(),
					rocketZ-body.getZ(), radius);
			if(!finite(time))
				continue;

			boolean tied = Math.abs(time-best.timeOfImpact) <= EPSILON;
			boolean captureWinsTie = tied
					&& type == EncounterType.BLACK_HOLE_CAPTURE
					&& best.type != EncounterType.BLACK_HOLE_CAPTURE;
			boolean stableIdWinsTie = tied && type == best.type
					&& best.body != null
					&& safeId(body).compareTo(safeId(best.body)) < 0;
			if(time < best.timeOfImpact-EPSILON
					|| captureWinsTie || stableIdWinsTie) {
				best = new Encounter(type, body, time);
			}
		}
		return best;
	}

	static double sweptSphereTime(double q0x, double q0y, double q0z,
			double q1x, double q1y, double q1z, double radius) {
		if(!finite(radius) || radius <= 0D || !finite(q0x) || !finite(q0y)
				|| !finite(q0z) || !finite(q1x) || !finite(q1y)
				|| !finite(q1z))
			return Double.NaN;

		double radiusSquared = radius*radius;
		double c = q0x*q0x + q0y*q0y + q0z*q0z-radiusSquared;
		if(c <= 0D)
			return 0D;

		double dx = q1x-q0x;
		double dy = q1y-q0y;
		double dz = q1z-q0z;
		double a = dx*dx + dy*dy + dz*dz;
		if(a <= EPSILON)
			return Double.NaN;

		double b = 2D*(q0x*dx + q0y*dy + q0z*dz);
		double discriminant = b*b-4D*a*c;
		if(discriminant < 0D || !finite(discriminant))
			return Double.NaN;

		double root = Math.sqrt(Math.max(0D, discriminant));
		double first = (-b-root)/(2D*a);
		double second = (-b+root)/(2D*a);
		if(first >= 0D && first <= 1D)
			return first;
		if(second >= 0D && second <= 1D)
			return second;
		return Double.NaN;
	}

	private static boolean finite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static String safeId(SimBodySnapshot body) {
		return body.getId() == null ? "" : body.getId();
	}
}
