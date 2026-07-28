package zmaster587.advancedRocketry.network;

import java.io.IOException;

import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.SatelliteRegistry;
import zmaster587.advancedRocketry.api.satellite.SatelliteBase;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.libVulpes.network.BasePacket;
import zmaster587.libVulpes.util.INetworkMachine;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class PacketSatellite extends BasePacket {

	SatelliteBase machine;

	NBTTagCompound nbt;

	byte packetId;

	public PacketSatellite() {
		nbt = new NBTTagCompound();
	};

	public PacketSatellite(SatelliteBase machine) {
		this();
		this.machine = machine;
	}


	@Override
	public void write(ByteBuf outline) {
		PacketBuffer packetBuffer = new PacketBuffer(outline);
		NBTTagCompound nbt = new NBTTagCompound();
		machine.writeToNBT(nbt);
		
		try {
			packetBuffer.writeNBTTagCompoundToBuffer(nbt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void readClient(ByteBuf in) {
		
		PacketBuffer packetBuffer = new PacketBuffer(in);
		NBTTagCompound nbt;
		
		//TODO: error handling
		try {
			nbt = packetBuffer.readNBTTagCompoundFromBuffer();
			if(nbt == null) {
				AdvancedRocketry.logger.warn("Received an empty satellite packet");
				return;
			}
			SatelliteBase satellite = SatelliteRegistry.createFromNBT(nbt);
			if(satellite == null) {
				AdvancedRocketry.logger.warn("Received satellite data with an unknown type");
				return;
			}
			DimensionProperties properties =
					zmaster587.advancedRocketry.dimension.DimensionManager
							.getInstance()
							.getDimensionProperties(satellite.getDimensionId());
			if(properties == null) {
				AdvancedRocketry.logger.warn("Received satellite data for an unknown dimension");
				return;
			}
			properties.addSatallite(satellite);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	@Override
	public void read(ByteBuf in) {
		//Should never happen
		
	}

	public void executeClient(EntityPlayer player) {
	}

	public void executeServer(EntityPlayerMP player) {
	}

	public void execute(EntityPlayer player, Side side) {
	}

}
