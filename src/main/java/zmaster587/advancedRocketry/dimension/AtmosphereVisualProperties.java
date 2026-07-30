package zmaster587.advancedRocketry.dimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import zmaster587.advancedRocketry.AdvancedRocketry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Optional, common-side visual overrides for an atmosphere.
 *
 * <p>Absent values are intentionally different from values equal to a legacy
 * default.  The renderer resolves absent values from the current gameplay
 * properties, so this class must preserve override presence through XML, NBT,
 * packets and copies.</p>
 */
public final class AtmosphereVisualProperties {

	public static final int SCHEMA_VERSION = 1;
	public static final String NBT_KEY = "atmosphereRender";

	public static final double DEFAULT_PLANET_RADIUS_KM = 6360D;
	public static final double DEFAULT_ATMOSPHERE_HEIGHT_KM = 100D;

	public static final double MIN_PLANET_RADIUS_KM = 100D;
	public static final double MAX_PLANET_RADIUS_KM = 200000D;
	public static final double MIN_ATMOSPHERE_HEIGHT_KM = 1D;
	public static final double MAX_ATMOSPHERE_HEIGHT_KM = 10000D;
	public static final double MAX_COLOR_COMPONENT = 4D;
	public static final double MAX_STRENGTH = 16D;
	public static final double MIN_RAYLEIGH_SCALE_HEIGHT_KM = 0.1D;
	public static final double MIN_MIE_SCALE_HEIGHT_KM = 0.05D;
	public static final double MIN_MIE_ANISOTROPY = -0.2D;
	public static final double MAX_MIE_ANISOTROPY = 0.95D;
	public static final double MIN_ABSORPTION_WIDTH_KM = 0.1D;
	public static final double MAX_MULTIPLE_SCATTERING_STRENGTH = 1D;
	public static final double MIN_EXPOSURE = 0.05D;
	public static final double MAX_EXPOSURE = 8D;

	private static final Set<String> EMITTED_WARNINGS =
			Collections.synchronizedSet(new HashSet<String>());

	public enum CloudLayerMode {
		AUTO,
		ENABLED,
		DISABLED;

		public static CloudLayerMode parse(String value) {
			if(value == null)
				return null;
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			}
			catch(IllegalArgumentException ignored) {
				return null;
			}
		}
	}

	private Double planetRadiusKm;
	private Double atmosphereHeightKm;
	private float[] rayleighColor;
	private Double rayleighStrength;
	private Double rayleighScaleHeightKm;
	private float[] mieColor;
	private Double mieStrength;
	private Double mieScaleHeightKm;
	private Double mieAnisotropy;
	private float[] absorptionColor;
	private Double absorptionStrength;
	private Double absorptionCenterKm;
	private Double absorptionWidthKm;
	private Double multipleScatteringStrength;
	private Double sunIntensityMultiplier;
	private Double exposure;
	private CloudLayerMode cloudLayerMode;
	private transient volatile Snapshot cachedSnapshot;

	public AtmosphereVisualProperties() {
	}

	public AtmosphereVisualProperties(AtmosphereVisualProperties source) {
		if(source == null)
			return;
		planetRadiusKm = source.planetRadiusKm;
		atmosphereHeightKm = source.atmosphereHeightKm;
		rayleighColor = copyColor(source.rayleighColor);
		rayleighStrength = source.rayleighStrength;
		rayleighScaleHeightKm = source.rayleighScaleHeightKm;
		mieColor = copyColor(source.mieColor);
		mieStrength = source.mieStrength;
		mieScaleHeightKm = source.mieScaleHeightKm;
		mieAnisotropy = source.mieAnisotropy;
		absorptionColor = copyColor(source.absorptionColor);
		absorptionStrength = source.absorptionStrength;
		absorptionCenterKm = source.absorptionCenterKm;
		absorptionWidthKm = source.absorptionWidthKm;
		multipleScatteringStrength = source.multipleScatteringStrength;
		sunIntensityMultiplier = source.sunIntensityMultiplier;
		exposure = source.exposure;
		cloudLayerMode = source.cloudLayerMode;
	}

	public AtmosphereVisualProperties copy() {
		return new AtmosphereVisualProperties(this);
	}

	public boolean hasOverrides() {
		return planetRadiusKm != null
				|| atmosphereHeightKm != null
				|| rayleighColor != null
				|| rayleighStrength != null
				|| rayleighScaleHeightKm != null
				|| mieColor != null
				|| mieStrength != null
				|| mieScaleHeightKm != null
				|| mieAnisotropy != null
				|| absorptionColor != null
				|| absorptionStrength != null
				|| absorptionCenterKm != null
				|| absorptionWidthKm != null
				|| multipleScatteringStrength != null
				|| sunIntensityMultiplier != null
				|| exposure != null
				|| cloudLayerMode != null;
	}

	public boolean hasPlanetRadiusKm() {
		return planetRadiusKm != null;
	}

	public double getPlanetRadiusKm() {
		return requireValue("planetRadiusKm", planetRadiusKm);
	}

	public void setPlanetRadiusKm(double value) {
		double validated = requireRange(
				"planetRadiusKm", value,
				MIN_PLANET_RADIUS_KM, MAX_PLANET_RADIUS_KM);
		double maximumHeight = maximumAtmosphereHeight(validated);
		if(atmosphereHeightKm != null
				&& atmosphereHeightKm > maximumHeight)
			throw new IllegalArgumentException(
					"planetRadiusKm would make atmosphereHeightKm "
							+ "exceed half the planet radius");
		if(atmosphereHeightKm != null)
			requireDependentFieldsWithin(atmosphereHeightKm);
		planetRadiusKm = validated;
		markDirty();
	}

	public void clearPlanetRadiusKm() {
		if(atmosphereHeightKm != null
				&& atmosphereHeightKm > maximumAtmosphereHeight(
						DEFAULT_PLANET_RADIUS_KM))
			throw new IllegalStateException(
					"Clearing planetRadiusKm would make the explicit "
							+ "atmosphereHeightKm exceed half the "
							+ "default planet radius");
		planetRadiusKm = null;
		markDirty();
	}

	public boolean hasAtmosphereHeightKm() {
		return atmosphereHeightKm != null;
	}

	public double getAtmosphereHeightKm() {
		return requireValue("atmosphereHeightKm", atmosphereHeightKm);
	}

	public void setAtmosphereHeightKm(double value) {
		double validated = requireRange(
				"atmosphereHeightKm", value,
				MIN_ATMOSPHERE_HEIGHT_KM, MAX_ATMOSPHERE_HEIGHT_KM);
		double maximumHeight = maximumAtmosphereHeight(
				planetRadiusKm == null
						? DEFAULT_PLANET_RADIUS_KM : planetRadiusKm);
		if(validated > maximumHeight)
			throw new IllegalArgumentException(
					"atmosphereHeightKm must not exceed "
							+ "min(10000, planetRadiusKm * 0.5)");
		requireDependentFieldsWithin(validated);
		atmosphereHeightKm = validated;
		markDirty();
	}

	public void clearAtmosphereHeightKm() {
		atmosphereHeightKm = null;
		markDirty();
	}

	public boolean hasRayleighColor() {
		return rayleighColor != null;
	}

	public float[] getRayleighColor() {
		return requireColor("rayleighColor", rayleighColor);
	}

	public float getRayleighColorComponent(int index) {
		return requireColorComponent("rayleighColor", rayleighColor, index);
	}

	public void setRayleighColor(float[] value) {
		rayleighColor = validateColor("rayleighColor", value);
		markDirty();
	}

	public void setRayleighColor(float red, float green, float blue) {
		setRayleighColor(new float[] { red, green, blue });
	}

	public void clearRayleighColor() {
		rayleighColor = null;
		markDirty();
	}

	public boolean hasRayleighStrength() {
		return rayleighStrength != null;
	}

	public double getRayleighStrength() {
		return requireValue("rayleighStrength", rayleighStrength);
	}

	public void setRayleighStrength(double value) {
		rayleighStrength = requireRange(
				"rayleighStrength", value, 0D, MAX_STRENGTH);
		markDirty();
	}

	public void clearRayleighStrength() {
		rayleighStrength = null;
		markDirty();
	}

	public boolean hasRayleighScaleHeightKm() {
		return rayleighScaleHeightKm != null;
	}

	public double getRayleighScaleHeightKm() {
		return requireValue("rayleighScaleHeightKm",
				rayleighScaleHeightKm);
	}

	public void setRayleighScaleHeightKm(double value) {
		double validated = requireRange(
				"rayleighScaleHeightKm", value,
				MIN_RAYLEIGH_SCALE_HEIGHT_KM,
				MAX_ATMOSPHERE_HEIGHT_KM);
		requireWithinExplicitAtmosphere(
				"rayleighScaleHeightKm", validated);
		rayleighScaleHeightKm = validated;
		markDirty();
	}

	public void clearRayleighScaleHeightKm() {
		rayleighScaleHeightKm = null;
		markDirty();
	}

	public boolean hasMieColor() {
		return mieColor != null;
	}

	public float[] getMieColor() {
		return requireColor("mieColor", mieColor);
	}

	public float getMieColorComponent(int index) {
		return requireColorComponent("mieColor", mieColor, index);
	}

	public void setMieColor(float[] value) {
		mieColor = validateColor("mieColor", value);
		markDirty();
	}

	public void setMieColor(float red, float green, float blue) {
		setMieColor(new float[] { red, green, blue });
	}

	public void clearMieColor() {
		mieColor = null;
		markDirty();
	}

	public boolean hasMieStrength() {
		return mieStrength != null;
	}

	public double getMieStrength() {
		return requireValue("mieStrength", mieStrength);
	}

	public void setMieStrength(double value) {
		mieStrength = requireRange(
				"mieStrength", value, 0D, MAX_STRENGTH);
		markDirty();
	}

	public void clearMieStrength() {
		mieStrength = null;
		markDirty();
	}

	public boolean hasMieScaleHeightKm() {
		return mieScaleHeightKm != null;
	}

	public double getMieScaleHeightKm() {
		return requireValue("mieScaleHeightKm", mieScaleHeightKm);
	}

	public void setMieScaleHeightKm(double value) {
		double validated = requireRange(
				"mieScaleHeightKm", value,
				MIN_MIE_SCALE_HEIGHT_KM, MAX_ATMOSPHERE_HEIGHT_KM);
		requireWithinExplicitAtmosphere(
				"mieScaleHeightKm", validated);
		mieScaleHeightKm = validated;
		markDirty();
	}

	public void clearMieScaleHeightKm() {
		mieScaleHeightKm = null;
		markDirty();
	}

	public boolean hasMieAnisotropy() {
		return mieAnisotropy != null;
	}

	public double getMieAnisotropy() {
		return requireValue("mieAnisotropy", mieAnisotropy);
	}

	public void setMieAnisotropy(double value) {
		mieAnisotropy = requireRange(
				"mieAnisotropy", value,
				MIN_MIE_ANISOTROPY, MAX_MIE_ANISOTROPY);
		markDirty();
	}

	public void clearMieAnisotropy() {
		mieAnisotropy = null;
		markDirty();
	}

	public boolean hasAbsorptionColor() {
		return absorptionColor != null;
	}

	public float[] getAbsorptionColor() {
		return requireColor("absorptionColor", absorptionColor);
	}

	public float getAbsorptionColorComponent(int index) {
		return requireColorComponent(
				"absorptionColor", absorptionColor, index);
	}

	public void setAbsorptionColor(float[] value) {
		absorptionColor = validateColor("absorptionColor", value);
		markDirty();
	}

	public void setAbsorptionColor(float red, float green, float blue) {
		setAbsorptionColor(new float[] { red, green, blue });
	}

	public void clearAbsorptionColor() {
		absorptionColor = null;
		markDirty();
	}

	public boolean hasAbsorptionStrength() {
		return absorptionStrength != null;
	}

	public double getAbsorptionStrength() {
		return requireValue("absorptionStrength", absorptionStrength);
	}

	public void setAbsorptionStrength(double value) {
		absorptionStrength = requireRange(
				"absorptionStrength", value, 0D, MAX_STRENGTH);
		markDirty();
	}

	public void clearAbsorptionStrength() {
		absorptionStrength = null;
		markDirty();
	}

	public boolean hasAbsorptionCenterKm() {
		return absorptionCenterKm != null;
	}

	public double getAbsorptionCenterKm() {
		return requireValue("absorptionCenterKm", absorptionCenterKm);
	}

	public void setAbsorptionCenterKm(double value) {
		double validated = requireRange(
				"absorptionCenterKm", value, 0D,
				MAX_ATMOSPHERE_HEIGHT_KM);
		requireWithinExplicitAtmosphere(
				"absorptionCenterKm", validated);
		absorptionCenterKm = validated;
		markDirty();
	}

	public void clearAbsorptionCenterKm() {
		absorptionCenterKm = null;
		markDirty();
	}

	public boolean hasAbsorptionWidthKm() {
		return absorptionWidthKm != null;
	}

	public double getAbsorptionWidthKm() {
		return requireValue("absorptionWidthKm", absorptionWidthKm);
	}

	public void setAbsorptionWidthKm(double value) {
		double validated = requireRange(
				"absorptionWidthKm", value,
				MIN_ABSORPTION_WIDTH_KM, MAX_ATMOSPHERE_HEIGHT_KM);
		requireWithinExplicitAtmosphere(
				"absorptionWidthKm", validated);
		absorptionWidthKm = validated;
		markDirty();
	}

	public void clearAbsorptionWidthKm() {
		absorptionWidthKm = null;
		markDirty();
	}

	public boolean hasMultipleScatteringStrength() {
		return multipleScatteringStrength != null;
	}

	public double getMultipleScatteringStrength() {
		return requireValue(
				"multipleScatteringStrength", multipleScatteringStrength);
	}

	public void setMultipleScatteringStrength(double value) {
		multipleScatteringStrength = requireRange(
				"multipleScatteringStrength", value,
				0D, MAX_MULTIPLE_SCATTERING_STRENGTH);
		markDirty();
	}

	public void clearMultipleScatteringStrength() {
		multipleScatteringStrength = null;
		markDirty();
	}

	public boolean hasSunIntensityMultiplier() {
		return sunIntensityMultiplier != null;
	}

	public double getSunIntensityMultiplier() {
		return requireValue(
				"sunIntensityMultiplier", sunIntensityMultiplier);
	}

	public void setSunIntensityMultiplier(double value) {
		sunIntensityMultiplier = requireRange(
				"sunIntensityMultiplier", value, 0D, MAX_STRENGTH);
		markDirty();
	}

	public void clearSunIntensityMultiplier() {
		sunIntensityMultiplier = null;
		markDirty();
	}

	public boolean hasExposure() {
		return exposure != null;
	}

	public double getExposure() {
		return requireValue("exposure", exposure);
	}

	public void setExposure(double value) {
		exposure = requireRange(
				"exposure", value, MIN_EXPOSURE, MAX_EXPOSURE);
		markDirty();
	}

	public void clearExposure() {
		exposure = null;
		markDirty();
	}

	public boolean hasCloudLayerMode() {
		return cloudLayerMode != null;
	}

	public CloudLayerMode getCloudLayerMode() {
		if(cloudLayerMode == null)
			throw missingValue("cloudLayerMode");
		return cloudLayerMode;
	}

	public void setCloudLayerMode(CloudLayerMode value) {
		if(value == null)
			throw new IllegalArgumentException(
					"cloudLayerMode cannot be null");
		cloudLayerMode = value;
		markDirty();
	}

	public void setCloudLayerMode(String value) {
		CloudLayerMode parsed = CloudLayerMode.parse(value);
		if(parsed == null)
			throw new IllegalArgumentException(
					"cloudLayerMode must be AUTO, ENABLED or DISABLED");
		cloudLayerMode = parsed;
		markDirty();
	}

	public void clearCloudLayerMode() {
		cloudLayerMode = null;
		markDirty();
	}

	/**
	 * Returns a valid copy after applying constraints that depend on another
	 * optional field.  Invalid dependent overrides are removed individually;
	 * legacy-derived defaults are left to the client profile resolver.
	 */
	public AtmosphereVisualProperties sanitizedCopy() {
		return sanitizedCopy(null, false);
	}

	public AtmosphereVisualProperties sanitizedCopy(String context) {
		return sanitizedCopy(context, true);
	}

	private AtmosphereVisualProperties sanitizedCopy(
			String context, boolean warn) {
		AtmosphereVisualProperties copy =
				new AtmosphereVisualProperties(this);
		double radius = copy.planetRadiusKm == null
				? DEFAULT_PLANET_RADIUS_KM : copy.planetRadiusKm;
		double maximumHeight = Math.min(
				MAX_ATMOSPHERE_HEIGHT_KM, radius * 0.5D);
		if(copy.atmosphereHeightKm != null
				&& copy.atmosphereHeightKm > maximumHeight) {
			if(warn)
				warnOnce(context, "atmosphereHeightKm",
						"value exceeds min(10000, planetRadiusKm * 0.5); "
								+ "using the legacy-derived fallback");
			copy.atmosphereHeightKm = null;
		}

		if(copy.atmosphereHeightKm != null) {
			double height = copy.atmosphereHeightKm;
			if(copy.rayleighScaleHeightKm != null
					&& copy.rayleighScaleHeightKm > height) {
				if(warn)
					warnOnce(context, "rayleighScaleHeightKm",
							"value exceeds atmosphere height; "
									+ "using the legacy-derived fallback");
				copy.rayleighScaleHeightKm = null;
			}
			if(copy.mieScaleHeightKm != null
					&& copy.mieScaleHeightKm > height) {
				if(warn)
					warnOnce(context, "mieScaleHeightKm",
							"value exceeds atmosphere height; "
									+ "using the legacy-derived fallback");
				copy.mieScaleHeightKm = null;
			}
			if(copy.absorptionCenterKm != null
					&& copy.absorptionCenterKm > height) {
				if(warn)
					warnOnce(context, "absorptionCenterKm",
							"value exceeds atmosphere height; "
									+ "using the legacy-derived fallback");
				copy.absorptionCenterKm = null;
			}
			if(copy.absorptionWidthKm != null
					&& copy.absorptionWidthKm > height) {
				if(warn)
					warnOnce(context, "absorptionWidthKm",
							"value exceeds atmosphere height; "
									+ "using the legacy-derived fallback");
				copy.absorptionWidthKm = null;
			}
		}
		return copy;
	}

	public Snapshot snapshot() {
		Snapshot snapshot = cachedSnapshot;
		if(snapshot == null) {
			synchronized(this) {
				snapshot = cachedSnapshot;
				if(snapshot == null) {
					snapshot = new Snapshot(sanitizedCopy());
					cachedSnapshot = snapshot;
				}
			}
		}
		return snapshot;
	}

	public void writeToNBT(NBTTagCompound nbt) {
		if(nbt == null)
			throw new IllegalArgumentException("nbt cannot be null");
		if(!hasOverrides())
			return;

		nbt.setInteger("version", SCHEMA_VERSION);
		writeDouble(nbt, "planetRadiusKm", planetRadiusKm);
		writeDouble(nbt, "atmosphereHeightKm", atmosphereHeightKm);
		writeColor(nbt, "rayleighColor", rayleighColor);
		writeDouble(nbt, "rayleighStrength", rayleighStrength);
		writeDouble(nbt, "rayleighScaleHeightKm",
				rayleighScaleHeightKm);
		writeColor(nbt, "mieColor", mieColor);
		writeDouble(nbt, "mieStrength", mieStrength);
		writeDouble(nbt, "mieScaleHeightKm", mieScaleHeightKm);
		writeDouble(nbt, "mieAnisotropy", mieAnisotropy);
		writeColor(nbt, "absorptionColor", absorptionColor);
		writeDouble(nbt, "absorptionStrength", absorptionStrength);
		writeDouble(nbt, "absorptionCenterKm", absorptionCenterKm);
		writeDouble(nbt, "absorptionWidthKm", absorptionWidthKm);
		writeDouble(nbt, "multipleScatteringStrength",
				multipleScatteringStrength);
		writeDouble(nbt, "sunIntensityMultiplier",
				sunIntensityMultiplier);
		writeDouble(nbt, "exposure", exposure);
		if(cloudLayerMode != null)
			nbt.setString("cloudLayerMode", cloudLayerMode.name());
	}

	public static AtmosphereVisualProperties readFromNBT(
			NBTTagCompound nbt) {
		return readFromNBT(nbt, null);
	}

	/**
	 * Reads the optional {@value #NBT_KEY} child from a DimensionProperties
	 * compound.  A missing or mistyped child produces a fresh empty profile,
	 * which is important when an existing station object is updated in place.
	 */
	public static AtmosphereVisualProperties readFromParentNBT(
			NBTTagCompound parent, String context) {
		if(parent == null || !parent.hasKey(NBT_KEY))
			return new AtmosphereVisualProperties();
		if(!parent.hasKey(NBT_KEY, NBT.TAG_COMPOUND)) {
			warnOnce(context, NBT_KEY,
					"non-compound value; using the legacy-derived fallback");
			return new AtmosphereVisualProperties();
		}
		return readFromNBT(parent.getCompoundTag(NBT_KEY), context);
	}

	public static AtmosphereVisualProperties readFromNBT(
			NBTTagCompound nbt, String context) {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		if(nbt == null)
			return properties;

		if(nbt.hasKey("version")
				&& !nbt.hasKey("version", NBT.TAG_ANY_NUMERIC))
			warnOnce(context, "version",
					"non-numeric schema version; reading known v1 fields");
		else if(nbt.hasKey("version", NBT.TAG_ANY_NUMERIC)
				&& nbt.getInteger("version") < 1)
			warnOnce(context, "version",
					"invalid schema version; reading known v1 fields");

		properties.planetRadiusKm = readDouble(
				nbt, "planetRadiusKm",
				MIN_PLANET_RADIUS_KM, MAX_PLANET_RADIUS_KM, context);
		properties.atmosphereHeightKm = readDouble(
				nbt, "atmosphereHeightKm",
				MIN_ATMOSPHERE_HEIGHT_KM,
				MAX_ATMOSPHERE_HEIGHT_KM, context);
		properties.rayleighColor =
				readColor(nbt, "rayleighColor", context);
		properties.rayleighStrength = readDouble(
				nbt, "rayleighStrength", 0D, MAX_STRENGTH, context);
		properties.rayleighScaleHeightKm = readDouble(
				nbt, "rayleighScaleHeightKm",
				MIN_RAYLEIGH_SCALE_HEIGHT_KM,
				MAX_ATMOSPHERE_HEIGHT_KM, context);
		properties.mieColor = readColor(nbt, "mieColor", context);
		properties.mieStrength = readDouble(
				nbt, "mieStrength", 0D, MAX_STRENGTH, context);
		properties.mieScaleHeightKm = readDouble(
				nbt, "mieScaleHeightKm",
				MIN_MIE_SCALE_HEIGHT_KM,
				MAX_ATMOSPHERE_HEIGHT_KM, context);
		properties.mieAnisotropy = readDouble(
				nbt, "mieAnisotropy",
				MIN_MIE_ANISOTROPY, MAX_MIE_ANISOTROPY, context);
		properties.absorptionColor =
				readColor(nbt, "absorptionColor", context);
		properties.absorptionStrength = readDouble(
				nbt, "absorptionStrength", 0D, MAX_STRENGTH, context);
		properties.absorptionCenterKm = readDouble(
				nbt, "absorptionCenterKm", 0D,
				MAX_ATMOSPHERE_HEIGHT_KM, context);
		properties.absorptionWidthKm = readDouble(
				nbt, "absorptionWidthKm",
				MIN_ABSORPTION_WIDTH_KM,
				MAX_ATMOSPHERE_HEIGHT_KM, context);
		properties.multipleScatteringStrength = readDouble(
				nbt, "multipleScatteringStrength", 0D,
				MAX_MULTIPLE_SCATTERING_STRENGTH, context);
		properties.sunIntensityMultiplier = readDouble(
				nbt, "sunIntensityMultiplier",
				0D, MAX_STRENGTH, context);
		properties.exposure = readDouble(
				nbt, "exposure", MIN_EXPOSURE, MAX_EXPOSURE, context);

		if(nbt.hasKey("cloudLayerMode")) {
			if(!nbt.hasKey("cloudLayerMode", NBT.TAG_STRING)) {
				warnOnce(context, "cloudLayerMode",
						"non-string value; using the legacy-derived fallback");
			}
			else {
				CloudLayerMode mode =
						CloudLayerMode.parse(nbt.getString("cloudLayerMode"));
				if(mode == null)
					warnOnce(context, "cloudLayerMode",
							"unknown value; using the legacy-derived fallback");
				else
					properties.cloudLayerMode = mode;
			}
		}

		return properties.sanitizedCopy(context);
	}

	private static void writeDouble(
			NBTTagCompound nbt, String key, Double value) {
		if(value != null)
			nbt.setDouble(key, value);
	}

	private static void writeColor(
			NBTTagCompound nbt, String key, float[] color) {
		if(color == null)
			return;
		NBTTagList list = new NBTTagList();
		for(float component : color)
			list.appendTag(new NBTTagFloat(component));
		nbt.setTag(key, list);
	}

	private static Double readDouble(
			NBTTagCompound nbt, String key,
			double minimum, double maximum, String context) {
		if(!nbt.hasKey(key))
			return null;
		if(!nbt.hasKey(key, NBT.TAG_ANY_NUMERIC)) {
			warnOnce(context, key,
					"non-numeric value; using the legacy-derived fallback");
			return null;
		}

		double value = nbt.getDouble(key);
		if(!isFinite(value) || value < minimum || value > maximum) {
			warnOnce(context, key,
					"value must be finite and in [" + minimum + ", "
							+ maximum
							+ "]; using the legacy-derived fallback");
			return null;
		}
		return value == 0D ? 0D : value;
	}

	private static float[] readColor(
			NBTTagCompound nbt, String key, String context) {
		if(!nbt.hasKey(key))
			return null;
		if(!nbt.hasKey(key, NBT.TAG_LIST)) {
			warnOnce(context, key,
					"non-list RGB value; using the legacy-derived fallback");
			return null;
		}

		NBTTagList list = nbt.getTagList(key, NBT.TAG_FLOAT);
		if(list.tagCount() != 3) {
			warnOnce(context, key,
					"RGB value must contain exactly three floats; "
							+ "using the legacy-derived fallback");
			return null;
		}

		float[] color = new float[3];
		for(int index = 0; index < color.length; index++)
			color[index] = list.func_150308_e(index);
		try {
			return validateColor(key, color);
		}
		catch(IllegalArgumentException exception) {
			warnOnce(context, key, exception.getMessage()
					+ "; using the legacy-derived fallback");
			return null;
		}
	}

	private static double requireValue(String field, Double value) {
		if(value == null)
			throw missingValue(field);
		return value;
	}

	private void markDirty() {
		cachedSnapshot = null;
	}

	private static IllegalStateException missingValue(String field) {
		return new IllegalStateException(
				field + " has no explicit atmosphere visual override");
	}

	private static Double requireRange(
			String field, double value,
			double minimum, double maximum) {
		if(!isFinite(value) || value < minimum || value > maximum)
			throw new IllegalArgumentException(
					field + " must be finite and in ["
							+ minimum + ", " + maximum + "]");
		return value == 0D ? 0D : value;
	}

	private static double maximumAtmosphereHeight(double radius) {
		return Math.min(MAX_ATMOSPHERE_HEIGHT_KM, radius * 0.5D);
	}

	private void requireWithinExplicitAtmosphere(
			String field, double value) {
		if(atmosphereHeightKm != null
				&& value > atmosphereHeightKm)
			throw new IllegalArgumentException(
					field + " must not exceed atmosphereHeightKm");
	}

	private void requireDependentFieldsWithin(double height) {
		if(rayleighScaleHeightKm != null
				&& rayleighScaleHeightKm > height)
			throw new IllegalArgumentException(
					"atmosphere geometry would make "
							+ "rayleighScaleHeightKm exceed "
							+ "atmosphereHeightKm");
		if(mieScaleHeightKm != null && mieScaleHeightKm > height)
			throw new IllegalArgumentException(
					"atmosphere geometry would make "
							+ "mieScaleHeightKm exceed "
							+ "atmosphereHeightKm");
		if(absorptionCenterKm != null && absorptionCenterKm > height)
			throw new IllegalArgumentException(
					"atmosphere geometry would make "
							+ "absorptionCenterKm exceed "
							+ "atmosphereHeightKm");
		if(absorptionWidthKm != null && absorptionWidthKm > height)
			throw new IllegalArgumentException(
					"atmosphere geometry would make "
							+ "absorptionWidthKm exceed "
							+ "atmosphereHeightKm");
	}

	private static float[] validateColor(String field, float[] value) {
		if(value == null || value.length != 3)
			throw new IllegalArgumentException(
					field + " must contain exactly three components");
		float[] copy = new float[3];
		for(int index = 0; index < copy.length; index++) {
			float component = value[index];
			if(!isFinite(component)
					|| component < 0F
					|| component > MAX_COLOR_COMPONENT)
				throw new IllegalArgumentException(
						field + " components must be finite and in [0, "
								+ MAX_COLOR_COMPONENT + "]");
			copy[index] = component == 0F ? 0F : component;
		}
		return copy;
	}

	private static float[] requireColor(String field, float[] value) {
		if(value == null)
			throw missingValue(field);
		return copyColor(value);
	}

	private static float requireColorComponent(
			String field, float[] value, int index) {
		if(value == null)
			throw missingValue(field);
		if(index < 0 || index >= 3)
			throw new IndexOutOfBoundsException(
					"RGB component index must be in [0, 2]");
		return value[index];
	}

	private static float[] copyColor(float[] value) {
		return value == null ? null : value.clone();
	}

	private static boolean isFinite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static boolean isFinite(float value) {
		return !Float.isNaN(value) && !Float.isInfinite(value);
	}

	private static void warnOnce(
			String context, String field, String message) {
		String description = context == null || context.trim().isEmpty()
				? "unknown planet" : context;
		String warningKey = description + "|" + field + "|" + message;
		if(EMITTED_WARNINGS.add(warningKey))
			AdvancedRocketry.logger.warn(
					"Atmosphere rendering override for "
							+ description + ", tag '" + field + "': "
							+ message);
	}

	/**
	 * Immutable, content-comparable view used by client-side profile caches.
	 */
	public static final class Snapshot {
		private final Double planetRadiusKm;
		private final Double atmosphereHeightKm;
		private final float[] rayleighColor;
		private final Double rayleighStrength;
		private final Double rayleighScaleHeightKm;
		private final float[] mieColor;
		private final Double mieStrength;
		private final Double mieScaleHeightKm;
		private final Double mieAnisotropy;
		private final float[] absorptionColor;
		private final Double absorptionStrength;
		private final Double absorptionCenterKm;
		private final Double absorptionWidthKm;
		private final Double multipleScatteringStrength;
		private final Double sunIntensityMultiplier;
		private final Double exposure;
		private final CloudLayerMode cloudLayerMode;
		private final int contentHash;

		private Snapshot(AtmosphereVisualProperties source) {
			planetRadiusKm = source.planetRadiusKm;
			atmosphereHeightKm = source.atmosphereHeightKm;
			rayleighColor = copyColor(source.rayleighColor);
			rayleighStrength = source.rayleighStrength;
			rayleighScaleHeightKm = source.rayleighScaleHeightKm;
			mieColor = copyColor(source.mieColor);
			mieStrength = source.mieStrength;
			mieScaleHeightKm = source.mieScaleHeightKm;
			mieAnisotropy = source.mieAnisotropy;
			absorptionColor = copyColor(source.absorptionColor);
			absorptionStrength = source.absorptionStrength;
			absorptionCenterKm = source.absorptionCenterKm;
			absorptionWidthKm = source.absorptionWidthKm;
			multipleScatteringStrength =
					source.multipleScatteringStrength;
			sunIntensityMultiplier = source.sunIntensityMultiplier;
			exposure = source.exposure;
			cloudLayerMode = source.cloudLayerMode;
			contentHash = calculateHash();
		}

		public boolean hasOverrides() {
			return planetRadiusKm != null
					|| atmosphereHeightKm != null
					|| rayleighColor != null
					|| rayleighStrength != null
					|| rayleighScaleHeightKm != null
					|| mieColor != null
					|| mieStrength != null
					|| mieScaleHeightKm != null
					|| mieAnisotropy != null
					|| absorptionColor != null
					|| absorptionStrength != null
					|| absorptionCenterKm != null
					|| absorptionWidthKm != null
					|| multipleScatteringStrength != null
					|| sunIntensityMultiplier != null
					|| exposure != null
					|| cloudLayerMode != null;
		}

		public boolean hasPlanetRadiusKm() {
			return planetRadiusKm != null;
		}

		public double getPlanetRadiusKm() {
			return requireValue("planetRadiusKm", planetRadiusKm);
		}

		public boolean hasAtmosphereHeightKm() {
			return atmosphereHeightKm != null;
		}

		public double getAtmosphereHeightKm() {
			return requireValue("atmosphereHeightKm",
					atmosphereHeightKm);
		}

		public boolean hasRayleighColor() {
			return rayleighColor != null;
		}

		public float[] getRayleighColor() {
			return requireColor("rayleighColor", rayleighColor);
		}

		public float getRayleighColorComponent(int index) {
			return requireColorComponent(
					"rayleighColor", rayleighColor, index);
		}

		public boolean hasRayleighStrength() {
			return rayleighStrength != null;
		}

		public double getRayleighStrength() {
			return requireValue("rayleighStrength", rayleighStrength);
		}

		public boolean hasRayleighScaleHeightKm() {
			return rayleighScaleHeightKm != null;
		}

		public double getRayleighScaleHeightKm() {
			return requireValue(
					"rayleighScaleHeightKm", rayleighScaleHeightKm);
		}

		public boolean hasMieColor() {
			return mieColor != null;
		}

		public float[] getMieColor() {
			return requireColor("mieColor", mieColor);
		}

		public float getMieColorComponent(int index) {
			return requireColorComponent("mieColor", mieColor, index);
		}

		public boolean hasMieStrength() {
			return mieStrength != null;
		}

		public double getMieStrength() {
			return requireValue("mieStrength", mieStrength);
		}

		public boolean hasMieScaleHeightKm() {
			return mieScaleHeightKm != null;
		}

		public double getMieScaleHeightKm() {
			return requireValue(
					"mieScaleHeightKm", mieScaleHeightKm);
		}

		public boolean hasMieAnisotropy() {
			return mieAnisotropy != null;
		}

		public double getMieAnisotropy() {
			return requireValue("mieAnisotropy", mieAnisotropy);
		}

		public boolean hasAbsorptionColor() {
			return absorptionColor != null;
		}

		public float[] getAbsorptionColor() {
			return requireColor("absorptionColor", absorptionColor);
		}

		public float getAbsorptionColorComponent(int index) {
			return requireColorComponent(
					"absorptionColor", absorptionColor, index);
		}

		public boolean hasAbsorptionStrength() {
			return absorptionStrength != null;
		}

		public double getAbsorptionStrength() {
			return requireValue(
					"absorptionStrength", absorptionStrength);
		}

		public boolean hasAbsorptionCenterKm() {
			return absorptionCenterKm != null;
		}

		public double getAbsorptionCenterKm() {
			return requireValue(
					"absorptionCenterKm", absorptionCenterKm);
		}

		public boolean hasAbsorptionWidthKm() {
			return absorptionWidthKm != null;
		}

		public double getAbsorptionWidthKm() {
			return requireValue(
					"absorptionWidthKm", absorptionWidthKm);
		}

		public boolean hasMultipleScatteringStrength() {
			return multipleScatteringStrength != null;
		}

		public double getMultipleScatteringStrength() {
			return requireValue("multipleScatteringStrength",
					multipleScatteringStrength);
		}

		public boolean hasSunIntensityMultiplier() {
			return sunIntensityMultiplier != null;
		}

		public double getSunIntensityMultiplier() {
			return requireValue(
					"sunIntensityMultiplier", sunIntensityMultiplier);
		}

		public boolean hasExposure() {
			return exposure != null;
		}

		public double getExposure() {
			return requireValue("exposure", exposure);
		}

		public boolean hasCloudLayerMode() {
			return cloudLayerMode != null;
		}

		public CloudLayerMode getCloudLayerMode() {
			if(cloudLayerMode == null)
				throw missingValue("cloudLayerMode");
			return cloudLayerMode;
		}

		public int getContentHash() {
			return contentHash;
		}

		@Override
		public boolean equals(Object object) {
			if(this == object)
				return true;
			if(!(object instanceof Snapshot))
				return false;
			Snapshot other = (Snapshot)object;
			return equalsNullable(planetRadiusKm, other.planetRadiusKm)
					&& equalsNullable(
							atmosphereHeightKm,
							other.atmosphereHeightKm)
					&& Arrays.equals(
							rayleighColor, other.rayleighColor)
					&& equalsNullable(
							rayleighStrength, other.rayleighStrength)
					&& equalsNullable(
							rayleighScaleHeightKm,
							other.rayleighScaleHeightKm)
					&& Arrays.equals(mieColor, other.mieColor)
					&& equalsNullable(
							mieStrength, other.mieStrength)
					&& equalsNullable(
							mieScaleHeightKm,
							other.mieScaleHeightKm)
					&& equalsNullable(
							mieAnisotropy, other.mieAnisotropy)
					&& Arrays.equals(
							absorptionColor, other.absorptionColor)
					&& equalsNullable(
							absorptionStrength,
							other.absorptionStrength)
					&& equalsNullable(
							absorptionCenterKm,
							other.absorptionCenterKm)
					&& equalsNullable(
							absorptionWidthKm,
							other.absorptionWidthKm)
					&& equalsNullable(
							multipleScatteringStrength,
							other.multipleScatteringStrength)
					&& equalsNullable(
							sunIntensityMultiplier,
							other.sunIntensityMultiplier)
					&& equalsNullable(exposure, other.exposure)
					&& cloudLayerMode == other.cloudLayerMode;
		}

		@Override
		public int hashCode() {
			return contentHash;
		}

		private int calculateHash() {
			int result = hashNullable(planetRadiusKm);
			result = 31 * result + hashNullable(atmosphereHeightKm);
			result = 31 * result + Arrays.hashCode(rayleighColor);
			result = 31 * result + hashNullable(rayleighStrength);
			result = 31 * result
					+ hashNullable(rayleighScaleHeightKm);
			result = 31 * result + Arrays.hashCode(mieColor);
			result = 31 * result + hashNullable(mieStrength);
			result = 31 * result + hashNullable(mieScaleHeightKm);
			result = 31 * result + hashNullable(mieAnisotropy);
			result = 31 * result + Arrays.hashCode(absorptionColor);
			result = 31 * result + hashNullable(absorptionStrength);
			result = 31 * result + hashNullable(absorptionCenterKm);
			result = 31 * result + hashNullable(absorptionWidthKm);
			result = 31 * result
					+ hashNullable(multipleScatteringStrength);
			result = 31 * result
					+ hashNullable(sunIntensityMultiplier);
			result = 31 * result + hashNullable(exposure);
			result = 31 * result
					+ (cloudLayerMode == null
							? 0 : cloudLayerMode.hashCode());
			return result;
		}

		private static boolean equalsNullable(
				Object first, Object second) {
			return first == null ? second == null : first.equals(second);
		}

		private static int hashNullable(Object value) {
			return value == null ? 0 : value.hashCode();
		}
	}
}
