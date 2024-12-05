package DhApi.events;

import DhApi.GenericRendering;
import DhApi.CustomWorldGenerator;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import org.slf4j.Logger;

/** @version 2024-08-02 */
public class DhLevelLoadEventHandler extends DhApiLevelLoadEvent
{
	private static final Logger LOGGER = LogUtils.getLogger();
	
	@Override
	public void onLevelLoad(DhApiEventParam<EventParam> event)
	{
		IDhApiLevelWrapper levelWrapper = event.value.levelWrapper;
		LOGGER.info("DH Level: ["+levelWrapper.getDimensionType()+"] loaded.");
		
		
		CustomWorldGenerator.registerForLevel(levelWrapper);
		GenericRendering.registerForLevel(levelWrapper);
	}
	
}
