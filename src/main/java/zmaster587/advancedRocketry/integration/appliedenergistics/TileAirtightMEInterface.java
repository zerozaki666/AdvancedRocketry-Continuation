package zmaster587.advancedRocketry.integration.appliedenergistics;

import java.util.EnumSet;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import zmaster587.advancedRocketry.block.IAirtightInterfaceNeighborAware;
import zmaster587.advancedRocketry.block.IAirtightInterfacePlacementAware;

/** API-only AE2 cable node used by the airtight smart interfaces. */
public abstract class TileAirtightMEInterface extends TileEntity
		implements IGridHost, IGridBlock, IAirtightInterfaceNeighborAware,
		IAirtightInterfacePlacementAware {

	private static final String NODE_NAME = "airtightMEInterface";
	private static final String NBT_PENDING_PLAYER = "airtightMEPlayerId";

	private final boolean dense;
	private IGridNode gridNode;
	private NBTTagCompound pendingNodeData;
	private int pendingPlayerId = -1;
	private boolean ready;

	protected TileAirtightMEInterface(boolean dense) {
		this.dense = dense;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void updateEntity() {
		if(worldObj != null && !worldObj.isRemote)
			ready = true;
		ensureNode();
	}

	private IGridNode ensureNode() {
		if(gridNode == null && ready && worldObj != null && !worldObj.isRemote
				&& !isInvalid()) {
			gridNode = AEApi.instance().createGridNode(this);
			if(gridNode != null) {
				if(pendingNodeData != null) {
					gridNode.loadFromNBT(NODE_NAME, pendingNodeData);
					pendingNodeData = null;
				}
				if(pendingPlayerId >= 0) {
					gridNode.setPlayerID(pendingPlayerId);
					pendingPlayerId = -1;
				}
				gridNode.updateState();
			}
		}
		return gridNode;
	}

	@Override
	public void onAdjacentBlockChanged() {
		IGridNode node = ensureNode();
		if(node != null)
			node.updateState();
	}

	@Override
	public void onPlacedBy(EntityLivingBase placer) {
		if(worldObj == null || worldObj.isRemote
				|| !(placer instanceof EntityPlayer))
			return;
		pendingPlayerId = AEApi.instance().registries().players()
				.getID((EntityPlayer)placer);
		if(gridNode != null) {
			gridNode.setPlayerID(pendingPlayerId);
			pendingPlayerId = -1;
		}
	}

	@Override
	public IGridNode getGridNode(ForgeDirection direction) {
		return ensureNode();
	}

	@Override
	public AECableType getCableConnectionType(ForgeDirection direction) {
		return dense ? AECableType.DENSE : AECableType.SMART;
	}

	@Override
	public void securityBreak() {
		if(worldObj != null)
			worldObj.setBlockToAir(xCoord, yCoord, zCoord);
	}

	@Override
	public double getIdlePowerUsage() {
		return 0;
	}

	@Override
	public EnumSet<GridFlags> getFlags() {
		return dense ? EnumSet.of(GridFlags.DENSE_CAPACITY)
				: EnumSet.of(GridFlags.PREFERRED);
	}

	@Override
	public boolean isWorldAccessible() {
		return true;
	}

	@Override
	public DimensionalCoord getLocation() {
		return new DimensionalCoord(this);
	}

	@Override
	public AEColor getGridColor() {
		return AEColor.Transparent;
	}

	@Override
	public void onGridNotification(GridNotification notification) {
		if(worldObj != null)
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	@Override
	public void setNetworkStatus(IGrid grid, int channelsInUse) {
	}

	@Override
	public EnumSet<ForgeDirection> getConnectableSides() {
		return EnumSet.allOf(ForgeDirection.class);
	}

	@Override
	public IGridHost getMachine() {
		return this;
	}

	@Override
	public void gridChanged() {
	}

	@Override
	public ItemStack getMachineRepresentation() {
		Block block = getBlockType();
		return block == null || block == Blocks.air ? null
				: new ItemStack(block);
	}

	@Override
	public void onChunkUnload() {
		destroyNode();
		super.onChunkUnload();
	}

	@Override
	public void invalidate() {
		destroyNode();
		super.invalidate();
	}

	private void destroyNode() {
		ready = false;
		if(gridNode != null) {
			gridNode.destroy();
			gridNode = null;
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		pendingNodeData = (NBTTagCompound)nbt.copy();
		pendingPlayerId = nbt.hasKey(NBT_PENDING_PLAYER)
				? nbt.getInteger(NBT_PENDING_PLAYER) : -1;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		if(gridNode != null)
			gridNode.saveToNBT(NODE_NAME, nbt);
		else if(pendingNodeData != null && pendingNodeData.hasKey(NODE_NAME))
			nbt.setTag(NODE_NAME,
					pendingNodeData.getCompoundTag(NODE_NAME).copy());
		if(pendingPlayerId >= 0)
			nbt.setInteger(NBT_PENDING_PLAYER, pendingPlayerId);
	}
}
