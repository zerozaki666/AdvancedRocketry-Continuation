package zmaster587.advancedRocketry.integration.opencomputers;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Full-cube equivalent of an OpenComputers cable, with no component. */
public class TileAirtightOpenComputersCable extends TileEntity
		implements Environment {

	private final Node node = Network.newNode(this, Visibility.None).create();

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && node != null && node.network() == null)
			Network.joinOrCreateNetwork(this);
	}

	@Override
	public Node node() {
		return node;
	}

	@Override
	public void onConnect(Node connectedNode) {
	}

	@Override
	public void onDisconnect(Node disconnectedNode) {
	}

	@Override
	public void onMessage(Message message) {
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		if(node != null)
			node.remove();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if(node != null)
			node.remove();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		if(node != null && nbt.hasKey("oc:node"))
			node.load(nbt.getCompoundTag("oc:node"));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		if(node != null) {
			NBTTagCompound nodeTag = new NBTTagCompound();
			node.save(nodeTag);
			nbt.setTag("oc:node", nodeTag);
		}
	}
}
