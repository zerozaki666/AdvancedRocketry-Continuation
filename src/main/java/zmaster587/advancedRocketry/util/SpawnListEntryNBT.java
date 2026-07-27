package zmaster587.advancedRocketry.util;

import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.biome.BiomeGenBase;
import org.w3c.dom.DOMException;

public class SpawnListEntryNBT extends BiomeGenBase.SpawnListEntry {

    NBTTagCompound nbt;
    String nbtString;
    private final String entityIdentifier;

    public SpawnListEntryNBT(Class<? extends EntityLiving> entityclassIn, int weight, int groupCountMin,
                             int groupCountMax) {
        this(entityclassIn, weight, groupCountMin, groupCountMax, null);
    }

    public SpawnListEntryNBT(Class<? extends EntityLiving> entityclassIn, int weight,
                             int groupCountMin, int groupCountMax, String entityIdentifier) {
        super(entityclassIn, weight, groupCountMin, groupCountMax);
        nbt = null;
        nbtString = "";
        this.entityIdentifier = entityIdentifier;
    }

    public void setNbt(String nbtString) throws DOMException, NBTException {

        this.nbtString = nbtString == null ? "" : nbtString.trim();
        if(this.nbtString.isEmpty())
            this.nbt = null;
        else {
            NBTBase parsed = JsonToNBT.func_150315_a(this.nbtString);
            if(!(parsed instanceof NBTTagCompound))
                throw new NBTException(
                        "Spawn NBT must be a compound tag enclosed in braces");
            this.nbt = (NBTTagCompound)parsed;
        }
    }

    public String getNBTString() {
        return this.nbtString;
    }

    public NBTTagCompound getNbt() {
        return nbt == null ? null : (NBTTagCompound)nbt.copy();
    }

    public String getEntityIdentifier() {
        return entityIdentifier;
    }
}
