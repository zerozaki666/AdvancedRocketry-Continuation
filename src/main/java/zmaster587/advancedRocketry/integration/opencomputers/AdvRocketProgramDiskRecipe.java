package zmaster587.advancedRocketry.integration.opencomputers;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import zmaster587.libVulpes.api.LibVulpesItems;

/**
 * Data-safe shapeless recipe for the AdvRocket OpenOS program disk.
 *
 * <p>OpenComputers uses the same item and metadata for blank floppies,
 * writable data disks and registered loot disks. Forge's normal shapeless
 * recipe matcher would therefore be able to consume a player's populated
 * disk. This recipe accepts only the canonical, completely untagged blank
 * floppy returned by the public OpenComputers API.</p>
 */
public final class AdvRocketProgramDiskRecipe
		implements net.minecraft.item.crafting.IRecipe {

	private final ItemStack output;
	private final ItemStack blankFloppy;

	public AdvRocketProgramDiskRecipe(ItemStack output,
			ItemStack blankFloppy) {
		if(output == null || blankFloppy == null)
			throw new IllegalArgumentException(
					"Program disk recipe stacks cannot be null");
		this.output = output.copy();
		this.output.stackSize = 1;
		this.blankFloppy = blankFloppy.copy();
		this.blankFloppy.stackSize = 1;
	}

	@Override
	public boolean matches(InventoryCrafting inventory, World world) {
		if(inventory == null)
			return false;
		int linkerCount = 0;
		int floppyCount = 0;
		int occupied = 0;
		for(int slot = 0; slot < inventory.getSizeInventory(); slot++) {
			ItemStack stack = inventory.getStackInSlot(slot);
			if(stack == null)
				continue;
			occupied++;
			if(isLinker(stack))
				linkerCount++;
			else if(isBlankFloppy(stack))
				floppyCount++;
			else
				return false;
		}
		return occupied == 2 && linkerCount == 1 && floppyCount == 1;
	}

	private static boolean isLinker(ItemStack stack) {
		Item linker = LibVulpesItems.itemLinker;
		return linker != null && stack.getItem() == linker;
	}

	boolean isBlankFloppy(ItemStack stack) {
		if(stack == null || stack.getItem() != blankFloppy.getItem())
			return false;
		int expectedDamage = blankFloppy.getItemDamage();
		if(expectedDamage != OreDictionary.WILDCARD_VALUE
				&& stack.getItemDamage() != expectedDamage)
			return false;
		// Reject color, label, filesystem, loot-factory, display-name and all
		// other NBT. The canonical blank stack itself is untagged.
		return !stack.hasTagCompound();
	}

	@Override
	public ItemStack getCraftingResult(InventoryCrafting inventory) {
		return matches(inventory, null) ? output.copy() : null;
	}

	@Override
	public int getRecipeSize() {
		return 2;
	}

	@Override
	public ItemStack getRecipeOutput() {
		return output.copy();
	}
}
