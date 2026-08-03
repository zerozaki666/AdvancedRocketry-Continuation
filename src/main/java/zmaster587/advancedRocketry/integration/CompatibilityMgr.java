package zmaster587.advancedRocketry.integration;

import cpw.mods.fml.common.Loader;
import net.minecraft.item.ItemStack;
import zmaster587.advancedRocketry.AdvancedRocketry;

public class CompatibilityMgr {

	public static boolean gregtechLoaded;
	public static boolean thermalExpansion;
	public static boolean powerSuits;
	public static boolean openComputersLoaded;
	
	public CompatibilityMgr() {
		gregtechLoaded = false;
		thermalExpansion = false;
		powerSuits = false;
		openComputersLoaded = false;
	}
	
	public static void getLoadedMods() {
		thermalExpansion = Loader.isModLoaded("ThermalExpansion");
		gregtechLoaded = Loader.isModLoaded("gregtech");
		powerSuits = Loader.isModLoaded("powersuits");
		openComputersLoaded = Loader.isModLoaded("OpenComputers");
		if(openComputersLoaded)
			AdvancedRocketry.logger.info("OpenComputers 1.12.44-GTNH detected; station controller integration enabled");
		else
			AdvancedRocketry.logger.debug("OpenComputers not detected; station controller integration disabled");
	}
	
	public static void initCompatRecipies() {
		if(gregtechLoaded) {
			
		}
	}
}
