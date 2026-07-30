package zmaster587.advancedRocketry.client.render.atmosphere;

/**
 * Pure display-space geometry shared by the station atmosphere paths.
 *
 * The station planet intentionally keeps its nearest ground point at the
 * legacy sky-plane distance.  Large visual scale multipliers can make a
 * physically proportioned atmosphere thicker than that gap, however, so the
 * complete concentric body must move away before an outer proxy shell crosses
 * the camera.  Keeping the camera outside also makes the renderer use the
 * nearby, front-facing shell intersection instead of a far-side intersection
 * that can lie beyond the view distance.
 */
public final class AtmosphereShellGeometry {

	public static final int STATION_SHELL_LATITUDE_SEGMENTS = 32;
	public static final double NORMAL_CLOUD_RADIUS_SCALE = 1.006D;
	public static final double GAS_GIANT_CLOUD_RADIUS_SCALE = 1.012D;

	private static final double STATION_SURFACE_CLEARANCE = 10.0D;
	private static final double STATION_MINIMUM_SHELL_CLEARANCE = 1.0D;
	private static final double FALLBACK_MINIMUM_SHELL_RATIO = 0.0001D;
	private static final double STATION_PROXY_FACET_CLEARANCE_SCALE =
			1.0D/Math.cos(Math.PI/STATION_SHELL_LATITUDE_SEGMENTS)-1.0D;

	private AtmosphereShellGeometry() {
	}

	public static double physicalOuterRadius(double displayGroundRadius,
			double shellThicknessRatio) {
		if(!validRadius(displayGroundRadius))
			return Double.NaN;
		double ratio = sanitizeShellRatio(shellThicknessRatio);
		return displayGroundRadius*(1.0D+ratio);
	}

	public static double fallbackOuterRadius(double displayGroundRadius,
			double shellThicknessRatio, double visualPressure) {
		if(!validRadius(displayGroundRadius))
			return Double.NaN;
		double ratio = Math.max(FALLBACK_MINIMUM_SHELL_RATIO,
				sanitizeShellRatio(shellThicknessRatio));
		double pressure = AtmosphereMath.clampFinite(visualPressure,
				0.0D, 4.0D, 1.0D);
		double widthMultiplier = 0.8D+0.2D*pressure;
		return displayGroundRadius*(1.0D+ratio*widthMultiplier);
	}

	public static double cloudOuterRadius(double displayGroundRadius,
			boolean gasGiant) {
		if(!validRadius(displayGroundRadius))
			return Double.NaN;
		return displayGroundRadius*(gasGiant
				? GAS_GIANT_CLOUD_RADIUS_SCALE
				: NORMAL_CLOUD_RADIUS_SCALE);
	}

	/**
	 * Includes every path that can be selected in the current frame.  Shader
	 * modes also include the analytic radius because LUT or capability failure
	 * can select that fallback dynamically.
	 */
	public static double maximumRenderedRadius(double displayGroundRadius,
			double shellThicknessRatio, double visualPressure,
			boolean shaderAtmosphereEnabled,
			boolean fallbackAtmosphereEnabled,
			boolean cloudLayerEnabled, boolean gasGiant) {
		if(!validRadius(displayGroundRadius))
			return Double.NaN;

		double maximum = displayGroundRadius;
		if(shaderAtmosphereEnabled)
			maximum = Math.max(maximum, physicalOuterRadius(
					displayGroundRadius, shellThicknessRatio));
		if(fallbackAtmosphereEnabled)
			maximum = Math.max(maximum, fallbackOuterRadius(
					displayGroundRadius, shellThicknessRatio,
					visualPressure));
		if(cloudLayerEnabled)
			maximum = Math.max(maximum, cloudOuterRadius(
					displayGroundRadius, gasGiant));
		return maximum;
	}

	/**
	 * Preserves the legacy surface position until the outer envelope would
	 * approach the camera.  The mesh-derived term places the analytic tangent
	 * beyond the first latitude row of the current 32-row proxy and also grows
	 * the separation when extreme scale multipliers reduce float precision.
	 */
	public static double resolveStationCenterY(double displayGroundRadius,
			double maximumRenderedRadius) {
		if(!validRadius(displayGroundRadius))
			return Double.NaN;
		double envelope = validRadius(maximumRenderedRadius)
				? Math.max(displayGroundRadius, maximumRenderedRadius)
				: displayGroundRadius;
		if(envelope <= displayGroundRadius)
			return -(displayGroundRadius+STATION_SURFACE_CLEARANCE);
		double shellClearance = Math.max(
				STATION_MINIMUM_SHELL_CLEARANCE,
				envelope*STATION_PROXY_FACET_CLEARANCE_SCALE);
		double centerDistance = Math.max(
				displayGroundRadius+STATION_SURFACE_CLEARANCE,
				envelope+shellClearance);
		return -centerDistance;
	}

	private static double sanitizeShellRatio(double value) {
		return AtmosphereMath.clampFinite(value, 0.0D, 0.5D, 0.0D);
	}

	private static boolean validRadius(double value) {
		return AtmosphereMath.isFinite(value) && value > 0.0D;
	}
}
