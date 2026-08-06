package zmaster587.advancedRocketry.atmosphere;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import net.minecraft.world.World;
import org.junit.Test;
import zmaster587.advancedRocketry.api.AreaBlob;
import zmaster587.advancedRocketry.api.util.IBlobHandler;
import zmaster587.libVulpes.util.BlockPosition;

public class AtmosphereHandlerDynamicSealTest {

	private static final BlockPosition ROOT = new BlockPosition(0, 1, 0);

	@Test
	public void closingFinalPanelCutsDoubleAirlockFromVentBlob() {
		AreaBlob blob = createOpenDoubleAirlockBlob();

		assertTrue(closePanel(blob, 0));
		assertTrue(blob.contains(new BlockPosition(6, 1, 0)));

		assertTrue(closePanel(blob, 1));
		assertTrue(blob.contains(new BlockPosition(6, 1, 0)));

		assertTrue(closePanel(blob, 2));
		assertTrue(blob.contains(ROOT));
		assertTrue(blob.contains(new BlockPosition(1, 1, 0)));
		assertFalse(blob.contains(new BlockPosition(2, 1, 0)));
		assertFalse(blob.contains(new BlockPosition(3, 1, 0)));
		assertFalse(blob.contains(new BlockPosition(6, 1, 0)));
	}

	@Test
	public void unsealedNonFullBlockDoesNotCutBlob() {
		AreaBlob blob = createOpenDoubleAirlockBlob();
		BlockPosition panel = new BlockPosition(2, 1, 0);

		assertFalse(AtmosphereHandler.removeSealedPositionFromBlob(
				blob, panel, false));
		assertTrue(blob.contains(panel));
		assertTrue(blob.contains(new BlockPosition(6, 1, 0)));
	}

	private static boolean closePanel(AreaBlob blob, int y) {
		return AtmosphereHandler.removeSealedPositionFromBlob(blob,
				new BlockPosition(2, y, 0), true);
	}

	private static AreaBlob createOpenDoubleAirlockBlob() {
		AreaBlob blob = new AreaBlob(new TestBlobHandler());
		for(int x = 0; x <= 6; x++) {
			for(int y = 0; y <= 2; y++) {
				blob.addBlock(new BlockPosition(x, y, 0),
						Collections.<AreaBlob>emptyList());
			}
		}
		return blob;
	}

	private static class TestBlobHandler implements IBlobHandler {

		@Override
		public boolean canFormBlob() {
			return true;
		}

		@Override
		public World getWorld() {
			return null;
		}

		@Override
		public boolean canBlobsOverlap(BlockPosition blockPosition,
				AreaBlob blob) {
			return false;
		}

		@Override
		public int getMaxBlobRadius() {
			return 32;
		}

		@Override
		public BlockPosition getRootPosition() {
			return ROOT;
		}

		@Override
		public int getTraceDistance() {
			return -1;
		}
	}
}
