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
	float strands = 0.72
			+ 0.16*sin(angle*11.0 + radius*2.7
					- differentialPhase*42.0)
			+ 0.09*sin(angle*23.0 - radius*5.1
					+ differentialPhase*27.0)
			+ 0.05*sin(angle*47.0 + radius*1.3
					- differentialPhase*15.0);
	return clamp(strands, 0.24, 1.08);
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
			*pow(max(uDiskInner, 1.0), 0.75)*2.65);

	float beta = min(0.85, inversesqrt(max(safeRadius, 1.0)));
	float gamma = inversesqrt(max(0.001, 1.0 - beta*beta));
	float betaLos = clamp(beta*sin(uInclination)*cos(angle)
			*imageParity, -0.849, 0.849);
	float doppler = clamp(1.0/(gamma*(1.0 - betaLos)),
			0.25, 4.0);
	float beaming = clamp(doppler*doppler*doppler, 0.24, 4.2);

	float outerFade = 1.0
			- smoothstep(0.68, 1.0, radialFraction);
	float rawEmission = mask*uAccretionRate
			*(0.24 + 0.92*heat)*outerFade
			*diskStructure(safeRadius, angle, phase)*beaming;
	float emission = (1.0 - exp(-rawEmission*1.35))*2.05;

	vec3 amber = vec3(1.0, 0.30, 0.055);
	vec3 gold = vec3(1.0, 0.70, 0.28);
	vec3 hot = vec3(1.0, 0.96, 0.82);
	vec3 color = mix(amber, gold, smoothstep(0.05, 0.50, heat));
	color = mix(color, hot, smoothstep(0.38, 1.0, heat));
	float approaching = saturate(doppler - 1.0);
	float receding = saturate(1.0 - doppler);
	color = mix(color, vec3(0.92, 0.97, 1.0),
			approaching*0.16);
	color = mix(color, vec3(1.0, 0.42, 0.10),
			receding*0.14);
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
	float edgeWidth = max(1.15/max(uShadowRadius, 1.0), 0.0045);
	float shadow = 1.0 - smoothstep(boundary - edgeWidth,
			boundary + edgeWidth, radial);

	/*
	 * Weak-field background deflection.  It remains bounded and fades out
	 * before the proxy edge so an off-screen sample cannot smear a viewport
	 * border across the disk.
	 */
	vec2 radialDirection = radial > 0.0001
			? p/radial : vec2(1.0, 0.0);
	vec2 tangentDirection = vec2(-radialDirection.y, radialDirection.x);
	float impact = max(radial, boundary + 0.035);
	float inverseImpact = 1.0/impact;
	float side = sign(dot(p, majorAxis));
	float radialDeflection = min(0.36,
			0.120*inverseImpact + 0.040*inverseImpact*inverseImpact);
	float frameDragging = clamp(side*0.042*uSpin
			*inverseImpact*inverseImpact, -0.10, 0.10);
	float lensWindow = 1.0 - smoothstep(boundary + 1.65,
			max(boundary + 1.75, proxyLimit), radial);
	vec2 sampleOffset = (radialDirection*radialDeflection
			+ tangentDirection*frameDragging)*lensWindow
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
	float flattening = max(0.070, abs(cosInclination));
	float pixelSoftness = SQRT_27*1.55/max(uShadowRadius, 1.0);
	float diskSoftness = min(max(0.055, pixelSoftness),
			max(0.0004, (uDiskOuter - uDiskInner)*0.45));
	float phase = uWorldTime*0.012 + uBodyPhase*TWO_PI;
	float inclinationBlend = smoothstep(0.08, 0.30, sinInclination);

	/*
	 * Inverse point-lens mapping.  Evaluating the inclined source disk in
	 * this coordinate system naturally produces the primary far-side image
	 * and the parity-flipped arc near the critical curve.
	 */
	float r2 = max(dot(q, q), 0.0025);
	float einsteinRadius = (1.10 + 0.20*sinInclination)
			*(0.85 + 0.15*boundary);
	float lensScale = 1.0
			- einsteinRadius*einsteinRadius/r2;
	vec2 source = q*lensScale;
	source += vec2(-q.y, q.x)
			*(0.030*uSpin/(r2 + 0.20));
	vec2 sourceDiskPoint = vec2(source.x,
			source.y/flattening);
	float sourceDiskRadius = length(sourceDiskPoint)*SQRT_27;
	float sourceDiskAngle = atan(sourceDiskPoint.y,
			sourceDiskPoint.x);
	float farGate = smoothstep(-0.12, 0.075,
			source.y*viewSign);
	float arcLift = mix(0.62, 1.0,
			smoothstep(0.025, 0.45, abs(q.y)));
	float lensedDiskMask = annulus(sourceDiskRadius,
			uDiskInner, uDiskOuter, diskSoftness)
			*farGate*arcLift*inclinationBlend;
	float imageParity = lensScale < 0.0 ? -1.0 : 1.0;
	vec3 farDisk = diskRadiance(sourceDiskRadius,
			sourceDiskAngle, lensedDiskMask, imageParity, phase);
	color += farDisk;

	/* Apparent shadow occludes the far side and the captured background. */
	color = mix(color, vec3(0.0), shadow);

	/*
	 * The direct near side is composited after the shadow.  This ordering is
	 * what lets the foreground band cross the lower silhouette instead of
	 * being erased into a perfectly isolated black oval.
	 */
	vec2 directDiskPoint = vec2(q.x, q.y/flattening);
	float directDiskRadius = length(directDiskPoint)*SQRT_27;
	float directDiskAngle = atan(directDiskPoint.y,
			directDiskPoint.x);
	float splitNearGate = smoothstep(-0.12, 0.075,
			-q.y*viewSign);
	float nearGate = mix(1.0 - shadow, splitNearGate,
			inclinationBlend);
	float directMask = annulus(directDiskRadius,
			uDiskInner, uDiskOuter, diskSoftness)*nearGate;
	float foregroundLimb = mix(1.0,
			smoothstep(0.52, 0.94, radial), shadow);
	directMask *= foregroundLimb;
	color += diskRadiance(directDiskRadius, directDiskAngle,
			directMask, 1.0, phase);

	float criticalBand = (1.0 - smoothstep(edgeWidth*0.65,
			edgeWidth*2.4, abs(radial - boundary)))
			*sqrt(max(uAccretionRate, 0.0));
	float ringVariation = 0.58 + 0.42*saturate(
			length(farDisk)*0.40);
	float ringDoppler = 0.72 + 0.28*saturate(
			0.5 + 0.5*dot(normalize(p + vec2(0.000001)), majorAxis));
	color += vec3(1.0, 0.88, 0.68)
			*criticalBand*ringVariation*ringDoppler;

	float proxyFeather = max(0.08,
			3.0/max(uShadowRadius, 1.0));
	float proxyFade = 1.0 - smoothstep(
			proxyLimit - proxyFeather, proxyLimit, radial);
	color = mix(originalColor, color,
			saturate(uAlpha)*proxyFade);
	gl_FragColor = vec4(clamp(color, 0.0, 4.0), 1.0);
}
