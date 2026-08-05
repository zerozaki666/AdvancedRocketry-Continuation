package zmaster587.advancedRocketry.integration.appliedenergistics;

import appeng.api.AEApi;
import appeng.api.util.AEColor;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.AdvancedRocketryBlocks;
import zmaster587.advancedRocketry.block.BlockAirtightInterface;
import zmaster587.advancedRocketry.util.SealableBlockHandler;

/** Optional AE2 block and recipe registration boundary. */
public final class AppliedEnergisticsAirtightInterfaceRegistration {

	private static boolean blocksRegistered;
	private static boolean recipesRegistered;

	private AppliedEnergisticsAirtightInterfaceRegistration() {
	}

	public static synchronized void registerBlocks(CreativeTabs creativeTab) {
		if(blocksRegistered)
			return;
		Block smart = new BlockAirtightInterface(Material.iron,
				TileAirtightMESmartInterface.class)
				.setBlockName("airtightMESmartInterface")
				.setBlockTextureName("advancedrocketry:dataHatch")
				.setCreativeTab(creativeTab).setHardness(3f).setResistance(12f);
		Block dense = new BlockAirtightInterface(Material.iron,
				TileAirtightMEDenseInterface.class)
				.setBlockName("airtightMEDenseInterface")
				.setBlockTextureName("advancedrocketry:machineStorage")
				.setCreativeTab(creativeTab).setHardness(3f).setResistance(12f);

		GameRegistry.registerBlock(smart, "airtightMESmartInterface");
		GameRegistry.registerBlock(dense, "airtightMEDenseInterface");
		GameRegistry.registerTileEntity(TileAirtightMESmartInterface.class,
				"ARAirtightMESmartInterface");
		GameRegistry.registerTileEntity(TileAirtightMEDenseInterface.class,
				"ARAirtightMEDenseInterface");
		AdvancedRocketryBlocks.blockAirtightMESmartInterface = smart;
		AdvancedRocketryBlocks.blockAirtightMEDenseInterface = dense;
		SealableBlockHandler.INSTANCE.addSealableBlock(smart);
		SealableBlockHandler.INSTANCE.addSealableBlock(dense);
		blocksRegistered = true;
		AdvancedRocketry.logger.info("Registered optional airtight AE2 smart "
				+ "and dense smart interfaces");
	}

	public static synchronized void registerRecipes() {
		if(recipesRegistered || !blocksRegistered)
			return;
		ItemStack smartCable = AEApi.instance().definitions().parts()
				.cableSmart().stack(AEColor.Transparent, 1);
		ItemStack denseCable = AEApi.instance().definitions().parts()
				.cableDense().stack(AEColor.Transparent, 1);
		if(smartCable == null || denseCable == null) {
			AdvancedRocketry.logger.error("AE2 did not expose canonical smart "
					+ "cables; airtight ME recipes were skipped");
			return;
		}
		GameRegistry.addRecipe(interfaceRecipe(
				AdvancedRocketryBlocks.blockAirtightMESmartInterface,
				smartCable));
		GameRegistry.addRecipe(interfaceRecipe(
				AdvancedRocketryBlocks.blockAirtightMEDenseInterface,
				denseCable));
		recipesRegistered = true;
	}

	private static ShapedOreRecipe interfaceRecipe(Block output,
			ItemStack cable) {
		return new ShapedOreRecipe(new ItemStack(output),
				"pcp", "csc", "pcp", 'p', "plateSteel", 'c', cable,
				's', AdvancedRocketryBlocks.blockPipeSealer);
	}
}
