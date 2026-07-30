package zmaster587.advancedRocketry.api;

import java.util.Locale;

/**
 * Server-side free-space interaction level. The default is deliberately
 * non-destructive for existing worlds.
 */
public enum BlackHoleInteractionMode {
	VISUAL_ONLY(false, false, false),
	WARNING(true, false, false),
	GRAVITY(true, true, false),
	CAPTURE(true, true, true);

	private final boolean warning;
	private final boolean gravity;
	private final boolean capture;

	BlackHoleInteractionMode(boolean warning, boolean gravity,
			boolean capture) {
		this.warning = warning;
		this.gravity = gravity;
		this.capture = capture;
	}

	public boolean hasWarning() { return warning; }
	public boolean hasGravity() { return gravity; }
	public boolean hasCapture() { return capture; }

	public static BlackHoleInteractionMode parse(String value) {
		if(value != null) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			}
			catch(IllegalArgumentException ignored) {
			}
		}
		return VISUAL_ONLY;
	}
}
