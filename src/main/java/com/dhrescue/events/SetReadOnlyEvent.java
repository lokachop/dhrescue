package com.dhrescue.events;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;

/** Puts DH into read-only mode once a level loads, so it never writes over the restored chunks. */
public class SetReadOnlyEvent extends DhApiLevelLoadEvent
{

    @Override
    public void onLevelLoad(DhApiEventParam<EventParam> event)
    {
        DhApi.Delayed.worldProxy.setReadOnly(true);
    }

}