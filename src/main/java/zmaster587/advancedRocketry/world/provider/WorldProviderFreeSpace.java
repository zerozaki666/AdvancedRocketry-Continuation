package zmaster587.advancedRocketry.world.provider;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.IRenderHandler;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.AdvancedRocketryBiomes;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.client.render.planet.RenderFreeSpaceSky;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.world.ChunkProviderSpace;

/**
 * Empty interplanetary space used by manually flown rockets.
 *
 * <p>Space stations continue to use {@link WorldProviderStation}; keeping the
 * dimensions separate prevents station coordinates from colliding with the
 * simulated universe coordinate system.</p>
 */
public class WorldProviderFreeSpace extends WorldProviderPlanet {

	private IRenderHandler skyRenderer;
	private final DimensionProperties properties =
			(DimensionProperties)DimensionManager.defaultSpaceDimensionProperties.clone();

	@Override
	public double getHorizon() {
		return 0;
	}

	@Override
	public boolean isPlanet() {
		return false;
	}

	@Override
	public int getAverageGroundLevel() {
		return 0;
	}

	@Override
	public IChunkProvider createChunkGenerator() {
		return new ChunkProviderSpace(worldObj, worldObj.getSeed());
	}

	@Override
	public float getStarBrightness(float partialTicks) {
		return 1.0F;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IRenderHandler getSkyRenderer() {
		if(Configuration.spaceSkyOverride) {
			if(skyRenderer == null)
				skyRenderer = new RenderFreeSpaceSky();
			return skyRenderer;
		}
		return super.getSkyRenderer();
	}

	@Override
	public float getSunBrightness(float partialTicks) {
		return 1.0F;
	}

	@Override
	public float getAtmosphereDensity(int x, int z) {
		return 0;
	}

	@Override
	protected void registerWorldChunkManager() {
		worldObj.getWorldInfo().setTerrainType(AdvancedRocketry.spaceWorldType);
		worldChunkMgr = new WorldChunkManagerHell(AdvancedRocketryBiomes.spaceBiome, 0.0F);
		hasNoSky = false;
	}

	@Override
	public DimensionProperties getDimensionProperties(int x, int z) {
		return properties;
	}
}
