package zmaster587.advancedRocketry.integration.opencomputers;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * Strongly typed OC node operations kept outside normal tile lifecycle code.
 * Callers retain nodes as {@link Object} so their tile classes remain safe to
 * load when OpenComputers is absent.
 */
public final class OpenComputersNetworkNodeSupport {

	private OpenComputersNetworkNodeSupport() {
	}

	public static Object create(Environment host, String componentName,
			NBTTagCompound savedNode) {
		Node node = Network.newNode(host, Visibility.Network)
				.withComponent(componentName, Visibility.Network).create();
		if(node != null && savedNode != null)
			node.load(savedNode);
		return node;
	}

	public static void join(TileEntity tile, Object rawNode) {
		Node node = asNode(rawNode);
		if(node != null && node.network() == null)
			Network.joinOrCreateNetwork(tile);
	}

	public static void remove(Object rawNode) {
		Node node = asNode(rawNode);
		if(node != null)
			node.remove();
	}

	public static NBTTagCompound save(Object rawNode) {
		NBTTagCompound tag = new NBTTagCompound();
		Node node = asNode(rawNode);
		if(node != null)
			node.save(tag);
		return tag;
	}

	public static void sendSignal(Object rawNode, String signal,
			Object... arguments) {
		Node node = asNode(rawNode);
		if(node == null || node.network() == null)
			return;

		Object[] payload = new Object[arguments.length + 2];
		payload[0] = signal;
		payload[1] = node.address();
		System.arraycopy(arguments, 0, payload, 2, arguments.length);
		node.sendToReachable("computer.signal", payload);
	}

	private static Node asNode(Object rawNode) {
		return rawNode instanceof Node ? (Node)rawNode : null;
	}
}
