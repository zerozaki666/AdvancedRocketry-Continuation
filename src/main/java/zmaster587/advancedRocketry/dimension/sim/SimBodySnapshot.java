package zmaster587.advancedRocketry.dimension.sim;

/**
 * Immutable cross-tick view of a simulated celestial body.
 */
public final class SimBodySnapshot {
	private final String id;
	private final String name;
	private final SimBodyType bodyType;
	private final int dimensionId;
	private final double previousX;
	private final double previousY;
	private final double previousZ;
	private final double x;
	private final double y;
	private final double z;
	private final double mass;
	private final double size;
	private final boolean landable;
	private final double warningRadius;
	private final double influenceRadius;
	private final double captureRadius;

	public SimBodySnapshot(String id, String name, SimBodyType bodyType,
			int dimensionId, double previousX, double previousY,
			double previousZ, double x, double y, double z, double mass,
			double size, boolean landable, double warningRadius,
			double influenceRadius, double captureRadius) {
		this.id = id;
		this.name = name;
		this.bodyType = bodyType;
		this.dimensionId = dimensionId;
		this.previousX = previousX;
		this.previousY = previousY;
		this.previousZ = previousZ;
		this.x = x;
		this.y = y;
		this.z = z;
		this.mass = mass;
		this.size = size;
		this.landable = landable;
		this.warningRadius = warningRadius;
		this.influenceRadius = influenceRadius;
		this.captureRadius = captureRadius;
	}

	public String getId() { return id; }
	public String getName() { return name; }
	public SimBodyType getBodyType() { return bodyType; }
	public int getDimensionId() { return dimensionId; }
	public double getPreviousX() { return previousX; }
	public double getPreviousY() { return previousY; }
	public double getPreviousZ() { return previousZ; }
	public double getX() { return x; }
	public double getY() { return y; }
	public double getZ() { return z; }
	public double getMass() { return mass; }
	public double getSize() { return size; }
	public boolean isLandable() { return landable; }
	public double getWarningRadius() { return warningRadius; }
	public double getInfluenceRadius() { return influenceRadius; }
	public double getCaptureRadius() { return captureRadius; }
}
