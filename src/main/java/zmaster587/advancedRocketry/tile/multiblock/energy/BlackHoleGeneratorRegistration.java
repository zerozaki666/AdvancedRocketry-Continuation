package zmaster587.advancedRocketry.tile.multiblock.energy;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.AdvancedRocketryBlocks;
import zmaster587.advancedRocketry.api.AdvancedRocketryItems;
import zmaster587.libVulpes.api.LibVulpesBlocks;
import zmaster587.libVulpes.api.LibVulpesItems;
import zmaster587.libVulpes.block.BlockTile;
import zmaster587.libVulpes.items.ItemProjector;

/**
 * Small integration helpers kept outside the shared mod bootstrap.
 */
public final class BlackHoleGeneratorRegistration {

	private static boolean recipeRegistered;
	private static boolean projectorRegistered;

	private BlackHoleGeneratorRegistration() {
	}

	public static synchronized void registerRecipe(Block controller) {
		if(recipeRegistered)
			return;
		if(controller == null) {
			AdvancedRocketry.logger.warn(
					"Cannot register black hole generator recipe: controller block is null");
			return;
		}

		warnIfOreMissing("plateTitaniumAluminide");
		warnIfOreMissing("blockMotor");

		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(controller),
				"bgb",
				"pcp",
				"msm",
				Character.valueOf('b'),
				new ItemStack(LibVulpesItems.itemBattery, 1, 1),
				Character.valueOf('g'),
				AdvancedRocketryBlocks.blockStructureTower,
				Character.valueOf('p'), "plateTitaniumAluminide",
				Character.valueOf('c'),
				LibVulpesBlocks.blockAdvStructureBlock,
				Character.valueOf('m'), "blockMotor",
				Character.valueOf('s'),
				new ItemStack(AdvancedRocketryItems.itemMisc, 1, 0)));

		BlackHoleGeneratorFuelRegistry.reloadFromConfiguration();
		recipeRegistered = true;
	}

	public static synchronized void registerProjector(Block controller) {
		if(projectorRegistered)
			return;
		if(!(controller instanceof BlockTile)) {
			AdvancedRocketry.logger.warn(
					"Cannot register black hole generator with Holo Projector: controller is not a BlockTile");
			return;
		}
		if(!(LibVulpesItems.itemHoloProjector instanceof ItemProjector)) {
			AdvancedRocketry.logger.warn(
					"Cannot register black hole generator with Holo Projector: projector item is unavailable");
			return;
		}

		((ItemProjector)LibVulpesItems.itemHoloProjector).registerMachine(
				new TileBlackHoleGenerator(), (BlockTile)controller);
		projectorRegistered = true;
	}

	private static void warnIfOreMissing(String oreName) {
		if(OreDictionary.getOres(oreName).isEmpty())
			AdvancedRocketry.logger.warn(
					"Black hole generator recipe ore key '{}' has no registered entries",
					oreName);
	}
}
