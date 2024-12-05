package DhApi;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiRaycastResult;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3i;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;

import org.joml.Vector3f;

/** @version 2024-08-02 */
public class TerrainDataAccess
{
	public static TerrainDataAccess INSTANCE = new TerrainDataAccess();
	
	private BlockState previousBlockState = null;
	/** 
	 * WARNING: sharing across levels/dimensions will cause incorrect behavior. <br><br>
	 * 
	 * Using a cache will significantly speed up reads from the same LOD,
	 * however unless you periodically clear the cache LODs may become out of date and
	 * memory use will grow.
	 */
	private IDhApiTerrainDataCache terrainCache = null;
	/** The DH data repo is thread safe */
	private Thread dataRetreivalThread = null;
	
	
	
	public void printRaycastResultToChatAsync()
	{
		// when initially loading in LODs from the hard drive
		// it can be a bit slow and we don't want to lag the render thread
		// so we're running this on a separate thread
		if (this.dataRetreivalThread == null)
		{
			this.dataRetreivalThread = new Thread(() -> 
			{
				try
				{
					this.printRaycastResultToChat();
				}
				finally
				{
					this.dataRetreivalThread = null;
				}
			});
			this.dataRetreivalThread.start();
		}
	}
	
	public void printRaycastResultToChat()
	{
		//====================================//
		// wait for a Minecraft level to load //
		//====================================//
		
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null)
		{
			return;
		}
		
		ClientPlayerEntity player = mc.player;
		if (player == null)
		{
			return;
		}
		
		if (!DhApi.Delayed.worldProxy.worldLoaded())
		{
			return;
		}
		IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
		if (levelWrapper == null)
		{
			return;
		}
		
		
		
		//============//
		// query prep //
		//============//
		
		// if you plan to retrieve data over a long period of time you will want to periodically
		// clear this memory cache so the cache doesn't become stale 
		// (IE clear the cache once in a while otherwise the data you pull won't match what's real).
		if (this.terrainCache == null)
		{
			this.terrainCache = DhApi.Delayed.terrainRepo.getSoftCache();
		}
		
		
		
		//=========//
		// raycast //
		//=========//
		
		// attempt to get the DH datapoint that the player is looking at
		Camera camera = mc.gameRenderer.getCamera();
		Vector3f cameraDir = camera.getHorizontalPlane();
		DhApiResult<DhApiRaycastResult> rayCastResult = DhApi.Delayed.terrainRepo.raycast(
				levelWrapper,
				camera.getPos().getX(), camera.getPos().getY(), camera.getPos().getZ(),
				cameraDir.x, cameraDir.y, cameraDir.z,
				// how far in blocks to look before giving up
				2000, 
				// this argument is optional but will make your queries significantly faster after the initial LOD retrieval
				this.terrainCache);
		
		
		
		//============================//
		// process the raycast result //
		//============================//
		
		BlockState newBlockState = null;
		DhApiVec3i rayCastBlockPos = null;
		if (rayCastResult.success && rayCastResult.payload != null)
		{
			// the raycast successfully hit a block
			
			// Note: whenever you use a wrapper method on a new Minecraft version it is recommended that you
			// call object.getClass() to determine which object the API will return before you try casting it.
			newBlockState = (BlockState) rayCastResult.payload.dataPoint.blockStateWrapper.getWrappedMcObject();
			rayCastBlockPos = rayCastResult.payload.pos;
		}
		
		// only send a chat message when the block changes to reduce spam 
		if (this.previousBlockState != newBlockState)
		{
			this.previousBlockState = newBlockState;
			
			// get the block name
			String blockName = "NULL";
			if (newBlockState != null)
			{
				blockName = newBlockState.getBlock().getTranslationKey();
			}
			
			// get the distance
			double rayDistance = -1;
			if (rayCastBlockPos != null)
			{
				rayDistance =
					Math.sqrt(
						Math.pow(rayCastBlockPos.x - camera.getPos().getX(), 2) +
						Math.pow(rayCastBlockPos.y - camera.getPos().getY(), 2) +
						Math.pow(rayCastBlockPos.z - camera.getPos().getZ(), 2)
					);
				rayDistance = Math.round(rayDistance * 100);
				rayDistance = rayDistance / 100.0;
			}
			
			
			// print to chat
			String message = "block: ["+blockName+"] pos: ["+rayCastBlockPos+"] distance: ["+rayDistance+"]";
			player.sendMessage(Text.of(message), false);
			//LOGGER.info(message);
		}
	}
	
	
}
