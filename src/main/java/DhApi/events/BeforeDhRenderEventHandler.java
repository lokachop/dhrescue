package DhApi.events;

import DhApi.TerrainDataAccess;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import org.slf4j.Logger;

/**
 * @version 2024-08-02
 */
public class BeforeDhRenderEventHandler extends DhApiBeforeRenderEvent
{
	private static final Logger LOGGER = LogUtils.getLogger();
	
	
	
	@Override 
	public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event)
	{
		TerrainDataAccess.INSTANCE.printRaycastResultToChatAsync();
	}
	
}
