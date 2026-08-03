package zmaster587.advancedRocketry.tile.multiblock.machine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import net.minecraft.block.Block;
import org.junit.Test;
import zmaster587.advancedRocketry.api.AdvancedRocketryBlocks;
import zmaster587.advancedRocketry.block.BlockQuartzCrucible;

public class TileCrystallizerTest {

	@Test
	public void quartzCrucibleHasLocalizedBlockName() {
		assertEquals("tile.qcrucible",
				new BlockQuartzCrucible().getUnlocalizedName());
	}

	@Test
	public void structureRefreshesAfterEarlyClassLoading() {
		Block previousCrucible =
				AdvancedRocketryBlocks.blockQuartzCrucible;
		try {
			AdvancedRocketryBlocks.blockQuartzCrucible = null;
			TileCrystallizer.refreshStructure();

			BlockQuartzCrucible registeredCrucible =
					new BlockQuartzCrucible();
			AdvancedRocketryBlocks.blockQuartzCrucible =
					registeredCrucible;

			Object[][][] structure =
					new TileCrystallizer().getStructure();
			for(int row = 0; row < 2; row++)
				for(int column = 0; column < 3; column++)
					assertSame(registeredCrucible,
							structure[0][row][column]);
		}
		finally {
			AdvancedRocketryBlocks.blockQuartzCrucible =
					previousCrucible;
			TileCrystallizer.refreshStructure();
		}
	}
}
