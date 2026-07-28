package zmaster587.advancedRocketry.thread;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.block.rocket.ILeveledPartsDivider;
import zmaster587.advancedRocketry.entity.LeveledRocketPart;
import zmaster587.advancedRocketry.tile.TileGuidanceComputer;
import zmaster587.advancedRocketry.util.StorageChunk;
import zmaster587.libVulpes.util.BlockPosition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds the component list used by the experimental multistage-rocket API.
 *
 * <p>The development implementation used unsynchronised maps and shared traversal
 * state.  A result could therefore leak between rockets, and
 * {@code getResultAndRemove} always returned {@code null}.  This version keeps
 * every traversal local to one task and uses a blocking queue so the daemon
 * does not busy-spin while idle.</p>
 */
public class RocketStructureThread extends Thread {

    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final BlockingQueue<RocketTask> tasks = new LinkedBlockingQueue<>();
    private final ConcurrentMap<UUID, RocketResult> results =
            new ConcurrentHashMap<UUID, RocketResult>();
    private final ConcurrentMap<UUID, Long> generations =
            new ConcurrentHashMap<UUID, Long>();
    private final AtomicLong nextGeneration = new AtomicLong();

    public RocketStructureThread(String name) {
        super(name);
        setDaemon(true);
    }

    @Override
    public void run() {
        while(!isInterrupted()) {
            RocketTask task = null;
            try {
                task = tasks.take();
                ArrayList<LeveledRocketPart> result = calculateLeveledRocketParts(task.storage);
                Long currentGeneration = generations.get(task.id);
                if(currentGeneration != null
                        && currentGeneration.longValue() == task.generation)
                    results.put(task.id,
                            new RocketResult(task.generation, result));
            } catch(InterruptedException interrupted) {
                interrupt();
            } catch(RuntimeException exception) {
                AdvancedRocketry.logger.error("Unable to calculate multistage rocket structure", exception);
                if(task != null) {
                    Long currentGeneration = generations.get(task.id);
                    if(currentGeneration != null
                            && currentGeneration.longValue() == task.generation)
                        results.put(task.id, new RocketResult(task.generation,
                                new ArrayList<LeveledRocketPart>()));
                }
            }
        }
    }

    /**
     * Queues a detached snapshot. Callers must invoke this from the server
     * thread, which is the only point where live tile entities are serialized.
     */
    public void addATask(UUID id, StorageChunk entireRocket) {
        if(id == null || entireRocket == null)
            return;

        StorageChunk snapshot = entireRocket.copyForAsyncAnalysis();
        long generation = nextGeneration.incrementAndGet();
        generations.put(id, generation);
        results.remove(id);
        tasks.remove(new RocketTask(id, null));
        tasks.offer(new RocketTask(id, snapshot, generation));
    }

    public void cancelTask(UUID id) {
        if(id == null)
            return;

        generations.remove(id);
        results.remove(id);
        tasks.remove(new RocketTask(id, null));
    }

    public boolean isTaskCompleted(UUID id) {
        RocketResult result = results.get(id);
        Long generation = generations.get(id);
        return result != null && generation != null
                && result.generation == generation.longValue();
    }

    public ArrayList<LeveledRocketPart> getResult(UUID id) {
        RocketResult result = results.get(id);
        Long generation = generations.get(id);
        return result == null || generation == null
                || result.generation != generation.longValue()
                        ? null
                        : new ArrayList<LeveledRocketPart>(result.parts);
    }

    public ArrayList<LeveledRocketPart> getResultAndRemove(UUID id) {
        RocketResult result = results.get(id);
        Long generation = generations.get(id);
        if(result == null || generation == null
                || result.generation != generation.longValue())
            return null;
        if(!results.remove(id, result))
            return null;
        generations.remove(id, generation);
        return result.parts;
    }

    public void shutdown() {
        tasks.clear();
        results.clear();
        generations.clear();
        interrupt();
    }

    private ArrayList<LeveledRocketPart> calculateLeveledRocketParts(StorageChunk entireRocket) {
        List<TileGuidanceComputer> computers = new ArrayList<>();
        for(TileEntity tile : entireRocket.getTileEntityList()) {
            if(tile instanceof TileGuidanceComputer)
                computers.add((TileGuidanceComputer) tile);
        }

        if(computers.size() != 1) {
            AdvancedRocketry.logger.warn(
                    "Skipping multistage split: expected exactly one guidance computer, found {}",
                    computers.size());
            return new ArrayList<>();
        }

        Set<BlockPosition> visited = new HashSet<>();
        ArrayDeque<BlockPosition> stageSeeds = new ArrayDeque<>();
        stageSeeds.add(positionOf(computers.get(0)));

        ArrayList<LeveledRocketPart> parts = new ArrayList<>();
        int level = 0;

        while(!stageSeeds.isEmpty()) {
            BlockPosition seed = stageSeeds.removeFirst();
            if(visited.contains(seed) || isAir(entireRocket, seed))
                continue;

            StageResult stage = collectStage(entireRocket, seed, visited);
            if(stage.blocks.isEmpty())
                continue;

            parts.add(new LeveledRocketPart(
                    StorageChunk.divideStorage(entireRocket, stage.blocks),
                    0,
                    level == 0,
                    level++));

            for(BlockPosition next : stage.nextStageSeeds) {
                if(!visited.contains(next))
                    stageSeeds.addLast(next);
            }
        }

        return parts;
    }

    private StageResult collectStage(StorageChunk storage, BlockPosition seed, Set<BlockPosition> visited) {
        ArrayDeque<BlockPosition> open = new ArrayDeque<>();
        ArrayList<BlockPosition> blocks = new ArrayList<>();
        Set<BlockPosition> nextSeeds = new HashSet<>();
        open.add(seed);

        while(!open.isEmpty()) {
            BlockPosition position = open.removeFirst();
            if(visited.contains(position) || isAir(storage, position))
                continue;

            visited.add(position);
            blocks.add(position);

            Block block = storage.getBlock(position.x, position.y, position.z);
            boolean divider = block instanceof ILeveledPartsDivider;

            for(int[] offset : NEIGHBOURS) {
                BlockPosition neighbour = new BlockPosition(
                        position.x + offset[0],
                        position.y + offset[1],
                        position.z + offset[2]);

                if(visited.contains(neighbour) || isAir(storage, neighbour))
                    continue;

                if(divider)
                    nextSeeds.add(neighbour);
                else
                    open.addLast(neighbour);
            }
        }

        return new StageResult(blocks, nextSeeds);
    }

    private static BlockPosition positionOf(TileEntity tile) {
        return new BlockPosition(tile.xCoord, tile.yCoord, tile.zCoord);
    }

    private static boolean isAir(StorageChunk storage, BlockPosition position) {
        Block block = storage.getBlock(position.x, position.y, position.z);
        return block == null || block == Blocks.air;
    }

    private static final class StageResult {
        private final ArrayList<BlockPosition> blocks;
        private final Set<BlockPosition> nextStageSeeds;

        private StageResult(ArrayList<BlockPosition> blocks, Set<BlockPosition> nextStageSeeds) {
            this.blocks = blocks;
            this.nextStageSeeds = nextStageSeeds;
        }
    }

    private static final class RocketTask {
        private final UUID id;
        private final StorageChunk storage;
        private final long generation;

        private RocketTask(UUID id, StorageChunk storage) {
            this(id, storage, 0);
        }

        private RocketTask(UUID id, StorageChunk storage, long generation) {
            this.id = id;
            this.storage = storage;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof RocketTask && id.equals(((RocketTask) object).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class RocketResult {
        private final long generation;
        private final ArrayList<LeveledRocketPart> parts;

        private RocketResult(long generation,
                ArrayList<LeveledRocketPart> parts) {
            this.generation = generation;
            this.parts = parts;
        }
    }
}
