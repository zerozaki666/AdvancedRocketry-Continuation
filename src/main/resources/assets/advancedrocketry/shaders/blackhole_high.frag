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

float saturate(float value) {
	return clamp(value, 0.0, 1.0);
}

float shadowBoundary(vec2 p) {
	vec2 axis = normalize(uProjectedSpinAxis + vec2(0.000001, 0.0));
	vec2 majorAxis = vec2(-axis.y, axis.x);
	vec2 curvePoint = vec2(dot(p, majorAxis), dot(p, axis));
	float angle = atan(curvePoint.y, curvePoint.x)/TWO_PI + 0.5;
	return texture2D(uShadowBoundary,
			vec2(fract(angle + 0.001953125), 0.5)).r;
}

float annulus(float radius, float innerRadius, float outerRadius,
		float softness) {
	return smoothstep(innerRadius, innerRadius + softness, radius)
			*(1.0 - smoothstep(outerRadius - softness, outerRadius,
			radius));
}

float diskStructure(float radius, float angle, float phase) {
	float differentialPhase = phase
			/pow(max(radius, 1.0), 1.5);
	float shear = angle - differentialPhase*18.0;
	float strands = 0.70
			+ 0.15*sin(shear*11.0 + radius*2.7)
			+ 0.09*sin(shear*23.0 - radius*5.1
					+ sin(shear*3.0)*0.75)
			+ 0.055*sin(shear*47.0 + radius*1.3)
			+ 0.035*sin(shear*79.0 - radius*8.7);
	return clamp(strands, 0.20, 1.10);
}

vec3 diskRadiance(float radius, float angle, float mask,
		float imageParity, float phase) {
	if(mask <= 0.0001)
		return vec3(0.0);

	float safeRadius = max(radius, uDiskInner + 0.0001);
	float radialFraction = saturate((safeRadius - uDiskInner)
			/max(uDiskOuter - uDiskInner, 0.0001));
	float zeroTorque = max(0.0,
			1.0 - sqrt(uDiskInner/safeRadius));
	float temperature = pow(max(zeroTorque
			/(safeRadius*safeRadius*safeRadius), 0.0), 0.25);
	float heat = saturate(temperature
			*pow(max(uDiskInner, 1.0), 0.75)*2.75);

	float beta = min(0.85, inversesqrt(max(safeRadius, 1.0)));
	float gamma = inversesqrt(max(0.001, 1.0 - beta*beta));
	float betaLos = clamp(beta*sin(uInclination)*cos(angle)
			*imageParity, -0.849, 0.849);
	float doppler = clamp(1.0/(gamma*(1.0 - betaLos)),
			0.25, 4.0);
	float beaming = clamp(doppler*doppler*doppler, 0.22, 4.6);

	float outerFade = 1.0
			- smoothstep(0.70, 1.0, radialFraction);
	float rawEmission = mask*uAccretionRate
			*(0.22 + 0.98*heat)*outerFade
			*diskStructure(safeRadius, angle, phase)*beaming;
	float emission = (1.0 - exp(-rawEmission*1.42))*2.15;

	vec3 amber = vec3(1.0, 0.28, 0.045);
	vec3 gold = vec3(1.0, 0.72, 0.30);
	vec3 hot = vec3(1.0, 0.97, 0.85);
	vec3 color = mix(amber, gold, smoothstep(0.04, 0.48, heat));
	color = mix(color, hot, smoothstep(0.34, 1.0, heat));
	float approaching = saturate(doppler - 1.0);
	float receding = saturate(1.0 - doppler);
	color = mix(color, vec3(0.91, 0.97, 1.0),
			approaching*0.18);
	color = mix(color, vec3(1.0, 0.40, 0.085),
			receding*0.15);
	return color*emission;
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

	vec2 spinAxis = normalize(uProjectedSpinAxis
			+ vec2(0.000001, 0.0));
	vec2 majorAxis = vec2(-spinAxis.y, spinAxis.x);
	vec2 q = vec2(dot(p, majorAxis), dot(p, spinAxis));
	float boundary = shadowBoundary(p);
	float edgeWidth = max(1.0/max(uShadowRadius, 1.0), 0.0035);
	float shadow = 1.0 - smoothstep(boundary - edgeWidth,
			boundary + edgeWidth, radial);

	/*
	 * Bounded iterative deflection for the captured celestial background.
	 * The compile-time upper limit keeps GLSL 1.20 drivers happy; the
	 * uniform early exit makes the configured step count a real cost limit.
	 */
	vec2 ray = p;
	for(int stepIndex = 0; stepIndex < MAX_STEPS; ++stepIndex) {
		if(float(stepIndex) >= uSteps)
			break;
		float rayRadius = max(length(ray), boundary + 0.035);
		vec2 rayDirection = ray/rayRadius;
		vec2 tangent = vec2(-rayDirection.y, rayDirection.x);
		float radialStep = min(0.015,
				0.0060/(rayRadius*rayRadius));
		float spinStep = clamp(uSpin*0.0019
				/(rayRadius*rayRadius), -0.0032, 0.0032);
		ray += rayDirection*radialStep + tangent*spinStep;
	}
	float lensWindow = 1.0 - smoothstep(boundary + 1.85,
			max(boundary + 1.95, proxyLimit), radial);
	vec2 sampleOffset = (ray - p)*lensWindow
			*uShadowRadius/viewportSize;
	vec2 requestedUv = originalUv + sampleOffset;
	float sampleValid = step(0.0, requestedUv.x)
			*step(requestedUv.x, 1.0)
			*step(0.0, requestedUv.y)
			*step(requestedUv.y, 1.0);
	vec3 originalColor = texture2D(uSceneColor, originalUv).rgb;
	vec3 lensedColor = texture2D(uSceneColor,
			clamp(requestedUv, vec2(0.001), vec2(0.999))).rgb;
	vec3 color = mix(originalColor, lensedColor,
			lensWindow*sampleValid);

	float cosInclination = cos(uInclination);
	float sinInclination = abs(sin(uInclination));
	float viewSign = cosInclination >= 0.0 ? 1.0 : -1.0;
	float flattening = max(0.060, abs(cosInclination));
	float pixelSoftness = SQRT_27*1.35/max(uShadowRadius, 1.0);
	float diskSoftness = min(max(0.045, pixelSoftness),
			max(0.0004, (uDiskOuter - uDiskInner)*0.45));
	float phase = uWorldTime*0.012 + uBodyPhase*TWO_PI;
	float inclinationBlend = smoothstep(0.08, 0.30, sinInclination);

	/*
	 * The source-plane disk is evaluated through the inverse point-lens
	 * equation.  Outer and parity-flipped image branches emerge from the same
	 * bounded mapping; frame dragging adds only a finite tangential offset.
	 */
	float r2 = max(dot(q, q), 0.0020);
	float einsteinRadius = (1.12 + 0.22*sinInclination)
			*(0.85 + 0.15*boundary);
	float lensScale = 1.0
			- einsteinRadius*einsteinRadius/r2;
	vec2 source = q*lensScale;
	source += vec2(-q.y, q.x)
			*(0.040*uSpin/(r2 + 0.18));
	vec2 sourceDiskPoint = vec2(source.x,
			source.y/flattening);
	float sourceDiskRadius = length(sourceDiskPoint)*SQRT_27;
	float sourceDiskAngle = atan(sourceDiskPoint.y,
			sourceDiskPoint.x);
	float farGate = smoothstep(-0.11, 0.065,
			source.y*viewSign);
	float arcLift = mix(0.56, 1.0,
			smoothstep(0.018, 0.42, abs(q.y)));
	float lensedDiskMask = annulus(sourceDiskRadius,
			uDiskInner, uDiskOuter, diskSoftness)
			*farGate*arcLift*inclinationBlend;
	float imageParity = lensScale < 0.0 ? -1.0 : 1.0;
	vec3 farDisk = diskRadiance(sourceDiskRadius,
			sourceDiskAngle, lensedDiskMask, imageParity, phase);
	color += farDisk;

	color = mix(color, vec3(0.0), shadow);

	vec2 directDiskPoint = vec2(q.x, q.y/flattening);
	float directDiskRadius = length(directDiskPoint)*SQRT_27;
	float directDiskAngle = atan(directDiskPoint.y,
			directDiskPoint.x);
	float splitNearGate = smoothstep(-0.11, 0.065,
			-q.y*viewSign);
	float nearGate = mix(1.0 - shadow, splitNearGate,
			inclinationBlend);
	float directMask = annulus(directDiskRadius,
			uDiskInner, uDiskOuter, diskSoftness)*nearGate;
	float foregroundLimb = mix(1.0,
			smoothstep(0.50, 0.93, radial), shadow);
	directMask *= foregroundLimb;
	color += diskRadiance(directDiskRadius, directDiskAngle,
			directMask, 1.0, phase);

	float criticalBand = (1.0 - smoothstep(edgeWidth*0.55,
			edgeWidth*2.1, abs(radial - boundary)))
			*sqrt(max(uAccretionRate, 0.0));
	float ringVariation = 0.52 + 0.48*saturate(
			length(farDisk)*0.42);
	float ringDoppler = 0.70 + 0.30*saturate(
			0.5 + 0.5*dot(normalize(p + vec2(0.000001)), majorAxis));
	color += vec3(1.0, 0.90, 0.72)
			*criticalBand*ringVariation*ringDoppler;

	float proxyFeather = max(0.07,
			3.0/max(uShadowRadius, 1.0));
	float proxyFade = 1.0 - smoothstep(
			proxyLimit - proxyFeather, proxyLimit, radial);
	color = mix(originalColor, color,
			saturate(uAlpha)*proxyFade);
	gl_FragColor = vec4(clamp(color, 0.0, 4.0), 1.0);
}
