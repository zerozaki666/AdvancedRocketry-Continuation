package zmaster587.advancedRocketry.client.render.planet;

final class StationPlanetRotationMath {

	private StationPlanetRotationMath() { }

	static double getRotationDegrees(long worldTime, float partialTicks,
			double periodTicks, double speedMultiplier) {
		if(periodTicks <= 0D || Double.isNaN(periodTicks)
				|| Double.isInfinite(periodTicks)
				|| speedMultiplier == 0D
				|| Double.isNaN(speedMultiplier)
				|| Double.isInfinite(speedMultiplier))
			return 0D;

		/*
		 * Cast before adding partialTicks.  A long + float expression is
		 * evaluated as a float by Java's binary numeric promotion.  Long-lived
		 * saves therefore quantized their world time into increasingly large
		 * steps and lost frame interpolation entirely.
		 */
		double animationTicks = (double)worldTime
				+sanitizePartialTicks(partialTicks);
		double cycles = animationTicks*speedMultiplier/periodTicks;
		return (cycles-Math.floor(cycles))*360D;
	}

	private static double sanitizePartialTicks(float partialTicks) {
		if(Float.isNaN(partialTicks) || Float.isInfinite(partialTicks))
			return 0D;
		return Math.max(0D, Math.min(1D, (double)partialTicks));
	}
}
