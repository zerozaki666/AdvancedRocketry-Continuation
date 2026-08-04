package zmaster587.advancedRocketry.tile.multiblock;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import zmaster587.advancedRocketry.tile.multiblock.BiomeModIdResolver.Candidate;

public class BiomeModIdResolverTest {

	@Test
	public void knownNamespacesHaveStableIds() {
		assertEquals("minecraft", BiomeModIdResolver
				.resolveKnownNamespace(
						"net.minecraft.world.biome.BiomeGenForest"));
		assertEquals("advancedRocketry", BiomeModIdResolver
				.resolveKnownNamespace(
						"zmaster587.advancedRocketry.world.biome.BiomeGenMoon"));
	}

	@Test
	public void uniqueSourceIdentifiesModWithUnrelatedPackage() {
		assertEquals("strangebiomes", BiomeModIdResolver.selectBestModId(
				"org.example.world.BiomeStrange", "/mods/strange.jar",
				Collections.singletonList(new Candidate("strangebiomes",
						"/mods/strange.jar", "com.author.StrangeMod",
						Collections.<String>emptyList()))));
	}

	@Test
	public void mainPackageDisambiguatesSharedJar() {
		Candidate core = new Candidate("examplecore", "/mods/example.jar",
				"com.example.core.CoreMod", Arrays.asList("com.example",
						"com.example.biome"));
		Candidate biomes = new Candidate("examplebiomes",
				"/mods/example.jar", "com.example.biome.BiomeMod",
				Arrays.asList("com.example", "com.example.biome"));

		assertEquals("examplebiomes", BiomeModIdResolver.selectBestModId(
				"com.example.biome.ForestBiome", "/mods/example.jar",
				Arrays.asList(core, biomes)));
	}

	@Test
	public void ownedPackageWorksWhenCodeSourceIsUnavailable() {
		Candidate unrelated = new Candidate("unrelated", null,
				"com.other.UnrelatedMod", Arrays.asList("com.other"));
		Candidate owner = new Candidate("owner", null,
				"org.owner.Main", Arrays.asList("org.owner.biomes"));

		assertEquals("owner", BiomeModIdResolver.selectBestModId(
				"org.owner.biomes.CrystalBiome", null,
				Arrays.asList(unrelated, owner)));
	}

	@Test
	public void ambiguousSharedJarFailsClosed() {
		Candidate first = new Candidate("first", "/mods/shared.jar",
				null, Arrays.asList("shared.biomes"));
		Candidate second = new Candidate("second", "/mods/shared.jar",
				null, Arrays.asList("shared.biomes"));

		assertEquals("unknown", BiomeModIdResolver.selectBestModId(
				"shared.biomes.SharedBiome", "/mods/shared.jar",
				Arrays.asList(first, second)));
	}
}
