package zmaster587.advancedRocketry.util;

import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.BlackHoleLuminosityMode;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.api.dimension.solar.BlackHoleProperties;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;

public class AstronomicalBodyHelper {
	
	public static float getBodySizeMultiplier(float orbitalDistance) {
		//Returns size multiplier relative to Earth standard (1AU = 100 Distance)
		return 100f/orbitalDistance;
	}
	public static double getOrbitalPeriod(int orbitalDistance, float solarSize) {
		//Gives output in MC Days, uses 40 for Orbital Mechanics G
		//One MC Year is 48 MC days (16 IRL Hours), one month is 8 MC Days
		return 48d*Math.pow((Math.pow((orbitalDistance/100d), 3)*Math.pow(Math.PI, 2))/(Math.pow(solarSize, 3)*10d), 0.5d);
	}
	public static double getMoonOrbitalPeriod(float orbitalDistance, float planetaryMass) {
		//The same a the function for planets, but since gravity is directly correlated with mass uses the gravity of the plant for mass
		return 48d*Math.pow((Math.pow((orbitalDistance/100d), 3)*Math.pow(Math.PI, 2))/(planetaryMass*10d), 0.5d);
	}
	public static double getOrbitalTheta(int orbitalDistance, float solarSize) {
		double orbitalPeriod = getOrbitalPeriod(orbitalDistance, solarSize);
		//Returns angle, relative to 0, of a planet at any given time
		return ((AdvancedRocketry.proxy.getWorldTimeUniversal(0) % (24000d*orbitalPeriod))/(24000d*orbitalPeriod))*(2d*Math.PI);
	}
	public static double getMoonOrbitalTheta(int orbitalDistance, float parentGravitationalMultiplier) {
		//Because the function is still in AU and solar mass, some correctional factors to convert to those units
		double orbitalPeriod = getMoonOrbitalPeriod(orbitalDistance * 0.0025f, parentGravitationalMultiplier * 0.000003f);
		//Returns angle, relative to 0, of a moon at any given time
		return ((AdvancedRocketry.proxy.getWorldTimeUniversal(0) % (24000d*orbitalPeriod))/(24000d*orbitalPeriod))*(2d*Math.PI);
	}
	public static int getAverageTemperature(StellarBody star, int orbitalDistance, int atmPressure) {
		int starSurfaceTemperature = 58 * star.getTemperature();
		float starRadius = star.getSize()/215f;
		//Gives output in AU
		float planetaryOrbitalRadius = orbitalDistance/100f;
		//Albedo is 0.3f hardcoded because of inability to easily calculate
		double averageWithoutAtmosphere = starSurfaceTemperature * Math.pow(starRadius/(2 * planetaryOrbitalRadius), 0.5) * Math.pow((1f-0.3f), 0.25);
		//Slightly kludgey solution that works out mostly for Venus and well for Earth, without being overly complex
		double result = averageWithoutAtmosphere * Math.max(1,
				(1.125d * Math.pow((atmPressure/100f), 0.25)));
		if(allComponentsAreBlackHoles(star)
				&& Configuration.blackHoleLuminosityMode
						== BlackHoleLuminosityMode.ACCRETION_RATE) {
			double factor = getAccretionLuminosityFactor(star);
			if(factor == 0D)
				return 0;
			result *= Math.pow(factor, 0.25D);
			if(Double.isNaN(result) || Double.isInfinite(result))
				return 0;
		}
		return (int)result;
	}
	public static double getStellarBrightness(StellarBody star, int orbitalDistance) {
		if(star == null)
			return 0D;

		//Make all values ratios of Earth normal to get ratio compared to Earth
		float normalizedStarTemperature = star.getTemperature()/100f;
		float planetaryOrbitalRadius = orbitalDistance/100f;
		//Returns ratio compared to a planet at 1 AU for Sol, because the other values in AR are normalized,
		//and this works fairly well for hooking into with other mod's solar panels & such
		double brightness = ((Math.pow(star.getSize(), 2)
				* Math.pow(normalizedStarTemperature, 4))
				/Math.pow(planetaryOrbitalRadius, 2));
		if(allComponentsAreBlackHoles(star)) {
			double factor = Configuration.blackHoleLuminosityMode
					== BlackHoleLuminosityMode.ACCRETION_RATE
					? getAccretionLuminosityFactor(star) : 0.25D;
			brightness *= factor;
			if(Double.isNaN(brightness) || Double.isInfinite(brightness))
				return 0D;
		}
		return brightness;
	}

	public static boolean allComponentsAreBlackHoles(StellarBody star) {
		if(star == null || !star.isBlackHole())
			return false;
		for(StellarBody subStar : star.getSubStars()) {
			if(subStar == null || !subStar.isBlackHole())
				return false;
		}
		return true;
	}

	private static double getAccretionLuminosityFactor(StellarBody star) {
		BlackHoleProperties properties = star.getBlackHoleProperties();
		if(properties == null)
			return 0.25D;
		double factor = properties.getAccretionRate();
		if(Double.isNaN(factor) || Double.isInfinite(factor))
			return 0D;
		return Math.max(0D, Math.min(1D, factor));
	}
}
