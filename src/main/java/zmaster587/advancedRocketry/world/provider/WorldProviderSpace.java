package zmaster587.advancedRocketry.world.provider;

/**
 * Legacy binary-compatible name for the space-station provider.
 *
 * <p>Existing 1.7.10 addons may extend or test this class directly, so the
 * original name must retain its station semantics. New internal code may use
 * {@link WorldProviderStation} when it needs to make that meaning explicit.</p>
 */
@Deprecated
public class WorldProviderSpace extends WorldProviderStation {
}
