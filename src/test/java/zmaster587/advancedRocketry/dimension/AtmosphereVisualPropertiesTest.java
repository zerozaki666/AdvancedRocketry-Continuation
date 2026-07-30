package zmaster587.advancedRocketry.dimension;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.minecraft.nbt.NBTTagCompound;
import zmaster587.advancedRocketry.dimension.AtmosphereVisualProperties.CloudLayerMode;

public class AtmosphereVisualPropertiesTest {

	private static final double EPSILON = 1.0e-9D;

	@Test
	public void emptyPropertiesDoNotWriteAnOverrideBlock() {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		NBTTagCompound child = new NBTTagCompound();
		properties.writeToNBT(child);
		assertTrue(child.hasNoTags());

		NBTTagCompound parent = new NBTTagCompound();
		AtmosphereVisualProperties restored =
				AtmosphereVisualProperties.readFromParentNBT(
						parent, "test");
		assertFalse(restored.hasOverrides());
	}

	@Test
	public void nbtRoundTripPreservesPresenceAndValues() {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		properties.setPlanetRadiusKm(7000D);
		properties.setAtmosphereHeightKm(140D);
		properties.setRayleighColor(0.25F, 0.5F, 1F);
		properties.setRayleighStrength(1.25D);
		properties.setRayleighScaleHeightKm(8D);
		properties.setMieColor(1F, 0.6F, 0.2F);
		properties.setMieStrength(0.8D);
		properties.setMieScaleHeightKm(1.5D);
		properties.setMieAnisotropy(0.88D);
		properties.setAbsorptionColor(0.1F, 0.2F, 0.3F);
		properties.setAbsorptionStrength(0.4D);
		properties.setAbsorptionCenterKm(30D);
		properties.setAbsorptionWidthKm(12D);
		properties.setMultipleScatteringStrength(0.25D);
		properties.setSunIntensityMultiplier(1.2D);
		properties.setExposure(1.5D);
		properties.setCloudLayerMode(CloudLayerMode.DISABLED);

		NBTTagCompound child = new NBTTagCompound();
		properties.writeToNBT(child);
		AtmosphereVisualProperties restored =
				AtmosphereVisualProperties.readFromNBT(child, "test");

		assertTrue(restored.hasOverrides());
		assertTrue(restored.hasPlanetRadiusKm());
		assertEquals(7000D, restored.getPlanetRadiusKm(), EPSILON);
		assertEquals(140D, restored.getAtmosphereHeightKm(), EPSILON);
		assertArrayEquals(new float[] {0.25F, 0.5F, 1F},
				restored.getRayleighColor(), 0F);
		assertEquals(1.25D, restored.getRayleighStrength(), EPSILON);
		assertEquals(8D, restored.getRayleighScaleHeightKm(), EPSILON);
		assertArrayEquals(new float[] {1F, 0.6F, 0.2F},
				restored.getMieColor(), 0F);
		assertEquals(0.8D, restored.getMieStrength(), EPSILON);
		assertEquals(1.5D, restored.getMieScaleHeightKm(), EPSILON);
		assertEquals(0.88D, restored.getMieAnisotropy(), EPSILON);
		assertArrayEquals(new float[] {0.1F, 0.2F, 0.3F},
				restored.getAbsorptionColor(), 0F);
		assertEquals(0.4D, restored.getAbsorptionStrength(), EPSILON);
		assertEquals(30D, restored.getAbsorptionCenterKm(), EPSILON);
		assertEquals(12D, restored.getAbsorptionWidthKm(), EPSILON);
		assertEquals(0.25D,
				restored.getMultipleScatteringStrength(), EPSILON);
		assertEquals(1.2D,
				restored.getSunIntensityMultiplier(), EPSILON);
		assertEquals(1.5D, restored.getExposure(), EPSILON);
		assertEquals(CloudLayerMode.DISABLED,
				restored.getCloudLayerMode());
	}

	@Test
	public void missingParentBlockProducesFreshEmptyProperties() {
		AtmosphereVisualProperties source =
				new AtmosphereVisualProperties();
		source.setExposure(2D);
		NBTTagCompound child = new NBTTagCompound();
		source.writeToNBT(child);
		NBTTagCompound parent = new NBTTagCompound();
		parent.setTag(AtmosphereVisualProperties.NBT_KEY, child);

		AtmosphereVisualProperties restored =
				AtmosphereVisualProperties.readFromParentNBT(
						parent, "test");
		assertTrue(restored.hasExposure());
		parent.removeTag(AtmosphereVisualProperties.NBT_KEY);
		restored = AtmosphereVisualProperties.readFromParentNBT(
				parent, "test");
		assertFalse(restored.hasOverrides());
	}

	@Test
	public void copyAndSnapshotDefensivelyOwnColorArrays() {
		float[] source = {0.2F, 0.4F, 0.8F};
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		properties.setRayleighColor(source);
		source[0] = 1F;

		float[] firstRead = properties.getRayleighColor();
		assertEquals(0.2F, firstRead[0], 0F);
		firstRead[1] = 1F;
		assertEquals(0.4F, properties.getRayleighColor()[1], 0F);

		AtmosphereVisualProperties copy = properties.copy();
		assertNotSame(properties.getRayleighColor(),
				copy.getRayleighColor());
		copy.setRayleighColor(1F, 0F, 0F);
		assertEquals(0.2F, properties.getRayleighColor()[0], 0F);

		AtmosphereVisualProperties.Snapshot snapshot =
				properties.snapshot();
		float[] snapshotColor = snapshot.getRayleighColor();
		snapshotColor[2] = 0F;
		assertEquals(0.8F, snapshot.getRayleighColor()[2], 0F);
	}

	@Test
	public void invalidFieldsFallBackIndependently() {
		NBTTagCompound child = new NBTTagCompound();
		child.setInteger("version", 1);
		child.setDouble("planetRadiusKm", Double.NaN);
		child.setDouble("atmosphereHeightKm", 100D);
		child.setString("cloudLayerMode", "unknown");

		AtmosphereVisualProperties restored =
				AtmosphereVisualProperties.readFromNBT(child, "test");
		assertFalse(restored.hasPlanetRadiusKm());
		assertTrue(restored.hasAtmosphereHeightKm());
		assertEquals(100D, restored.getAtmosphereHeightKm(), EPSILON);
		assertFalse(restored.hasCloudLayerMode());
	}

	@Test
	public void partialLayerOverridesSurviveWithoutExplicitHeight() {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		properties.setRayleighScaleHeightKm(150D);
		properties.setMieScaleHeightKm(150D);
		properties.setAbsorptionCenterKm(150D);
		properties.setAbsorptionWidthKm(150D);

		NBTTagCompound child = new NBTTagCompound();
		properties.writeToNBT(child);
		AtmosphereVisualProperties restored =
				AtmosphereVisualProperties.readFromNBT(child, "test");
		AtmosphereVisualProperties.Snapshot snapshot =
				restored.snapshot();

		assertFalse(snapshot.hasAtmosphereHeightKm());
		assertEquals(150D,
				snapshot.getRayleighScaleHeightKm(), EPSILON);
		assertEquals(150D, snapshot.getMieScaleHeightKm(), EPSILON);
		assertEquals(150D, snapshot.getAbsorptionCenterKm(), EPSILON);
		assertEquals(150D, snapshot.getAbsorptionWidthKm(), EPSILON);
	}

	@Test
	public void explicitHeightStillConstrainsLayerOverrides() {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		properties.setAtmosphereHeightKm(100D);
		boolean rejected = false;
		try {
			properties.setRayleighScaleHeightKm(150D);
		}
		catch(IllegalArgumentException expected) {
			rejected = true;
		}
		assertTrue(rejected);
		assertFalse(properties.hasRayleighScaleHeightKm());
	}

	@Test
	public void clearingRadiusCannotInvalidateExplicitHeight() {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		properties.setPlanetRadiusKm(30000D);
		properties.setAtmosphereHeightKm(10000D);

		boolean rejected = false;
		try {
			properties.clearPlanetRadiusKm();
		}
		catch(IllegalStateException expected) {
			rejected = true;
		}
		assertTrue(rejected);
		assertTrue(properties.hasPlanetRadiusKm());
		assertEquals(30000D, properties.getPlanetRadiusKm(), EPSILON);
	}

	@Test
	public void zeroValuesAndAutoModePreservePresence() {
		AtmosphereVisualProperties properties =
				new AtmosphereVisualProperties();
		properties.setRayleighStrength(0D);
		properties.setSunIntensityMultiplier(0D);
		properties.setCloudLayerMode(CloudLayerMode.AUTO);

		NBTTagCompound child = new NBTTagCompound();
		properties.writeToNBT(child);
		AtmosphereVisualProperties restored =
				AtmosphereVisualProperties.readFromNBT(child, "test");

		assertTrue(restored.hasRayleighStrength());
		assertEquals(0D, restored.getRayleighStrength(), EPSILON);
		assertTrue(restored.hasSunIntensityMultiplier());
		assertEquals(0D, restored.getSunIntensityMultiplier(), EPSILON);
		assertTrue(restored.hasCloudLayerMode());
		assertEquals(CloudLayerMode.AUTO,
				restored.getCloudLayerMode());
	}
}
