package zmaster587.advancedRocketry.integration.opencomputers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zmaster587.libVulpes.api.LibVulpesItems;

public class AdvRocketProgramDiskRecipeTest {

	private Item previousLinker;
	private Item linker;
	private Item floppy;
	private Item outputItem;
	private AdvRocketProgramDiskRecipe recipe;

	@Before
	public void setUp() {
		previousLinker = LibVulpesItems.itemLinker;
		linker = new TestItem();
		floppy = new TestItem();
		outputItem = new TestItem();
		LibVulpesItems.itemLinker = linker;
		recipe = new AdvRocketProgramDiskRecipe(
				new ItemStack(outputItem, 1, 9),
				new ItemStack(floppy, 1, 4));
	}

	@After
	public void tearDown() {
		LibVulpesItems.itemLinker = previousLinker;
	}

	@Test
	public void acceptsExactlyOneLinkerAndCanonicalBlankFloppy() {
		InventoryCrafting grid = grid(new ItemStack(linker),
				new ItemStack(floppy, 1, 4));
		assertTrue(recipe.matches(grid, null));
		ItemStack result = recipe.getCraftingResult(grid);
		assertEquals(outputItem, result.getItem());
		assertEquals(9, result.getItemDamage());
		assertNotSame(recipe.getRecipeOutput(), result);
	}

	@Test
	public void remainsShapeless() {
		assertTrue(recipe.matches(grid(new ItemStack(floppy, 1, 4),
				new ItemStack(linker)), null));
	}

	@Test
	public void rejectsEveryTaggedFloppy() {
		for(String key : new String[] { "oc:color", "oc:lootFactory",
				"oc:data", "display", "custom" }) {
			ItemStack candidate = new ItemStack(floppy, 1, 4);
			NBTTagCompound tag = new NBTTagCompound();
			tag.setString(key, "value");
			candidate.setTagCompound(tag);
			InventoryCrafting grid = grid(new ItemStack(linker), candidate);
			assertFalse("tagged floppy was accepted: " + key,
					recipe.matches(grid, null));
			assertNull(recipe.getCraftingResult(grid));
		}
	}

	@Test
	public void rejectsWrongMetadataAndOtherItems() {
		assertFalse(recipe.matches(grid(new ItemStack(linker),
				new ItemStack(floppy, 1, 5)), null));
		assertFalse(recipe.matches(grid(new ItemStack(linker),
				new ItemStack(new TestItem())), null));
	}

	@Test
	public void rejectsMissingDuplicateAndExtraIngredients() {
		assertFalse(recipe.matches(grid(new ItemStack(linker)), null));
		assertFalse(recipe.matches(grid(new ItemStack(linker),
				new ItemStack(linker)), null));
		assertFalse(recipe.matches(grid(new ItemStack(floppy, 1, 4),
				new ItemStack(floppy, 1, 4)), null));
		assertFalse(recipe.matches(grid(new ItemStack(linker),
				new ItemStack(floppy, 1, 4),
				new ItemStack(new TestItem())), null));
	}

	private static InventoryCrafting grid(ItemStack... stacks) {
		InventoryCrafting inventory = new InventoryCrafting(new Container() {
			@Override
			public boolean canInteractWith(EntityPlayer player) {
				return false;
			}
		}, 3, 3);
		for(int index = 0; index < stacks.length; index++)
			inventory.setInventorySlotContents(index, stacks[index]);
		return inventory;
	}

	private static final class TestItem extends Item {
	}
}
