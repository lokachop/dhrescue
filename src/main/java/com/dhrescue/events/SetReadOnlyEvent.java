package com.dhrescue.events;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

/** @version 2024-08-02 */
public class SetReadOnlyEvent extends DhApiLevelLoadEvent
{

    @Override
    public void onLevelLoad(DhApiEventParam<EventParam> event)
    {
        IDhApiLevelWrapper levelWrapper = event.value.levelWrapper;

        DhApi.Delayed.worldProxy.setReadOnly(true);
    }

}