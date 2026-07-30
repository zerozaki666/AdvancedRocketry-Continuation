package zmaster587.advancedRocketry.tile.multiblock.energy;

/**
 * Stable, network-visible states for the black hole generator.
 *
 * <p>The explicit codes are part of the save/network compatibility contract.
 * Do not replace them with enum ordinals.</p>
 */
public enum BlackHoleGeneratorState {
	INCOMPLETE(0, "msg.blackholegenerator.state.incomplete"),
	DISABLED(1, "msg.blackholegenerator.state.disabled"),
	CONFIG_DISABLED(2, "msg.blackholegenerator.state.configdisabled"),
	WRONG_DIMENSION(3, "msg.blackholegenerator.state.wrongdimension"),
	NO_STATION(4, "msg.blackholegenerator.state.nostation"),
	IN_WARP(5, "msg.blackholegenerator.state.inwarp"),
	NOT_ORBITING_BLACK_HOLE(6, "msg.blackholegenerator.state.notorbiting"),
	NO_OUTPUT(7, "msg.blackholegenerator.state.nooutput"),
	OUTPUT_FULL(8, "msg.blackholegenerator.state.outputfull"),
	NO_VALID_INPUT(9, "msg.blackholegenerator.state.noinput"),
	RUNNING(10, "msg.blackholegenerator.state.running");

	private final byte networkCode;
	private final String localizationKey;

	private BlackHoleGeneratorState(int networkCode, String localizationKey) {
		this.networkCode = (byte)networkCode;
		this.localizationKey = localizationKey;
	}

	public byte getNetworkCode() {
		return networkCode;
	}

	public String getLocalizationKey() {
		return localizationKey;
	}

	public static BlackHoleGeneratorState fromNetworkCode(byte code) {
		switch(code) {
		case 0:
			return INCOMPLETE;
		case 1:
			return DISABLED;
		case 2:
			return CONFIG_DISABLED;
		case 3:
			return WRONG_DIMENSION;
		case 4:
			return NO_STATION;
		case 5:
			return IN_WARP;
		case 6:
			return NOT_ORBITING_BLACK_HOLE;
		case 7:
			return NO_OUTPUT;
		case 8:
			return OUTPUT_FULL;
		case 9:
			return NO_VALID_INPUT;
		case 10:
			return RUNNING;
		default:
			return INCOMPLETE;
		}
	}
}
