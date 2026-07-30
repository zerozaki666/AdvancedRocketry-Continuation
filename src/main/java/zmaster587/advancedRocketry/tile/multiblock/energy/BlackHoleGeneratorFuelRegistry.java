package zmaster587.advancedRocketry.tile.multiblock.energy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.Configuration;

/**
 * Immutable runtime view of the black hole generator fuel configuration.
 */
public final class BlackHoleGeneratorFuelRegistry {

	private static final int FALLBACK_BURN_TIME = 500;
	private static final int BASE_POWER_PER_TICK = 500;

	private static volatile FuelTable currentTable;
	private static boolean warnedInvalidMultiplier;

	private BlackHoleGeneratorFuelRegistry() {
	}

	/**
	 * Rebuilds the immutable lookup table. This should be called during init,
	 * after every mod has registered its items.
	 */
	public static synchronized void reloadFromConfiguration() {
		currentTable = FuelTable.parse(Configuration.blackHoleTimings,
				Configuration.defaultItemTimeBlackHole,
				Configuration.blackHoleAllowUnlistedMatter);
		warnedInvalidMultiplier = false;
	}

	public static int getBurnTime(ItemStack stack) {
		if(stack == null || stack.stackSize <= 0 || stack.getItem() == null)
			return 0;

		return getTable().getBurnTime(stack);
	}

	/**
	 * Returns the sanitized RF/t request. A return value of zero means that the
	 * generator is disabled by configuration.
	 */
	public static int getRequestedPowerPerTick() {
		double multiplier = Configuration.blackHoleGeneratorMultiplier;
		if(Double.isNaN(multiplier) || Double.isInfinite(multiplier)
				|| multiplier < 0D) {
			if(!warnedInvalidMultiplier) {
				AdvancedRocketry.logger.warn(
						"Invalid blackHoleGeneratorMultiplier {}; disabling black hole generator output",
						Double.valueOf(multiplier));
				warnedInvalidMultiplier = true;
			}
			return 0;
		}

		if(multiplier == 0D)
			return 0;

		double requested = BASE_POWER_PER_TICK * multiplier;
		if(Double.isInfinite(requested) || requested >= Integer.MAX_VALUE)
			return Integer.MAX_VALUE;

		// Positive sub-RF multipliers should remain usable rather than silently
		// collapsing to the same behavior as an explicit zero multiplier.
		return Math.max(1, (int)Math.floor(requested));
	}

	private static FuelTable getTable() {
		FuelTable table = currentTable;
		if(table == null) {
			synchronized(BlackHoleGeneratorFuelRegistry.class) {
				table = currentTable;
				if(table == null) {
					reloadFromConfiguration();
					table = currentTable;
				}
			}
		}
		return table;
	}

	private static final class FuelTable {
		private final Map<FuelKey, Integer> exact;
		private final Map<String, Integer> wildcard;
		private final int defaultBurnTime;
		private final boolean allowUnlisted;

		private FuelTable(Map<FuelKey, Integer> exact,
				Map<String, Integer> wildcard, int defaultBurnTime,
				boolean allowUnlisted) {
			this.exact = Collections.unmodifiableMap(exact);
			this.wildcard = Collections.unmodifiableMap(wildcard);
			this.defaultBurnTime = defaultBurnTime;
			this.allowUnlisted = allowUnlisted;
		}

		private int getBurnTime(ItemStack stack) {
			Object registryObject = Item.itemRegistry
					.getNameForObject(stack.getItem());
			if(registryObject == null)
				return 0;

			String registryName = registryObject.toString();
			Integer duration = exact.get(new FuelKey(registryName,
					stack.getItemDamage()));
			if(duration != null)
				return duration.intValue();

			duration = wildcard.get(registryName);
			if(duration != null)
				return duration.intValue();

			return allowUnlisted ? defaultBurnTime : 0;
		}

		private static FuelTable parse(String[] entries, int configuredDefault,
				boolean allowUnlisted) {
			int defaultBurnTime = configuredDefault;
			if(defaultBurnTime <= 0) {
				AdvancedRocketry.logger.warn(
						"Invalid black hole generator defaultBurnTime {}; using {}",
						Integer.valueOf(defaultBurnTime),
						Integer.valueOf(FALLBACK_BURN_TIME));
				defaultBurnTime = FALLBACK_BURN_TIME;
			}

			Map<FuelKey, Integer> exact =
					new HashMap<FuelKey, Integer>();
			Map<String, Integer> wildcard =
					new HashMap<String, Integer>();

			if(entries == null)
				entries = new String[0];

			for(String rawEntry : entries)
				parseEntry(rawEntry, exact, wildcard);

			return new FuelTable(exact, wildcard, defaultBurnTime,
					allowUnlisted);
		}

		private static void parseEntry(String rawEntry,
				Map<FuelKey, Integer> exact,
				Map<String, Integer> wildcard) {
			if(rawEntry == null || rawEntry.trim().length() == 0) {
				warnInvalid(rawEntry, "entry is empty");
				return;
			}

			String entry = rawEntry.trim();
			int separator = entry.lastIndexOf(';');
			if(separator <= 0 || separator == entry.length() - 1
					|| entry.indexOf(';') != separator) {
				warnInvalid(rawEntry,
						"expected modid:item[:meta-or-*];ticks");
				return;
			}

			String itemSpec = entry.substring(0, separator).trim();
			String durationSpec = entry.substring(separator + 1).trim();
			int duration;
			try {
				duration = Integer.parseInt(durationSpec);
			}
			catch(NumberFormatException ex) {
				warnInvalid(rawEntry, "burn time is not a positive integer");
				return;
			}
			if(duration <= 0) {
				warnInvalid(rawEntry, "burn time must be positive");
				return;
			}

			String[] itemParts = itemSpec.split(":", -1);
			if((itemParts.length != 2 && itemParts.length != 3)
					|| itemParts[0].length() == 0
					|| itemParts[1].length() == 0) {
				warnInvalid(rawEntry, "invalid item registry name");
				return;
			}

			String registryName = itemParts[0] + ":" + itemParts[1];
			Object registeredItem = Item.itemRegistry.getObject(registryName);
			if(!(registeredItem instanceof Item)) {
				warnInvalid(rawEntry, "item is not registered");
				return;
			}

			if(itemParts.length == 2) {
				exact.put(new FuelKey(registryName, 0),
						Integer.valueOf(duration));
				return;
			}

			String metaSpec = itemParts[2].trim();
			if("*".equals(metaSpec)) {
				wildcard.put(registryName, Integer.valueOf(duration));
				return;
			}

			int meta;
			try {
				meta = Integer.parseInt(metaSpec);
			}
			catch(NumberFormatException ex) {
				warnInvalid(rawEntry, "metadata is neither an integer nor *");
				return;
			}
			if(meta < 0) {
				warnInvalid(rawEntry, "metadata must not be negative");
				return;
			}

			exact.put(new FuelKey(registryName, meta),
					Integer.valueOf(duration));
		}

		private static void warnInvalid(String entry, String reason) {
			AdvancedRocketry.logger.warn(
					"Ignoring invalid black hole generator fuel entry '{}': {}",
					String.valueOf(entry), reason);
		}
	}

	private static final class FuelKey {
		private final String registryName;
		private final int metadata;

		private FuelKey(String registryName, int metadata) {
			this.registryName = registryName;
			this.metadata = metadata;
		}

		@Override
		public boolean equals(Object object) {
			if(this == object)
				return true;
			if(!(object instanceof FuelKey))
				return false;

			FuelKey other = (FuelKey)object;
			return metadata == other.metadata
					&& registryName.equals(other.registryName);
		}

		@Override
		public int hashCode() {
			return 31 * registryName.hashCode() + metadata;
		}
	}
}
