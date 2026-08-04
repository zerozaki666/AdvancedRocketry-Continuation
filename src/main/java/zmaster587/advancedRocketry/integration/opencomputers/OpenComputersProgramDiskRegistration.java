package zmaster587.advancedRocketry.integration.opencomputers;

import java.util.concurrent.Callable;

import cpw.mods.fml.common.registry.GameRegistry;
import li.cil.oc.api.FileSystem;
import li.cil.oc.api.Items;
import li.cil.oc.api.detail.ItemInfo;
import net.minecraft.item.ItemStack;
import zmaster587.advancedRocketry.AdvancedRocketry;

/**
 * OpenComputers-only registration boundary for the AdvRocket program disk.
 *
 * <p>This class must only be loaded after OpenComputers has been detected and
 * during FML init or later. Keeping all strong OC API references here allows
 * AdvancedRocketry to start normally when OpenComputers is absent.</p>
 */
public final class OpenComputersProgramDiskRegistration {

	private static final String DISK_LABEL = "AdvRocket";
	private static final String RESOURCE_DOMAIN = "advancedrocketry";
	private static final String RESOURCE_ROOT = "loot/advrocket";
	private static final int CYAN_DYE_INDEX = 6;
	private static boolean registered;

	private OpenComputersProgramDiskRegistration() {
	}

	public static synchronized void register() {
		if(registered)
			return;
		try {
			ItemInfo floppyInfo = Items.get("floppy");
			if(floppyInfo == null) {
				AdvancedRocketry.logger.error("OpenComputers did not expose "
						+ "the canonical floppy item; AdvRocket program disk "
						+ "registration was skipped");
				return;
			}
			final ItemStack blankFloppy = floppyInfo.createItemStack(1);
			if(blankFloppy == null) {
				AdvancedRocketry.logger.error("OpenComputers returned no "
						+ "blank floppy stack; AdvRocket program disk "
						+ "registration was skipped");
				return;
			}

			ItemStack programDisk = Items.registerFloppy(DISK_LABEL,
					CYAN_DYE_INDEX,
					new Callable<li.cil.oc.api.fs.FileSystem>() {
						@Override
						public li.cil.oc.api.fs.FileSystem call() {
							return FileSystem.fromClass(
									OpenComputersProgramDiskRegistration.class,
									RESOURCE_DOMAIN, RESOURCE_ROOT);
						}
					}, false);
			if(programDisk == null) {
				AdvancedRocketry.logger.error("OpenComputers rejected the "
						+ "AdvRocket program disk registration; its recipe "
						+ "was not registered");
				return;
			}

			GameRegistry.addRecipe(new AdvRocketProgramDiskRecipe(
					programDisk, blankFloppy));
			registered = true;
			AdvancedRocketry.logger.info("Registered the optional "
					+ "AdvRocket OpenComputers program disk and recipe");
		}
		catch(Throwable throwable) {
			// Optional integration must never prevent the base mod from loading.
			AdvancedRocketry.logger.error("Failed to register the optional "
					+ "AdvRocket OpenComputers program disk; continuing "
					+ "without it", throwable);
		}
	}
}
