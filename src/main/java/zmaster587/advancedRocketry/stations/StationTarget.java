package zmaster587.advancedRocketry.stations;

import javax.annotation.Nullable;

import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.dimension.DimensionProperties;

/**
 * Explicit result of resolving an integer stored in a station's orbit fields.
 * Synthetic stellar IDs are never exposed as Forge dimensions.
 */
public final class StationTarget {

	public enum Kind {
		DIMENSION,
		BLACK_HOLE_STAR,
		WARP,
		INVALID
	}

	private final int rawId;
	private final Kind kind;
	private final DimensionProperties dimensionProperties;
	private final StellarBody stellarBody;

	StationTarget(int rawId, Kind kind,
			@Nullable DimensionProperties dimensionProperties,
			@Nullable StellarBody stellarBody) {
		this.rawId = rawId;
		this.kind = kind;
		this.dimensionProperties = dimensionProperties;
		this.stellarBody = stellarBody;
	}

	public int getRawId() {
		return rawId;
	}

	public Kind getKind() {
		return kind;
	}

	@Nullable
	public DimensionProperties getDimensionProperties() {
		return dimensionProperties;
	}

	@Nullable
	public StellarBody getStellarBody() {
		return stellarBody;
	}

	public boolean isValid() {
		return kind != Kind.INVALID;
	}

	public boolean isDestination() {
		return kind == Kind.DIMENSION || kind == Kind.BLACK_HOLE_STAR;
	}
}
