package com.dhrescue;


import com.dhrescue.command.RestoreInArea;
import com.dhrescue.events.SetReadOnlyEvent;
import com.dhrescue.networking.DHRescueNetworking;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the Fabric "Hello World" project
 * for the Distant Horizons API. <br>
 * This example is designed to be a jumping-off
 * point, so you can start using the DH API. <br><br>
 *
 * If at any point you have questions or issues,
 * feel free to stop by our Discord.
 * 
 * @version 2024-08-02
 */
public class DHRescue implements ModInitializer
{
    public static final Logger LOGGER = LoggerFactory.getLogger("dhrescue_main");
	public static final String NAME = "DHRescue";



	@Override
	public void onInitialize()
	{
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		
		// This means some DH api methods may not be fully set up yet.


		// Register networking shit
		// So the client command cna set blocks on the server
		// this is scary and unsafe, whatever...
		DHRescueNetworking.register();

		// Serverside only
		DHRescueNetworking.register_SV();

		// Register commands
		RestoreInArea.register();
		
		
		//========================//
		// Check if DH is present // 
		//========================//
		
		boolean dhPresent;
		try
		{
			// alternatively you could check if DH is loaded via your mod loader's API
			Class<?> dhApiClass = Class.forName("com.seibel.distanthorizons.api.DhApi");
			
			LOGGER.info("DH API found. ");
			dhPresent = true;
		}
		catch (ClassNotFoundException e)
		{
			LOGGER.info("DH API missing. DH may not be installed.");
			dhPresent = false;
		}
		

		
		//===========================//
		// use the DH API if present //
		//===========================//
		
		if (dhPresent)
		{
			LOGGER.info("Attempting to use the Distant Horizons API...");
			
			// The DhApi and its immediate methods are always available to use...
			Class<?> alwaysAvailable = DhApi.class;
			// ...however anything in DhApi.Delayed needs DH to fully load before it can be used.
			// in order to access anything in the Delayed subclass register a DhApiAfterDhInitEvent
			Class<?> requiresDhToFullyLoad = DhApi.Delayed.class;
			
			
			
			//=====================//
			// get the API version //
			//=====================//
			
			// DH Version
			String dhVersion = DhApi.getModVersion();
			LOGGER.info("DH version: " + dhVersion);
			
			// API version
			int dhApiMajorVersion = DhApi.getApiMajorVersion();
			int dhApiMinorVersion = DhApi.getApiMinorVersion();
			LOGGER.info("DH API version: " + dhApiMajorVersion + "." + dhApiMinorVersion);
			
			
			
			//==========================//
			// register event listeners //
			//==========================//
			
			LOGGER.info("Registering API events...");
			
			// accessing a delayed dH method at your mod's startup ///
			try
			{
				// if DH hasn't been initialized yet this will throw an exception
				DhApi.Delayed.configs.graphics().renderingEnabled().getValue();
				LOGGER.info("DH initialization has been completed.");
			}
			catch (Exception e)
			{
				LOGGER.info("Caught expected exception. DH initialization hasn't been completed.");
			}
			// Instead of depending on a specific mod load order, use the following event instead.
			// This event will fire either after DH finishes loading, 
			// or immediately if DH is already loaded.
			LOGGER.info("Finished Registering DH Events.");
			DhApiEventRegister.on(DhApiLevelLoadEvent.class, new SetReadOnlyEvent());
		}
	}
}