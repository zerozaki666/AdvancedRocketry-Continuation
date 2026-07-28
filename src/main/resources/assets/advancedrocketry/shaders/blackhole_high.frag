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

const float TWO_PI = 6.28318530717958647692;
const float SQRT_27 = 5.196152422706632;
const int MAX_STEPS = 32;

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
	float edgeWidth = max(1.15/max(uShadowRadius, 1.0), 0.0045);
	float shadow = 1.0 - smoothstep(boundary - edgeWidth,
			boundary + edgeWidth, radial);

	// Fixed-upper-bound screen-space bending.  uSteps only masks iterations;
	// the loop bound remains compile-time constant for GLSL 1.20 drivers.
	vec2 ray = p;
	for(int stepIndex = 0; stepIndex < MAX_STEPS; ++stepIndex) {
		float enabled = 1.0 - step(uSteps - 0.5, float(stepIndex));
		float rayRadius = max(length(ray), boundary + 0.035);
		vec2 rayDirection = ray/rayRadius;
		vec2 tangent = vec2(-rayDirection.y, rayDirection.x);
		float radialStep = min(0.014,
				0.0055/(rayRadius*rayRadius));
		float spinStep = clamp(uSpin*0.0018
				/(rayRadius*rayRadius), -0.003, 0.003);
		ray += enabled*(rayDirection*radialStep + tangent*spinStep);
	}
	float lensWindow = 1.0 - smoothstep(boundary + 1.8,
			proxyLimit, radial);
	vec2 sampleOffset = (ray - p)*lensWindow
			*uShadowRadius/viewportSize;
	vec2 lensedUv = clamp(originalUv + sampleOffset,
			vec2(0.001), vec2(0.999));
	vec3 originalColor = texture2D(uSceneColor, originalUv).rgb;
	vec3 color = texture2D(uSceneColor, lensedUv).rgb;

	vec2 spinAxis = normalize(uProjectedSpinAxis + vec2(0.000001, 0.0));
	vec2 diskMajorAxis = vec2(-spinAxis.y, spinAxis.x);
	float flattening = max(0.10, abs(cos(uInclination)));
	vec2 diskPoint = vec2(dot(p, diskMajorAxis),
			dot(p, spinAxis)/flattening);
	float diskRadius = length(diskPoint)*SQRT_27;
	float diskSoftness = max(0.065, 1.25/max(uShadowRadius, 1.0));
	float primaryDisk = annulus(diskRadius, uDiskInner, uDiskOuter,
			diskSoftness);
	// A bounded secondary image close to the critical region.
	float secondaryRadius = diskRadius*(1.0
			+ 0.18/(max(radial, boundary + 0.04)));
	float secondaryDisk = annulus(secondaryRadius, uDiskInner,
			uDiskOuter, diskSoftness)*0.34;
	float diskMask = max(primaryDisk, secondaryDisk)*(1.0 - shadow);

	float safeDiskRadius = max(diskRadius, uDiskInner + 0.0001);
	float zeroTorque = max(0.0,
			1.0 - sqrt(uDiskInner/safeDiskRadius));
	float temperature = pow(max(zeroTorque
			/(safeDiskRadius*safeDiskRadius*safeDiskRadius), 0.0), 0.25);
	float normalizedHeat = clamp(temperature
			*pow(max(uDiskInner, 1.0), 0.75)*2.6, 0.0, 1.0);

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
	float boundedNoise = 0.80 + 0.13*sin(diskRadius*15.0
			+ diskAngle*6.0 - phase)
			+ 0.07*sin(diskRadius*31.0 - diskAngle*3.0 + phase*0.63);
	float emission = clamp(diskMask*uAccretionRate*normalizedHeat
			*boundedNoise*beaming, 0.0, 2.7);
	vec3 redShift = vec3(1.0, 0.22, 0.045);
	vec3 blueShift = vec3(0.38, 0.72, 1.0);
	vec3 diskColor = mix(redShift, blueShift,
			clamp(0.5 + 0.30*(doppler - 1.0), 0.0, 1.0));
	color += diskColor*emission;

	float criticalBand = (1.0 - smoothstep(edgeWidth*0.7,
			edgeWidth*4.2, abs(radial - boundary)))
			*uAccretionRate;
	color += vec3(1.0, 0.65, 0.28)*criticalBand*0.92;
	color = mix(color, vec3(0.0), shadow);
	color = mix(originalColor, color, clamp(uAlpha, 0.0, 1.0));
	gl_FragColor = vec4(clamp(color, 0.0, 4.0), 1.0);
}
