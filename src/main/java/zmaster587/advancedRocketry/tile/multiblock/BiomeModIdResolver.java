package zmaster587.advancedRocketry.tile.multiblock;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.world.biome.BiomeGenBase;
import zmaster587.advancedRocketry.api.Constants;

/** Resolves the owning mod id for legacy 1.7.10 biome classes. */
public final class BiomeModIdResolver {

	private static final String UNKNOWN_MOD_ID = "unknown";
	private static final ConcurrentMap<Class<?>, String> CACHE =
			new ConcurrentHashMap<Class<?>, String>();

	private BiomeModIdResolver() {
	}

	public static String resolve(BiomeGenBase biome) {
		if(biome == null)
			return UNKNOWN_MOD_ID;

		Class<?> biomeClass = biome.getClass();
		String cached = CACHE.get(biomeClass);
		if(cached != null)
			return cached;

		String resolved = resolveKnownNamespace(biomeClass.getName());
		if(resolved == null)
			resolved = selectBestModId(biomeClass.getName(),
					getClassSource(biomeClass), getLoadedCandidates());
		if(resolved == null)
			resolved = UNKNOWN_MOD_ID;

		String previous = CACHE.putIfAbsent(biomeClass, resolved);
		return previous == null ? resolved : previous;
	}

	static String resolveKnownNamespace(String className) {
		if(isInPackage(className, "net.minecraft"))
			return "minecraft";
		if(isInPackage(className, "zmaster587.advancedRocketry"))
			return Constants.modId;
		return null;
	}

	static String selectBestModId(String biomeClassName,
			String biomeSource, List<Candidate> candidates) {
		if(biomeClassName == null || candidates == null
				|| candidates.isEmpty())
			return UNKNOWN_MOD_ID;

		List<Candidate> sourceMatches = new ArrayList<Candidate>();
		if(biomeSource != null)
			for(Candidate candidate : candidates)
				if(sameSource(biomeSource, candidate.source))
					sourceMatches.add(candidate);

		List<Candidate> pool = sourceMatches.isEmpty()
				? candidates : sourceMatches;
		Candidate best = null;
		int bestScore = Integer.MIN_VALUE;
		boolean tied = false;
		for(Candidate candidate : pool) {
			int score = score(biomeClassName, candidate);
			if(score > bestScore) {
				best = candidate;
				bestScore = score;
				tied = false;
			}
			else if(score == bestScore && best != null
					&& !best.modId.equals(candidate.modId))
				tied = true;
		}

		if(best == null || tied)
			return UNKNOWN_MOD_ID;
		return best.modId;
	}

	private static int score(String biomeClassName, Candidate candidate) {
		int score = 0;
		String modPackage = packageName(candidate.mainClassName);
		if(isInPackage(biomeClassName, modPackage))
			score += 1000000 + modPackage.length();
		else
			score += commonPackageSegments(biomeClassName,
					candidate.mainClassName) * 1000;

		int ownedSpecificity = 0;
		for(String ownedPackage : candidate.ownedPackages)
			if(isInPackage(biomeClassName, ownedPackage))
				ownedSpecificity = Math.max(ownedSpecificity,
						ownedPackage.length());
		if(ownedSpecificity > 0)
			score += 100000 + ownedSpecificity;
		return score;
	}

	private static List<Candidate> getLoadedCandidates() {
		List<Candidate> candidates = new ArrayList<Candidate>();
		try {
			for(ModContainer container : Loader.instance()
					.getActiveModList()) {
				try {
					if(container == null || container.getModId() == null)
						continue;
					Object mod = container.getMod();
					String mainClassName = mod == null ? null
							: mod.getClass().getName();
					List<String> ownedPackages = container.getOwnedPackages();
					candidates.add(new Candidate(container.getModId(),
							normalizeFile(container.getSource()), mainClassName,
							ownedPackages));
				}
				catch(RuntimeException ignored) {
					// One unusual container must not hide every other owner.
				}
			}
		}
		catch(RuntimeException ignored) {
			return Collections.emptyList();
		}
		return candidates;
	}

	private static String getClassSource(Class<?> type) {
		try {
			CodeSource codeSource = type.getProtectionDomain().getCodeSource();
			if(codeSource == null)
				return null;
			URL location = codeSource.getLocation();
			if(location == null || !"file".equalsIgnoreCase(
					location.getProtocol()))
				return null;
			return normalizeFile(new File(location.toURI()));
		}
		catch(Exception ignored) {
			return null;
		}
	}

	private static String normalizeFile(File file) {
		if(file == null)
			return null;
		try {
			return file.getCanonicalPath();
		}
		catch(Exception ignored) {
			return file.getAbsolutePath();
		}
	}

	private static boolean sameSource(String first, String second) {
		return first != null && second != null
				&& first.equalsIgnoreCase(second);
	}

	private static boolean isInPackage(String className,
			String packageName) {
		return className != null && packageName != null
				&& !packageName.isEmpty()
				&& (className.equals(packageName)
						|| className.startsWith(packageName + "."));
	}

	private static String packageName(String className) {
		if(className == null)
			return "";
		int separator = className.lastIndexOf('.');
		return separator < 0 ? "" : className.substring(0, separator);
	}

	private static int commonPackageSegments(String firstClass,
			String secondClass) {
		String[] first = packageName(firstClass).split("\\.");
		String[] second = packageName(secondClass).split("\\.");
		int count = 0;
		while(count < first.length && count < second.length
				&& first[count].equals(second[count]))
			count++;
		return count;
	}

	static final class Candidate {
		private final String modId;
		private final String source;
		private final String mainClassName;
		private final List<String> ownedPackages;

		Candidate(String modId, String source, String mainClassName,
				List<String> ownedPackages) {
			this.modId = modId == null ? UNKNOWN_MOD_ID : modId;
			this.source = source;
			this.mainClassName = mainClassName;
			this.ownedPackages = ownedPackages == null
					? Collections.<String>emptyList() : ownedPackages;
		}
	}
}
