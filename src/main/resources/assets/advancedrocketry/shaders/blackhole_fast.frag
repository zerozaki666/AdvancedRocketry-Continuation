#version 120

uniform sampler2D uSceneColor;
uniform sampler2D uShadowBoundary;
uniform vec4 uViewport;
uniform vec2 uCenter;
uniform float uShadowRadius;
uniform float uProxyRadius;
uniform float uMass;
uniform float uSpin;
uniform vec2 uProjectedSpinAxis;
uniform float uInclination;
uniform float uDiskInner;
uniform float uDiskOuter;
uniform float uAccretionRate;
uniform float uWorldTime;
uniform float uBodyPhase;
uniform float uAlpha;
uniform float uSteps;

varying vec2 vProxyUv;

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;
const float SQRT_27 = 5.196152422706632;

float shadowBoundary(vec2 p) {
	vec2 axis = normalize(uProjectedSpinAxis + vec2(0.000001, 0.0));
	vec2 majorAxis = vec2(-axis.y, axis.x);
	vec2 curvePoint = vec2(dot(p, majorAxis), dot(p, axis));
	float angle = atan(curvePoint.y, curvePoint.x)/TWO_PI + 0.5;
	return texture2D(uShadowBoundary, vec2(fract(angle), 0.5)).r;
}

float annulus(float radius, float innerRadius, float outerRadius,
		float softness) {
	return smoothstep(innerRadius, innerRadius + softness, radius)
			*(1.0 - smoothstep(outerRadius - softness, outerRadius,
			radius));
}

void main() {
	vec2 viewportSize = max(uViewport.zw, vec2(1.0));
	vec2 originalUv = clamp((gl_FragCoord.xy - uViewport.xy)
			/viewportSize, vec2(0.0), vec2(1.0));
	vec2 p = (gl_FragCoord.xy - uCenter)/max(uShadowRadius, 0.5);
	float radial = length(p);
	float proxyLimit = uProxyRadius/max(uShadowRadius, 0.5);
	if(radial > proxyLimit)
		discard;

	float boundary = shadowBoundary(p);
	float edgeWidth = max(1.25/max(uShadowRadius, 1.0), 0.006);
	float shadow = 1.0 - smoothstep(boundary - edgeWidth,
			boundary + edgeWidth, radial);
	vec2 radialDirection = radial > 0.0001 ? p/radial : vec2(1.0, 0.0);
	vec2 tangentDirection = vec2(-radialDirection.y, radialDirection.x);

	// Bounded weak-field heuristic outside the apparent shadow.
	float impact = max(radial, boundary + 0.035);
	float inverseImpact = 1.0/impact;
	vec2 projectedAxis = normalize(uProjectedSpinAxis
			+ vec2(0.000001, 0.0));
	float side = sign(dot(p,
			vec2(-projectedAxis.y, projectedAxis.x)));
	float radialDeflection = min(0.34,
			0.115*inverseImpact + 0.038*inverseImpact*inverseImpact);
	float frameDragging = clamp(side*0.045*uSpin
			*inverseImpact*inverseImpact, -0.10, 0.10);
	float lensWindow = 1.0 - smoothstep(boundary + 1.6,
			proxyLimit, radial);
	vec2 sampleOffset = (radialDirection*radialDeflection
			+ tangentDirection*frameDragging)*lensWindow
			*uShadowRadius/viewportSize;
	vec2 lensedUv = clamp(originalUv + sampleOffset,
			vec2(0.001), vec2(0.999));
	vec3 originalColor = texture2D(uSceneColor, originalUv).rgb;
	vec3 color = texture2D(uSceneColor, lensedUv).rgb;

	vec2 spinAxis = normalize(uProjectedSpinAxis + vec2(0.000001, 0.0));
	vec2 diskMajorAxis = vec2(-spinAxis.y, spinAxis.x);
	float flattening = max(0.12, abs(cos(uInclination)));
	vec2 diskPoint = vec2(dot(p, diskMajorAxis),
			dot(p, spinAxis)/flattening);
	float diskRadius = length(diskPoint)*SQRT_27;
	float diskSoftness = max(0.08, 1.5/max(uShadowRadius, 1.0));
	float diskMask = annulus(diskRadius, uDiskInner, uDiskOuter,
			diskSoftness)*(1.0 - shadow);

	float safeDiskRadius = max(diskRadius, uDiskInner + 0.0001);
	float zeroTorque = max(0.0,
			1.0 - sqrt(uDiskInner/safeDiskRadius));
	float temperature = pow(max(zeroTorque
			/(safeDiskRadius*safeDiskRadius*safeDiskRadius), 0.0), 0.25);
	float normalizedHeat = clamp(temperature
			*pow(max(uDiskInner, 1.0), 0.75)*2.4, 0.0, 1.0);

	float beta = min(0.85, inversesqrt(max(safeDiskRadius, 1.0)));
	float gamma = inversesqrt(max(0.001, 1.0 - beta*beta));
	float orbitalSide = diskRadius > 0.0001
			? diskPoint.x/(diskRadius/SQRT_27) : 0.0;
	float betaLos = clamp(beta*sin(uInclination)*orbitalSide,
			-0.849, 0.849);
	float doppler = clamp(1.0/(gamma*(1.0 - betaLos)), 0.25, 4.0);
	float beaming = clamp(doppler*doppler*doppler, 0.15, 7.0);

	float diskAngle = atan(diskPoint.y, diskPoint.x);
	float phase = uWorldTime*0.035 + uBodyPhase*TWO_PI;
	float boundedNoise = 0.82 + 0.18*sin(diskRadius*13.0
			+ diskAngle*5.0 - phase);
	float emission = clamp(diskMask*uAccretionRate*normalizedHeat
			*boundedNoise*beaming, 0.0, 2.4);
	vec3 redShift = vec3(1.0, 0.24, 0.055);
	vec3 blueShift = vec3(0.42, 0.70, 1.0);
	vec3 diskColor = mix(redShift, blueShift,
			clamp(0.5 + 0.28*(doppler - 1.0), 0.0, 1.0));
	color += diskColor*emission;

	// A finite emissive band near the critical curve.  It is a visual proxy,
	// not a resolved hierarchy of photon orbits.
	float criticalBand = (1.0 - smoothstep(edgeWidth,
			edgeWidth*5.0, abs(radial - boundary)))
			*uAccretionRate;
	color += vec3(1.0, 0.62, 0.24)*criticalBand*0.75;
	color = mix(color, vec3(0.0), shadow);
	color = mix(originalColor, color, clamp(uAlpha, 0.0, 1.0));
	gl_FragColor = vec4(clamp(color, 0.0, 4.0), 1.0);
}
