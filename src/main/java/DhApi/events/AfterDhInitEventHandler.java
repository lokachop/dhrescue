package DhApi.events;

import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import org.slf4j.Logger;

/**
 * @version 2023-6-23
 */
public class AfterDhInitEventHandler extends DhApiAfterDhInitEvent
{
	private static final Logger LOGGER = LogUtils.getLogger();


	@Override
	public void afterDistantHorizonsInit(DhApiEventParam<Void> event) {
		LOGGER.info("After DH Init event fired.");
		runDelayedDhApiMethod();
	}
	
	public static void runDelayedDhApiMethod()
	{
		boolean renderingEnabled = DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
		boolean configValueChanged = DhApi.Delayed.configs.graphics().renderingEnabled().setValue(renderingEnabled);
		if (!configValueChanged)
		{
			// The DH Config can be locked to prevent API users from modifying it.
			// Your mod may have to handle locked config values,
			// but don't bug the user, that won't make anyone happy.
			LOGGER.warn("Config value locked. Unable set the value to: "+renderingEnabled);
		}
		
		LOGGER.info("DH Rendering: "+renderingEnabled);
	}
	
}
