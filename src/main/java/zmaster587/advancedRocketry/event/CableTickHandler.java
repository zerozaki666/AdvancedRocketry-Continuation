package zmaster587.advancedRocketry.event;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.event.world.ChunkEvent;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.Configuration;
import zmaster587.advancedRocketry.cable.NetworkRegistry;
import zmaster587.advancedRocketry.dimension.sim.AdvancedRocketryUniverse;
import zmaster587.advancedRocketry.dimension.sim.CelestialEncounterService;
import zmaster587.advancedRocketry.dimension.sim.CelestialEncounterService.Encounter;
import zmaster587.advancedRocketry.dimension.sim.CelestialEncounterService.EncounterType;
import zmaster587.advancedRocketry.dimension.sim.SimBodySnapshot;
import zmaster587.advancedRocketry.dimension.sim.SimBodyType;
import zmaster587.advancedRocketry.dimension.sim.SimUniverse;
import zmaster587.advancedRocketry.entity.EntityRocket;
import zmaster587.advancedRocketry.tile.cables.TilePipe;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;

public class CableTickHandler {

	private static final double GRAVITY_EPSILON = 0.25D;
	private final Map<String, Long> blackHoleWarningTimes =
			new HashMap<String, Long>();
	private long lastBlackHoleWarningPrune = Long.MIN_VALUE;

	@SubscribeEvent
	public void onTick(TickEvent.ServerTickEvent tick) {
		try {
			if(tick.phase == Phase.START) {
				World overworld = DimensionManager.getWorld(0);
				if(overworld != null)
					AdvancedRocketryUniverse.tick(overworld.getTotalWorldTime());
			}
			if(tick.phase == Phase.END) {
				NetworkRegistry.dataNetwork.tickAllNetworks();
				NetworkRegistry.energyNetwork.tickAllNetworks();
				NetworkRegistry.liquidNetwork.tickAllNetworks();
			}
		} catch (ConcurrentModificationException e) {
			e.printStackTrace();
		}
	}

	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event) {
		if(event.world.isRemote
				|| event.world.provider.dimensionId != Configuration.freeSpaceDimId)
			return;

		List entities = new ArrayList(event.world.loadedEntityList);
		List<SimBodySnapshot> bodies =
				SimUniverse.getInstance().getBodySnapshots();
		for(Object object : entities) {
			if(!(object instanceof EntityRocket))
				continue;

			EntityRocket rocket = (EntityRocket)object;
			if(rocket.isDead || !rocket.isInFlight())
				continue;

			if(event.phase == Phase.START)
				applyBlackHoleInfluence(rocket, bodies,
						event.world.getTotalWorldTime());
			else if(event.phase == Phase.END)
				handleEncounter(rocket, bodies);
		}
	}

	private void applyBlackHoleInfluence(EntityRocket rocket,
			List<SimBodySnapshot> bodies, long worldTime) {
		pruneBlackHoleWarnings(worldTime);
		if(!Configuration.blackHoleFreeSpaceInteraction.hasWarning()
				&& !Configuration.blackHoleFreeSpaceInteraction.hasGravity())
			return;

		double accelerationX = 0D;
		double accelerationY = 0D;
		double accelerationZ = 0D;
		for(SimBodySnapshot body : bodies) {
			if(body.getBodyType() != SimBodyType.BLACK_HOLE)
				continue;

			double dx = body.getX()-rocket.posX;
			double dy = body.getY()-rocket.posY;
			double dz = body.getZ()-rocket.posZ;
			double distanceSquared = dx*dx + dy*dy + dz*dz;
			if(!finite(distanceSquared))
				continue;
			double distance = Math.sqrt(Math.max(0D, distanceSquared));

			if(Configuration.blackHoleFreeSpaceInteraction.hasWarning()
					&& body.getWarningRadius() > 0D
					&& distance <= body.getWarningRadius())
				sendBlackHoleWarning(rocket, body, worldTime, distance,
						-dx, -dy, -dz);

			if(!Configuration.blackHoleFreeSpaceInteraction.hasGravity()
					|| body.getInfluenceRadius() <= 0D
					|| distance > body.getInfluenceRadius()
					|| distance <= 1.0E-9D)
				continue;

			double acceleration = Configuration.blackHoleGravityConstant
					*Math.max(0D, body.getMass())
					/Math.max(distanceSquared,
							GRAVITY_EPSILON*GRAVITY_EPSILON);
			acceleration = Math.min(Math.max(0D,
					Configuration.blackHoleMaxAcceleration),
					Math.max(0D, acceleration));
			if(!finite(acceleration))
				continue;

			double scale = acceleration/distance;
			accelerationX += dx*scale;
			accelerationY += dy*scale;
			accelerationZ += dz*scale;
		}

		double accelerationSquared = accelerationX*accelerationX
				+ accelerationY*accelerationY
				+ accelerationZ*accelerationZ;
		if(!finite(accelerationSquared) || accelerationSquared <= 0D)
			return;

		double maximumAcceleration = Math.max(0D,
				Configuration.blackHoleMaxAcceleration);
		if(accelerationSquared > maximumAcceleration*maximumAcceleration) {
			double scale = maximumAcceleration
					/Math.sqrt(accelerationSquared);
			accelerationX *= scale;
			accelerationY *= scale;
			accelerationZ *= scale;
		}
		rocket.motionX += accelerationX;
		rocket.motionY += accelerationY;
		rocket.motionZ += accelerationZ;
		clampRocketVelocity(rocket);
	}

	private void sendBlackHoleWarning(EntityRocket rocket,
			SimBodySnapshot body, long worldTime, double distance,
			double exitX, double exitY, double exitZ) {
		if(!(rocket.riddenByEntity instanceof EntityPlayerMP))
			return;

		String key = rocket.getUniqueID().toString() + ":" + body.getId();
		Long lastWarning = blackHoleWarningTimes.get(key);
		int interval = Math.max(1, Configuration.blackHoleWarningInterval);
		if(lastWarning != null && worldTime >= lastWarning.longValue()
				&& worldTime-lastWarning.longValue() < interval)
			return;
		blackHoleWarningTimes.put(key, worldTime);

		double length = Math.sqrt(exitX*exitX + exitY*exitY + exitZ*exitZ);
		if(length > 1.0E-9D) {
			exitX /= length;
			exitY /= length;
			exitZ /= length;
		}
		((EntityPlayerMP)rocket.riddenByEntity).addChatMessage(
				new ChatComponentTranslation("msg.blackhole.warning",
						body.getName(), Integer.valueOf((int)Math.ceil(distance)),
						String.format("%.2f", exitX),
						String.format("%.2f", exitY),
						String.format("%.2f", exitZ)));
	}

	private void pruneBlackHoleWarnings(long worldTime) {
		int interval = Math.max(1, Configuration.blackHoleWarningInterval);
		long retention = Math.max(1200L, (long)interval*2L);
		if(lastBlackHoleWarningPrune != Long.MIN_VALUE
				&& worldTime >= lastBlackHoleWarningPrune
				&& worldTime-lastBlackHoleWarningPrune < 200L)
			return;

		Iterator<Entry<String, Long>> iterator =
				blackHoleWarningTimes.entrySet().iterator();
		while(iterator.hasNext()) {
			long timestamp = iterator.next().getValue().longValue();
			if(timestamp > worldTime || worldTime-timestamp > retention)
				iterator.remove();
		}
		lastBlackHoleWarningPrune = worldTime;
	}

	private void handleEncounter(EntityRocket rocket,
			List<SimBodySnapshot> bodies) {
		Encounter encounter = CelestialEncounterService.findEarliest(bodies,
				rocket.getPreviousFreeSpaceX(),
				rocket.getPreviousFreeSpaceY(),
				rocket.getPreviousFreeSpaceZ(),
				rocket.posX, rocket.posY, rocket.posZ,
				Configuration.blackHoleFreeSpaceInteraction.hasCapture());
		if(encounter.getType() == EncounterType.LAND)
			rocket.landOnSimulatedBody(encounter.getBody().getDimensionId());
		else if(encounter.getType() == EncounterType.BLACK_HOLE_CAPTURE)
			rocket.captureByBlackHole(encounter.getBody().getId());
	}

	private static void clampRocketVelocity(EntityRocket rocket) {
		double speedSquared = rocket.motionX*rocket.motionX
				+ rocket.motionY*rocket.motionY
				+ rocket.motionZ*rocket.motionZ;
		double maximum = Math.max(0.05D, Configuration.maxSpaceRocketSpeed);
		if(!finite(speedSquared)) {
			rocket.motionX = rocket.motionY = rocket.motionZ = 0D;
		}
		else if(speedSquared > maximum*maximum) {
			double scale = maximum/Math.sqrt(speedSquared);
			rocket.motionX *= scale;
			rocket.motionY *= scale;
			rocket.motionZ *= scale;
		}
		rocket.velocityChanged = true;
	}

	private static boolean finite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	@SubscribeEvent
	public void chunkLoadedEvent(ChunkEvent.Load event) {

		Map map = event.getChunk().chunkTileEntityMap;
		Iterator<Entry> iter = map.entrySet().iterator();

		try {
			while(iter.hasNext()) {
				Object obj = iter.next().getValue();

				if(obj instanceof TilePipe) {
					((TilePipe)obj).markForUpdate();
				}
			}
		} catch ( ConcurrentModificationException e) {
			AdvancedRocketry.logger.warn("You have been visited by the rare pepe.. I mean error of pipes not loading, this is not good, some pipe systems may not work right away.  But it's better than a corrupt world");
		}

	}

	@SubscribeEvent
	public void onBlockBroken(BreakEvent event) {

		if(event.block.hasTileEntity(event.blockMetadata)) {

			TileEntity homeTile = event.world.getTileEntity(event.x , event.y, event.z);

			if(homeTile instanceof TilePipe) {

				//removed in favor of pipecount
				//boolean lastInNetwork =true;

				((TilePipe)homeTile).setDestroyed();
				((TilePipe)homeTile).setInvalid();

				int pipecount=0;

				for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
					TileEntity tile = event.world.getTileEntity(event.x + dir.offsetX, event.y + dir.offsetY, event.z + dir.offsetZ);
					if(tile instanceof TilePipe) 
						pipecount++;
				}
				//TODO: delete check if sinks/sources need removal
				if(pipecount > 1) {
					for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
						TileEntity tile = event.world.getTileEntity(event.x + dir.offsetX, event.y + dir.offsetY, event.z + dir.offsetZ);

						if(tile instanceof TilePipe) {
							((TilePipe) tile).getNetworkHandler().removeNetworkByID(((TilePipe) tile).getNetworkID());
							((TilePipe) tile).setInvalid();
							//lastInNetwork = false;
						}
						//HandlerCableNetwork.removeFromAllTypes((TilePipe)tile,event.world.getTileEntity(event.x, event.y, event.z));
					}
				}
				if(pipecount == 0) //lastInNetwork
					((TilePipe)homeTile).getNetworkHandler().removeNetworkByID(((TilePipe)homeTile).getNetworkID());
				((TilePipe)homeTile).markDirty();
			}
			else if(homeTile != null) {
				for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
					TileEntity tile = event.world.getTileEntity(event.x + dir.offsetX, event.y + dir.offsetY, event.z + dir.offsetZ);

					if(tile instanceof TilePipe) {
						((TilePipe)tile).getNetworkHandler().removeFromAllTypes((TilePipe)tile, homeTile);
					}
				}
			}
		}
	}
}
