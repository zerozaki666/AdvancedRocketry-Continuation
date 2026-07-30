package zmaster587.advancedRocketry.api.event;

import cpw.mods.fml.common.eventhandler.Cancelable;
import net.minecraftforge.event.entity.EntityEvent;
import zmaster587.advancedRocketry.entity.EntityRocket;

/**
 * Fired around a free-space black-hole capture.
 */
public abstract class BlackHoleCaptureEvent extends EntityEvent {
	private final String bodyId;

	protected BlackHoleCaptureEvent(EntityRocket rocket, String bodyId) {
		super(rocket);
		this.bodyId = bodyId;
	}

	public EntityRocket getRocket() {
		return (EntityRocket)entity;
	}

	public String getBodyId() {
		return bodyId;
	}

	@Cancelable
	public static final class Pre extends BlackHoleCaptureEvent {
		public Pre(EntityRocket rocket, String bodyId) {
			super(rocket, bodyId);
		}
	}

	public static final class Post extends BlackHoleCaptureEvent {
		public Post(EntityRocket rocket, String bodyId) {
			super(rocket, bodyId);
		}
	}
}
