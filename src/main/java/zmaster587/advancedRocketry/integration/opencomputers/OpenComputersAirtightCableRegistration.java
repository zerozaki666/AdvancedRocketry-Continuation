package zmaster587.advancedRocketry.integration.opencomputers;

import cpw.mods.fml.common.registry.GameRegistry;
import li.cil.oc.api.Items;
import li.cil.oc.api.detail.ItemInfo;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.AdvancedRocketryBlocks;
import zmaster587.advancedRocketry.block.BlockAirtightInterface;
import zmaster587.advancedRocketry.util.SealableBlockHandler;

/** Optional OpenComputers block and recipe registration boundary. */
public final class OpenComputersAirtightCableRegistration {

	private static boolean blockRegistered;
	private static boolean recipeRegistered;

	private OpenComputersAirtightCableRegistration() {
	}

	public static synchronized void registerBlock(CreativeTabs creativeTab) {
		if(blockRegistered)
			return;
		Block block = new BlockAirtightInterface(Material.iron,
				TileAirtightOpenComputersCable.class)
				.setBlockName("airtightOpenComputersCable")
				.setBlockTextureName("advancedrocketry:pipeData")
				.setCreativeTab(creativeTab).setHardness(3f).setResistance(12f);
		GameRegistry.registerBlock(block, "airtightOpenComputersCable");
		GameRegistry.registerTileEntity(TileAirtightOpenComputersCable.class,
				"ARAirtightOpenComputersCable");
		AdvancedRocketryBlocks.blockAirtightOpenComputersCable = block;
		SealableBlockHandler.INSTANCE.addSealableBlock(block);
		blockRegistered = true;
		AdvancedRocketry.logger.info("Registered optional airtight "
				+ "OpenComputers cable interface");
	}

	public static synchronized void registerRecipe() {
		if(recipeRegistered || !blockRegistered)
			return;
		ItemInfo cableInfo = Items.get("cable");
		ItemStack cable = cableInfo == null ? null
				: cableInfo.createItemStack(1);
		if(cable == null) {
			AdvancedRocketry.logger.error("OpenComputers did not expose its "
					+ "canonical cable; airtight cable recipe was skipped");
			return;
		}
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(
				AdvancedRocketryBlocks.blockAirtightOpenComputersCable),
				"pcp", "csc", "pcp", 'p', "plateSteel", 'c', cable,
				's', AdvancedRocketryBlocks.blockPipeSealer));
		recipeRegistered = true;
	}
}
