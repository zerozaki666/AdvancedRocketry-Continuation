package zmaster587.advancedRocketry.entity;

import net.minecraft.nbt.NBTTagCompound;
import zmaster587.advancedRocketry.util.StorageChunk;

public class LeveledRocketPart {
    private final StorageChunk storage;
    private final long fuelRemaining;
    private final boolean activated;
    private final int level;

    public LeveledRocketPart(StorageChunk storage, long fuelRemaining, boolean activated, int level) {
        this.storage = storage;
        this.fuelRemaining = fuelRemaining;
        this.activated = activated;
        this.level = level;
    }

    public StorageChunk getStorage() {
        return storage;
    }

    public long getFuelRemaining() {
        return fuelRemaining;
    }

    public boolean isActivated() {
        return activated;
    }

    public int getLevel() {
        return level;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("fuelRemaining", fuelRemaining);
        nbt.setBoolean("activated", activated);
        nbt.setInteger("level", level);
        if(storage != null) {
            NBTTagCompound storageNbt = new NBTTagCompound();
            storage.writeToNBT(storageNbt);
            nbt.setTag("storage", storageNbt);
        }
        return nbt;
    }

    public static LeveledRocketPart readFromNBT(NBTTagCompound nbt) {
        long fuelRemaining = nbt.getLong("fuelRemaining");
        boolean activated = nbt.hasKey("activated")
                ? nbt.getBoolean("activated")
                : nbt.getBoolean("isActived");
        int level = nbt.getInteger("level");
        StorageChunk storage = new StorageChunk();
        storage.readFromNBT(nbt.hasKey("storage") ? nbt.getCompoundTag("storage") : nbt);
        return new LeveledRocketPart(storage, fuelRemaining, activated, level);
    }
}
