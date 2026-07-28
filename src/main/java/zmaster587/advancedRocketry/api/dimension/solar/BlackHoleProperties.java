package zmaster587.advancedRocketry.api.dimension.solar;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import zmaster587.advancedRocketry.AdvancedRocketry;

/**
 * Versioned, gameplay-scale properties for an astronomical black hole.
 *
 * <p>The radii in this object intentionally use two different unit systems:
 * disk radii are expressed in {@code GM/c^2}, while capture/influence/warning
 * radii are free-space blocks.  {@link #visualScale} affects rendering only.</p>
 */
public final class BlackHoleProperties {

	public static final int SCHEMA_VERSION = 1;
	public static final double MAX_SPIN = 0.998D;
	private static final double MIN_MASS = 0.01D;
	private static final double DISK_EPSILON = 0.001D;

	private int schemaVersion;
	private double mass;
	private double spin;
	private double accretionRate;
	private double spinAxisInclinationDeg;
	private double spinAxisYawDeg;
	private boolean hasDiskInnerRadiusOverride;
	private double diskInnerRadiusOverM;
	private double diskOuterRadiusOverM;
	private double visualScale;
	private double captureRadius;
	private double influenceRadius;
	private double warningRadius;

	private final transient Set<String> emittedWarnings = new HashSet<String>();
	private transient String warningContext;

	private BlackHoleProperties(float legacySize, int stellarId, String stellarName) {
		double safeSize = isFinite(legacySize) && legacySize > 0D
				? legacySize : 1D;
		schemaVersion = SCHEMA_VERSION;
		mass = Math.max(safeSize * safeSize, MIN_MASS);
		spin = 0D;
		accretionRate = 0.25D;
		spinAxisInclinationDeg = 0D;
		spinAxisYawDeg = 0D;
		hasDiskInnerRadiusOverride = false;
		diskInnerRadiusOverM = 6D;
		diskOuterRadiusOverM = 20D;
		visualScale = 1D;
		captureRadius = Math.max(2D, safeSize * 4D);
		influenceRadius = captureRadius * 16D;
		warningRadius = influenceRadius * 1.25D;
		warningContext = describe(stellarId, stellarName);
	}

	public static BlackHoleProperties createDefaults(float legacySize,
			int stellarId, String stellarName) {
		return new BlackHoleProperties(legacySize, stellarId, stellarName);
	}

	public BlackHoleProperties copy() {
		BlackHoleProperties copy = new BlackHoleProperties(1F, 0, "");
		copy.schemaVersion = schemaVersion;
		copy.mass = mass;
		copy.spin = spin;
		copy.accretionRate = accretionRate;
		copy.spinAxisInclinationDeg = spinAxisInclinationDeg;
		copy.spinAxisYawDeg = spinAxisYawDeg;
		copy.hasDiskInnerRadiusOverride = hasDiskInnerRadiusOverride;
		copy.diskInnerRadiusOverM = diskInnerRadiusOverM;
		copy.diskOuterRadiusOverM = diskOuterRadiusOverM;
		copy.visualScale = visualScale;
		copy.captureRadius = captureRadius;
		copy.influenceRadius = influenceRadius;
		copy.warningRadius = warningRadius;
		copy.warningContext = warningContext;
		return copy;
	}

	public int getSchemaVersion() {
		return schemaVersion;
	}

	public double getMass() {
		return mass;
	}

	public void setMass(double value) {
		requireFinitePositive("mass", value);
		mass = Math.max(value, MIN_MASS);
	}

	public double getSpin() {
		return spin;
	}

	public void setSpin(double value) {
		requireFinite("spin", value);
		spin = clamp(value, 0D, MAX_SPIN);
		normalizeDiskRadii();
	}

	public double getAccretionRate() {
		return accretionRate;
	}

	public void setAccretionRate(double value) {
		requireFinite("accretionRate", value);
		accretionRate = clamp(value, 0D, 1D);
	}

	public double getSpinAxisInclinationDeg() {
		return spinAxisInclinationDeg;
	}

	public void setSpinAxisInclinationDeg(double value) {
		requireFinite("spinAxisInclinationDeg", value);
		spinAxisInclinationDeg = clamp(value, 0D, 180D);
	}

	public double getSpinAxisYawDeg() {
		return spinAxisYawDeg;
	}

	public void setSpinAxisYawDeg(double value) {
		requireFinite("spinAxisYawDeg", value);
		spinAxisYawDeg = value % 360D;
		if(spinAxisYawDeg < 0D)
			spinAxisYawDeg += 360D;
	}

	public boolean hasDiskInnerRadiusOverride() {
		return hasDiskInnerRadiusOverride;
	}

	public double getDiskInnerRadiusOverM() {
		return diskInnerRadiusOverM;
	}

	public void setDiskInnerRadiusOverM(double value) {
		requireFinitePositive("diskInnerRadiusOverM", value);
		hasDiskInnerRadiusOverride = true;
		diskInnerRadiusOverM = value;
		normalizeDiskRadii();
	}

	public void clearDiskInnerRadiusOverride() {
		hasDiskInnerRadiusOverride = false;
		normalizeDiskRadii();
	}

	public double getEffectiveDiskInnerRadiusOverM() {
		double horizon = 1D + Math.sqrt(Math.max(0D, 1D - spin * spin));
		double configured = hasDiskInnerRadiusOverride
				? diskInnerRadiusOverM : calculateProgradeIsco(spin);
		return Math.max(configured, 1.05D * horizon);
	}

	public double getDiskOuterRadiusOverM() {
		return diskOuterRadiusOverM;
	}

	public double getEffectiveDiskOuterRadiusOverM() {
		return Math.max(diskOuterRadiusOverM,
				getEffectiveDiskInnerRadiusOverM() + DISK_EPSILON);
	}

	public void setDiskOuterRadiusOverM(double value) {
		requireFinitePositive("diskOuterRadiusOverM", value);
		diskOuterRadiusOverM = Math.max(value,
				getEffectiveDiskInnerRadiusOverM() + DISK_EPSILON);
	}

	public double getVisualScale() {
		return visualScale;
	}

	public void setVisualScale(double value) {
		requireFinitePositive("visualScale", value);
		visualScale = value;
	}

	public double getCaptureRadius() {
		return captureRadius;
	}

	public void setCaptureRadius(double value) {
		requireFinitePositive("captureRadius", value);
		captureRadius = value;
		normalizeGameplayRadii();
	}

	public double getInfluenceRadius() {
		return influenceRadius;
	}

	public void setInfluenceRadius(double value) {
		requireFinitePositive("influenceRadius", value);
		influenceRadius = Math.max(value, captureRadius);
		warningRadius = Math.max(warningRadius, influenceRadius);
	}

	public double getWarningRadius() {
		return warningRadius;
	}

	public void setWarningRadius(double value) {
		requireFinitePositive("warningRadius", value);
		warningRadius = Math.max(value, influenceRadius);
	}

	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setInteger("schemaVersion", SCHEMA_VERSION);
		nbt.setDouble("mass", mass);
		nbt.setDouble("spin", spin);
		nbt.setDouble("accretionRate", accretionRate);
		nbt.setDouble("spinAxisInclinationDeg", spinAxisInclinationDeg);
		nbt.setDouble("spinAxisYawDeg", spinAxisYawDeg);
		if(hasDiskInnerRadiusOverride)
			nbt.setDouble("diskInnerRadiusOverM", diskInnerRadiusOverM);
		nbt.setDouble("diskOuterRadiusOverM", diskOuterRadiusOverM);
		nbt.setDouble("visualScale", visualScale);
		nbt.setDouble("captureRadius", captureRadius);
		nbt.setDouble("influenceRadius", influenceRadius);
		nbt.setDouble("warningRadius", warningRadius);
	}

	public void readFromNBT(NBTTagCompound nbt, int stellarId,
			String stellarName) {
		warningContext = describe(stellarId, stellarName);
		if(nbt.hasKey("schemaVersion", NBT.TAG_ANY_NUMERIC))
			schemaVersion = Math.max(1, nbt.getInteger("schemaVersion"));

		double decodedMass = readPositive(nbt, "mass", mass);
		if(decodedMass < MIN_MASS)
			warnOnce("mass-range", "mass below " + MIN_MASS
					+ "; clamping to the minimum");
		mass = Math.max(MIN_MASS, decodedMass);
		spin = readClamped(nbt, "spin", spin, 0D, MAX_SPIN);
		accretionRate = readClamped(nbt, "accretionRate",
				accretionRate, 0D, 1D);
		spinAxisInclinationDeg = readClamped(nbt,
				"spinAxisInclinationDeg", spinAxisInclinationDeg,
				0D, 180D);
		double yaw = readFinite(nbt, "spinAxisYawDeg", spinAxisYawDeg);
		if(yaw < 0D || yaw >= 360D)
			warnOnce("spinAxisYawDeg-range",
					"out-of-range spin axis yaw; wrapping into [0,360)");
		spinAxisYawDeg = yaw % 360D;
		if(spinAxisYawDeg < 0D)
			spinAxisYawDeg += 360D;

		hasDiskInnerRadiusOverride = false;
		if(nbt.hasKey("diskInnerRadiusOverM")) {
			if(nbt.hasKey("diskInnerRadiusOverM", NBT.TAG_ANY_NUMERIC)) {
				double inner = nbt.getDouble("diskInnerRadiusOverM");
				if(isFinite(inner) && inner > 0D) {
					hasDiskInnerRadiusOverride = true;
					diskInnerRadiusOverM = inner;
				}
				else
					warnOnce("diskInnerRadiusOverM",
							"invalid disk inner radius; using automatic ISCO");
			}
			else
				warnOnce("diskInnerRadiusOverM",
						"non-numeric disk inner radius; using automatic ISCO");
		}

		diskOuterRadiusOverM = readPositive(nbt, "diskOuterRadiusOverM",
				diskOuterRadiusOverM);
		visualScale = readPositive(nbt, "visualScale", visualScale);
		captureRadius = readPositive(nbt, "captureRadius", captureRadius);
		double defaultInfluence = safeMultiply(captureRadius, 16D);
		influenceRadius = readPositive(nbt, "influenceRadius",
				defaultInfluence);
		double defaultWarning = safeMultiply(influenceRadius, 1.25D);
		warningRadius = readPositive(nbt, "warningRadius",
				defaultWarning);
		normalizeDiskRadii();
		normalizeGameplayRadii();
	}

	private double readFinite(NBTTagCompound nbt, String key,
			double fallback) {
		if(!nbt.hasKey(key))
			return fallback;
		if(!nbt.hasKey(key, NBT.TAG_ANY_NUMERIC)) {
			warnOnce(key, "non-numeric value; using fallback " + fallback);
			return fallback;
		}
		double value = nbt.getDouble(key);
		if(!isFinite(value)) {
			warnOnce(key, "non-finite value; using fallback " + fallback);
			return fallback;
		}
		return value;
	}

	private double readPositive(NBTTagCompound nbt, String key,
			double fallback) {
		double value = readFinite(nbt, key, fallback);
		if(value <= 0D) {
			warnOnce(key, "non-positive value; using fallback " + fallback);
			return fallback;
		}
		return value;
	}

	private double readClamped(NBTTagCompound nbt, String key,
			double fallback, double minimum, double maximum) {
		double value = readFinite(nbt, key, fallback);
		if(value < minimum || value > maximum)
			warnOnce(key + "-range", "out-of-range value; clamping to ["
					+ minimum + "," + maximum + "]");
		return clamp(value, minimum, maximum);
	}

	private void normalizeDiskRadii() {
		double minimumOuter = getEffectiveDiskInnerRadiusOverM()
				+ DISK_EPSILON;
		if(!isFinite(diskOuterRadiusOverM)
				|| diskOuterRadiusOverM < minimumOuter)
			diskOuterRadiusOverM = minimumOuter;
	}

	private void normalizeGameplayRadii() {
		influenceRadius = Math.max(captureRadius, influenceRadius);
		warningRadius = Math.max(influenceRadius, warningRadius);
	}

	private void warnOnce(String key, String message) {
		if(emittedWarnings.add(key))
			AdvancedRocketry.logger.warn("Black-hole profile for "
					+ warningContext + " has " + message);
	}

	private static double calculateProgradeIsco(double dimensionlessSpin) {
		double a = clamp(dimensionlessSpin, 0D, MAX_SPIN);
		if(a == 0D)
			return 6D;
		double oneMinusASquared = Math.max(0D, 1D - a * a);
		double z1 = 1D + Math.cbrt(oneMinusASquared)
				* (Math.cbrt(1D + a) + Math.cbrt(1D - a));
		double z2 = Math.sqrt(Math.max(0D, 3D * a * a + z1 * z1));
		double radicand = Math.max(0D,
				(3D - z1) * (3D + z1 + 2D * z2));
		return 3D + z2 - Math.sqrt(radicand);
	}

	private static void requireFinite(String field, double value) {
		if(!isFinite(value))
			throw new IllegalArgumentException(field + " must be finite");
	}

	private static void requireFinitePositive(String field, double value) {
		requireFinite(field, value);
		if(value <= 0D)
			throw new IllegalArgumentException(field + " must be > 0");
	}

	private static boolean isFinite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static double clamp(double value, double minimum,
			double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double safeMultiply(double value, double multiplier) {
		if(value > Double.MAX_VALUE / multiplier)
			return Double.MAX_VALUE;
		return value * multiplier;
	}

	private static String describe(int stellarId, String stellarName) {
		String name = stellarName == null || stellarName.isEmpty()
				? "<unnamed>" : stellarName;
		return "'" + name + "' (id " + stellarId + ")";
	}
}
