package zmaster587.advancedRocketry.client.render.blackhole;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import zmaster587.advancedRocketry.api.dimension.solar.BlackHoleProperties;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;

/**
 * Immutable, projected input for one astronomical black hole.
 *
 * <p>Sky renderers submit geometry while their own model-view transforms are
 * active.  This class freezes the resulting screen-space center, radius and
 * projected spin axis, so the manager can render every hole later after one
 * shared scene-color capture.</p>
 */
public final class BlackHoleView {

	private static final double SQRT_27 = Math.sqrt(27D);
	private static final double EPSILON = 1.0E-6D;
	private static final float MAX_LENS_EINSTEIN_RADIUS = 1.46F;
	private static final float MAX_DISK_ASPECT_RATIO = 0.075F;

	private final String bodyId;
	private final float centerX;
	private final float centerY;
	private final float screenRadius;
	private final float depth;
	private final float projectedAxisX;
	private final float projectedAxisY;
	private final float viewInclinationRadians;
	private final float mass;
	private final float spin;
	private final float accretionRate;
	private final float diskInnerRadiusOverM;
	private final float diskOuterRadiusOverM;
	private final float alpha;
	private final float deterministicPhase;

	private BlackHoleView(String bodyId, float centerX, float centerY,
			float screenRadius, float depth, float projectedAxisX,
			float projectedAxisY, float viewInclinationRadians, float mass,
			float spin, float accretionRate, float diskInnerRadiusOverM,
			float diskOuterRadiusOverM, float alpha,
			float deterministicPhase) {
		this.bodyId = bodyId;
		this.centerX = centerX;
		this.centerY = centerY;
		this.screenRadius = screenRadius;
		this.depth = depth;
		this.projectedAxisX = projectedAxisX;
		this.projectedAxisY = projectedAxisY;
		this.viewInclinationRadians = viewInclinationRadians;
		this.mass = mass;
		this.spin = spin;
		this.accretionRate = accretionRate;
		this.diskInnerRadiusOverM = diskInnerRadiusOverM;
		this.diskOuterRadiusOverM = diskOuterRadiusOverM;
		this.alpha = alpha;
		this.deterministicPhase = deterministicPhase;
	}

	static BlackHoleView projectPlanetary(StellarBody body, double halfSize,
			float alpha, BlackHoleRenderContext context) {
		return project(body, 0D, 100D, 0D,
				halfSize, 0D, 0D,
				0D, 0D, halfSize, alpha, context);
	}

	static BlackHoleView projectBillboard(StellarBody body, double x,
			double y, double z, double halfSize, float alpha,
			BlackHoleRenderContext context) {
		double length = Math.sqrt(x*x + y*y + z*z);
		if(!isFinite(length) || length < EPSILON)
			return null;

		double viewX = x/length;
		double viewY = y/length;
		double viewZ = z/length;
		double rightX;
		double rightY = 0D;
		double rightZ;
		if(Math.abs(viewX) < EPSILON && Math.abs(viewZ) < EPSILON) {
			rightX = 1D;
			rightZ = 0D;
		}
		else {
			double rightLength = Math.sqrt(viewZ*viewZ + viewX*viewX);
			rightX = viewZ/rightLength;
			rightZ = -viewX/rightLength;
		}

		double upX = -rightZ*viewY;
		double upY = rightZ*viewX - rightX*viewZ;
		double upZ = rightX*viewY;
		return project(body, x, y, z,
				rightX*halfSize, rightY*halfSize, rightZ*halfSize,
				upX*halfSize, upY*halfSize, upZ*halfSize, alpha, context);
	}

	private static BlackHoleView project(StellarBody body, double centerX,
			double centerY, double centerZ, double basisXX, double basisXY,
			double basisXZ, double basisYX, double basisYY, double basisYZ,
			float alpha, BlackHoleRenderContext context) {
		if(body == null || !body.isBlackHole() || !context.hasDrawableViewport()
				|| !isFinite(centerX) || !isFinite(centerY)
				|| !isFinite(centerZ))
			return null;

		BlackHoleProperties properties = body.getBlackHoleProperties();
		if(properties == null)
			return null;

		double visualScale = properties.getVisualScale();
		if(!isFinite(visualScale) || visualScale <= 0D)
			visualScale = 1D;
		basisXX *= visualScale;
		basisXY *= visualScale;
		basisXZ *= visualScale;
		basisYX *= visualScale;
		basisYY *= visualScale;
		basisYZ *= visualScale;

		FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
		FloatBuffer projection = BufferUtils.createFloatBuffer(16);
		IntBuffer viewport = BufferUtils.createIntBuffer(4);
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

		/*
		 * GLU.gluProject may return finite window coordinates for a point
		 * behind the eye, and projected tangent-plane edges approach
		 * infinity as they cross clip W=0.  That previously turned a
		 * behind-camera black hole into a full-screen proxy.  Reject the
		 * center by its real clip W and derive a bounded angular radius from
		 * eye-space lengths instead of subtracting two perspective-divided
		 * edge coordinates.
		 */
		double cameraCenterX = transform(modelView, 0, centerX, centerY,
				centerZ, 1D);
		double cameraCenterY = transform(modelView, 1, centerX, centerY,
				centerZ, 1D);
		double cameraCenterZ = transform(modelView, 2, centerX, centerY,
				centerZ, 1D);
		double centerClipW = transform(projection, 3, cameraCenterX,
				cameraCenterY, cameraCenterZ, 1D);
		if(!isFinite(cameraCenterX) || !isFinite(cameraCenterY)
				|| !isFinite(cameraCenterZ) || !isFinite(centerClipW)
				|| centerClipW <= EPSILON)
			return null;

		double cameraBasisXX = transform(modelView, 0, basisXX, basisXY,
				basisXZ, 0D);
		double cameraBasisXY = transform(modelView, 1, basisXX, basisXY,
				basisXZ, 0D);
		double cameraBasisXZ = transform(modelView, 2, basisXX, basisXY,
				basisXZ, 0D);
		double cameraBasisYX = transform(modelView, 0, basisYX, basisYY,
				basisYZ, 0D);
		double cameraBasisYY = transform(modelView, 1, basisYX, basisYY,
				basisYZ, 0D);
		double cameraBasisYZ = transform(modelView, 2, basisYX, basisYY,
				basisYZ, 0D);
		double centerDistance = length(cameraCenterX, cameraCenterY,
				cameraCenterZ);
		double basisXLength = length(cameraBasisXX, cameraBasisXY,
				cameraBasisXZ);
		double basisYLength = length(cameraBasisYX, cameraBasisYY,
				cameraBasisYZ);
		double angularTangent = Math.max(basisXLength, basisYLength)
				/centerDistance;
		double focalPixelsX = Math.abs(projection.get(0))
				*Math.max(1, viewport.get(2))*0.5D;
		double focalPixelsY = Math.abs(projection.get(5))
				*Math.max(1, viewport.get(3))*0.5D;
		double radius = angularTangent*Math.max(focalPixelsX, focalPixelsY);
		if(!isFinite(centerDistance) || centerDistance < EPSILON
				|| !isFinite(angularTangent)
				|| !isFinite(focalPixelsX) || !isFinite(focalPixelsY))
			return null;

		float[] center = projectPoint(centerX, centerY, centerZ, modelView,
				projection, viewport);
		if(center == null || center[2] < 0F || center[2] > 1F)
			return null;
		if(!isFinite(radius) || radius < 0.25D)
			return null;

		double inclination = Math.toRadians(
				properties.getSpinAxisInclinationDeg());
		double yaw = Math.toRadians(properties.getSpinAxisYawDeg());
		double axisX = Math.sin(inclination)*Math.sin(yaw);
		double axisY = Math.cos(inclination);
		double axisZ = Math.sin(inclination)*Math.cos(yaw);

		double cameraAxisX = transform(modelView, 0, axisX, axisY, axisZ,
				0D);
		double cameraAxisY = transform(modelView, 1, axisX, axisY, axisZ,
				0D);
		double cameraAxisZ = transform(modelView, 2, axisX, axisY, axisZ,
				0D);
		double axisClipW = transform(projection, 3,
				cameraCenterX + cameraAxisX, cameraCenterY + cameraAxisY,
				cameraCenterZ + cameraAxisZ, 1D);
		float[] axisEnd = !isFinite(axisClipW) || axisClipW <= EPSILON
				? null : projectPoint(centerX + axisX, centerY + axisY,
						centerZ + axisZ, modelView, projection, viewport);
		float projectedX = axisEnd == null ? 0F : axisEnd[0] - center[0];
		float projectedY = axisEnd == null ? 1F : axisEnd[1] - center[1];
		float projectedLength = (float)Math.sqrt(
				projectedX*projectedX + projectedY*projectedY);
		if(!isFinite(projectedLength) || projectedLength < 1.0E-4F) {
			double fallbackAngle = deterministicUnit(body.getId())*Math.PI*2D;
			projectedX = (float)Math.cos(fallbackAngle);
			projectedY = (float)Math.sin(fallbackAngle);
		}
		else {
			projectedX /= projectedLength;
			projectedY /= projectedLength;
		}

		float observedInclination = calculateObservedInclination(modelView,
				centerX, centerY, centerZ, axisX, axisY, axisZ);
		double spin = clamp(properties.getSpin(), 0D,
				BlackHoleProperties.MAX_SPIN);
		double mass = Math.max(0.01D, properties.getMass());
		double accretion = clamp(properties.getAccretionRate(), 0D, 1D);
		double inner = properties.getEffectiveDiskInnerRadiusOverM();
		double outer = properties.getEffectiveDiskOuterRadiusOverM();
		if(!isFinite(inner) || inner <= 0D)
			inner = 6D;
		if(!isFinite(outer) || outer <= inner)
			outer = inner + 0.001D;

		return new BlackHoleView("star:" + body.getId(), center[0], center[1],
				(float)Math.min(radius, 16384D), center[2], projectedX,
				projectedY, observedInclination, (float)Math.min(mass, 1.0E6D),
				(float)spin, (float)accretion, (float)Math.min(inner, 64D),
				(float)Math.min(outer, 128D), clamp(alpha, 0F, 1F),
				(float)deterministicUnit(body.getId()));
	}

	private static float calculateObservedInclination(FloatBuffer modelView,
			double centerX, double centerY, double centerZ, double axisX,
			double axisY, double axisZ) {
		double cameraX = transform(modelView, 0, centerX, centerY, centerZ, 1D);
		double cameraY = transform(modelView, 1, centerX, centerY, centerZ, 1D);
		double cameraZ = transform(modelView, 2, centerX, centerY, centerZ, 1D);
		double transformedAxisX = transform(modelView, 0, axisX, axisY,
				axisZ, 0D);
		double transformedAxisY = transform(modelView, 1, axisX, axisY,
				axisZ, 0D);
		double transformedAxisZ = transform(modelView, 2, axisX, axisY,
				axisZ, 0D);
		double cameraLength = Math.sqrt(cameraX*cameraX + cameraY*cameraY
				+ cameraZ*cameraZ);
		double axisLength = Math.sqrt(transformedAxisX*transformedAxisX
				+ transformedAxisY*transformedAxisY
				+ transformedAxisZ*transformedAxisZ);
		if(cameraLength < EPSILON || axisLength < EPSILON
				|| !isFinite(cameraLength) || !isFinite(axisLength))
			return 0F;
		double dot = (-cameraX*transformedAxisX - cameraY*transformedAxisY
				- cameraZ*transformedAxisZ)/(cameraLength*axisLength);
		return (float)Math.acos(clamp(dot, -1D, 1D));
	}

	private static double transform(FloatBuffer matrix, int row, double x,
			double y, double z, double w) {
		return matrix.get(row)*x + matrix.get(4 + row)*y
				+ matrix.get(8 + row)*z + matrix.get(12 + row)*w;
	}

	private static float[] projectPoint(double x, double y, double z,
			FloatBuffer modelView, FloatBuffer projection, IntBuffer viewport) {
		FloatBuffer result = BufferUtils.createFloatBuffer(3);
		modelView.rewind();
		projection.rewind();
		viewport.rewind();
		if(!GLU.gluProject((float)x, (float)y, (float)z, modelView,
				projection, viewport, result))
			return null;
		float resultX = result.get(0);
		float resultY = result.get(1);
		float resultZ = result.get(2);
		return isFinite(resultX) && isFinite(resultY) && isFinite(resultZ)
				? new float[] {resultX, resultY, resultZ} : null;
	}

	private static double length(double x, double y, double z) {
		return Math.sqrt(x*x + y*y + z*z);
	}

	private static double deterministicUnit(int id) {
		int value = id;
		value ^= value >>> 16;
		value *= 0x7feb352d;
		value ^= value >>> 15;
		value *= 0x846ca68b;
		value ^= value >>> 16;
		return (value & 0x7fffffff)/(double)Integer.MAX_VALUE;
	}

	private static boolean isFinite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static float clamp(float value, float minimum, float maximum) {
		if(Float.isNaN(value) || Float.isInfinite(value))
			return minimum;
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double clamp(double value, double minimum,
			double maximum) {
		if(Double.isNaN(value) || Double.isInfinite(value))
			return minimum;
		return Math.max(minimum, Math.min(maximum, value));
	}

	public String getBodyId() {
		return bodyId;
	}

	public float getCenterX() {
		return centerX;
	}

	public float getCenterY() {
		return centerY;
	}

	public float getScreenRadius() {
		return screenRadius;
	}

	public float getProxyRadius() {
		float diskExtent = diskOuterRadiusOverM/(float)SQRT_27;
		float lensedExtent = 0.5F*(diskExtent
				+ (float)Math.sqrt(diskExtent*diskExtent
						+ 4F*MAX_LENS_EINSTEIN_RADIUS
								*MAX_LENS_EINSTEIN_RADIUS));
		return screenRadius*Math.max(1.55F,
				Math.min(5.6F, lensedExtent + 0.12F
						+ diskExtent*MAX_DISK_ASPECT_RATIO));
	}

	public float getDepth() {
		return depth;
	}

	public float getProjectedAxisX() {
		return projectedAxisX;
	}

	public float getProjectedAxisY() {
		return projectedAxisY;
	}

	public float getViewInclinationRadians() {
		return viewInclinationRadians;
	}

	public float getMass() {
		return mass;
	}

	public float getSpin() {
		return spin;
	}

	public float getAccretionRate() {
		return accretionRate;
	}

	public float getDiskInnerRadiusOverM() {
		return diskInnerRadiusOverM;
	}

	public float getDiskOuterRadiusOverM() {
		return diskOuterRadiusOverM;
	}

	public float getAlpha() {
		return alpha;
	}

	public float getDeterministicPhase() {
		return deterministicPhase;
	}

	public boolean isVisibleIn(BlackHoleRenderContext context) {
		float radius = getProxyRadius();
		return centerX + radius >= context.getViewportX()
				&& centerX - radius <= context.getViewportX()
						+ context.getViewportWidth()
				&& centerY + radius >= context.getViewportY()
				&& centerY - radius <= context.getViewportY()
						+ context.getViewportHeight();
	}
}
