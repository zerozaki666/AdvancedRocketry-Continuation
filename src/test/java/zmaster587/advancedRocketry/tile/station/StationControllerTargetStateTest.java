package zmaster587.advancedRocketry.tile.station;

import static org.junit.Assert.assertEquals;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.common.CommonProxy;

public class StationControllerTargetStateTest {
	private static CommonProxy previousProxy;

	@BeforeClass
	public static void installCommonProxyForTileConstruction() {
		previousProxy = LibVulpes.proxy;
		if(LibVulpes.proxy == null)
			LibVulpes.proxy = new CommonProxy();
		GameRegistry.registerTileEntity(TileStationOrientationControl.class,
				"AROrientationControl");
	}

	@AfterClass
	public static void restoreProxy() {
		LibVulpes.proxy = previousProxy;
	}

	@Test
	public void orientationNbtKeepsAllThreeAxesAndClampsThem() {
		TileStationOrientationControl controller =
				new TileStationOrientationControl();
		NBTTagCompound input = new NBTTagCompound();
		input.setShort("numRotationsX", (short)12);
		input.setShort("numRotationsY", (short)-27);
		input.setShort("numRotationsZ", (short)100);

		controller.readFromNBT(input);

		assertEquals(12, controller.numRotationsPerHour[0]);
		assertEquals(-27, controller.numRotationsPerHour[1]);
		assertEquals(60, controller.numRotationsPerHour[2]);
		NBTTagCompound output = new NBTTagCompound();
		controller.writeToNBT(output);
		assertEquals(12, output.getShort("numRotationsX"));
		assertEquals(-27, output.getShort("numRotationsY"));
		assertEquals(60, output.getShort("numRotationsZ"));
	}

	@Test
	public void yawAndPitchTargetsNeverChangeHiddenAxis() {
		TileStationOrientationControl controller =
				new TileStationOrientationControl();
		controller.setTargetRotationRate(2, 33);
		controller.setTargetRotationRate(1, 4);
		controller.setTargetRotationRate(0, -2);

		assertEquals(-2, controller.numRotationsPerHour[0]);
		assertEquals(4, controller.numRotationsPerHour[1]);
		assertEquals(33, controller.numRotationsPerHour[2]);
	}

	@Test
	public void orientationNetworkDecodeDoesNotApplyUntilServerUse() {
		TileStationOrientationControl controller =
				new TileStationOrientationControl();
		ByteBuf data = Unpooled.buffer();
		data.writeShort(55);
		data.writeShort(65);
		data.writeShort(93);
		NBTTagCompound decoded = new NBTTagCompound();

		controller.readDataFromNetwork(data, (byte)0, decoded);
		assertEquals(0, controller.numRotationsPerHour[0]);
		assertEquals(0, controller.numRotationsPerHour[1]);
		assertEquals(0, controller.numRotationsPerHour[2]);

		controller.useNetworkData(null, Side.SERVER, (byte)0, decoded);
		assertEquals(-5, controller.numRotationsPerHour[0]);
		assertEquals(5, controller.numRotationsPerHour[1]);
		assertEquals(33, controller.numRotationsPerHour[2]);
	}

	@Test
	public void gravityNetworkDecodeDoesNotApplyUntilServerUse() {
		TileStationGravityController controller =
				new TileStationGravityController();
		ByteBuf data = Unpooled.buffer();
		data.writeShort(28);
		NBTTagCompound decoded = new NBTTagCompound();

		controller.readDataFromNetwork(data, (byte)0, decoded);
		assertEquals(15, controller.gravity);
		controller.useNetworkData(null, Side.SERVER, (byte)0, decoded);
		assertEquals(38, controller.gravity);
	}

	@Test
	public void altitudeNetworkDecodeDoesNotApplyUntilServerUse() {
		TileStationAltitudeController controller =
				new TileStationAltitudeController();
		ByteBuf data = Unpooled.buffer();
		data.writeShort(40);
		NBTTagCompound decoded = new NBTTagCompound();

		controller.readDataFromNetwork(data, (byte)0, decoded);
		assertEquals(10, controller.gravity);
		controller.useNetworkData(null, Side.SERVER, (byte)0, decoded);
		assertEquals(50, controller.gravity);
	}

	@Test
	public void descriptionPacketsContainCompleteTargetState() {
		TileStationAltitudeController altitude =
				new TileStationAltitudeController();
		altitude.setTargetAltitudeProgress(25);
		altitude.setAltitudeChangeRateProgress(8);
		NBTTagCompound altitudeNbt = ((S35PacketUpdateTileEntity)altitude
				.getDescriptionPacket()).func_148857_g();
		assertEquals(35, altitudeNbt.getInteger("gravity"));
		assertEquals(8, altitudeNbt.getInteger(
				"altitudeChangeRateProgress"));

		TileStationOrientationControl orientation =
				new TileStationOrientationControl();
		orientation.setTargetRotationRate(0, -2);
		orientation.setTargetRotationRate(1, 4);
		orientation.setTargetRotationRate(2, 6);
		NBTTagCompound orientationNbt =
				((S35PacketUpdateTileEntity)orientation
						.getDescriptionPacket()).func_148857_g();
		assertEquals(-2, orientationNbt.getShort("numRotationsX"));
		assertEquals(4, orientationNbt.getShort("numRotationsY"));
		assertEquals(6, orientationNbt.getShort("numRotationsZ"));

		TileStationGravityController gravity =
				new TileStationGravityController();
		gravity.setTargetGravityPercent(38);
		NBTTagCompound gravityNbt = ((S35PacketUpdateTileEntity)gravity
				.getDescriptionPacket()).func_148857_g();
		assertEquals(38, gravityNbt.getInteger("gravity"));
	}
}
