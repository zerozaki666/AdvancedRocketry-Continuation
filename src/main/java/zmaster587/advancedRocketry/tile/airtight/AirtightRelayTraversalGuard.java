package zmaster587.advancedRocketry.tile.airtight;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;

/** Prevents pass-through interfaces from recursively routing around loops. */
final class AirtightRelayTraversalGuard {

	private static final ThreadLocal<Set<TileEntity>> ACTIVE =
			new ThreadLocal<Set<TileEntity>>() {
				@Override
				protected Set<TileEntity> initialValue() {
					return Collections.newSetFromMap(
							new IdentityHashMap<TileEntity, Boolean>());
				}
			};

	private AirtightRelayTraversalGuard() {
	}

	static boolean enter(TileEntity tile) {
		return ACTIVE.get().add(tile);
	}

	static void exit(TileEntity tile) {
		Set<TileEntity> active = ACTIVE.get();
		active.remove(tile);
		if(active.isEmpty())
			ACTIVE.remove();
	}
}
