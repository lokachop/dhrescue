package DhApi;

import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.EDhApiDetailLevel;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.AbstractDhApiChunkWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.Chunk;
import org.slf4j.Logger;

import java.util.ArrayList;

/** 
 * Custom world generators are helpful if you have the ability to
 * generate significantly simpler faster terrain for DH. <br>
 * Alternatively you could use {@link DhApi#isDhThread()} in your world gen code
 * and change the logic there.
 * 
 * @version 2024-08-02 
 */
public class CustomWorldGenerator extends AbstractDhApiChunkWorldGenerator
{
	private static final Logger LOGGER = LogUtils.getLogger();
	
	private final IDhApiLevelWrapper levelWrapper;
	private final WorldView level;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** This could also be handled externally from the {@link CustomWorldGenerator} object */
	public static void registerForLevel(IDhApiLevelWrapper levelWrapper)
	{
		// override the core DH world generator for this level
		IDhApiWorldGenerator exampleWorldGen = new CustomWorldGenerator(levelWrapper);
		DhApi.worldGenOverrides.registerWorldGeneratorOverride(levelWrapper, exampleWorldGen);
		
	}
	
	private CustomWorldGenerator(IDhApiLevelWrapper levelWrapper) 
	{
		this.levelWrapper = levelWrapper;
		
		// Note: whenever you use a wrapper method on a new Minecraft version it is recommended that you
		// call wrapper.getClass() to determine which object the API will return before you try casting it.
		this.level = (WorldView) levelWrapper.getWrappedMcObject();
	}
	
	
	
	//==================//
	// override methods //
	//==================//
	
	@Override
	public EDhApiWorldGeneratorReturnType getReturnType() 
	{
		// if set to VANILLA_CHUNKS (or this method isn't overridden) then generateChunk() will be called.
		// if set to API_CHUNKS, then the generateApiChunk() method will be called.
		// if you only want to use one method or the other the other can return null or throw an UnsupportedOperationException.
		return EDhApiWorldGeneratorReturnType.API_CHUNKS; 
	}

	public boolean isBusy() { return false; }
	
	@Override 
	public Object[] generateChunk(int chunkX, int chunkZ, EDhApiDistantGeneratorMode eDhApiDistantGeneratorMode)
	{
		Chunk chunk = this.level.getChunk(chunkX, chunkZ);
		return new Object[] { chunk, this.level };
	}
	
	@Override 
	public DhApiChunk generateApiChunk(int chunkPosX, int chunkPosZ, EDhApiDistantGeneratorMode generatorMode)
	{
		Chunk chunk = this.level.getChunk(chunkPosX, chunkPosZ);
		
		int minBuildHeight = chunk.getBottomY();
		int maxBuildHeight = chunk.getHeight();
		
		DhApiChunk apiChunk = DhApiChunk.create(chunkPosX, chunkPosZ, minBuildHeight, maxBuildHeight);
		for (int x = 0; x < 16; x++)
		{
			for (int z = 0; z < 16; z++)
			{
				ArrayList<DhApiTerrainDataPoint> dataPoints = new ArrayList<>();
				
				IDhApiBlockStateWrapper block = null;
				IDhApiBiomeWrapper biome = null;
				
				for (int y = minBuildHeight; y < maxBuildHeight; y++)
				{
					// Note: air/empty spaces must be defined, otherwise DH will fail to downsample correctly
					// and LODs will have the incorrect lighting.
					block = DhApi.Delayed.wrapperFactory.getBlockStateWrapper(new Object[]{ chunk.getBlockState(new BlockPos(x, y, z)) }, this.levelWrapper);
					biome = DhApi.Delayed.wrapperFactory.getBiomeWrapper(new Object[]{ chunk.getBiomeForNoiseGen(x, y, z) }, this.levelWrapper);
					// Note: merging identical datapoints together will improve processing speed and reduce filesize
					dataPoints.add(DhApiTerrainDataPoint.create(EDhApiDetailLevel.BLOCK.detailLevel, 0, 15, y, y+1, block, biome));
				}
				
				//Collections.reverse(dataPoints);
				// the api chunk can accept datapoints in either top-down or bottom-up order
				apiChunk.setDataPoints(x, z, dataPoints);
			}
		}
		return apiChunk;
	}
	
	@Override 
	public void preGeneratorTaskStart()
	{
		// do nothing
	}
	
	@Override 
	public void close()
	{
		// do nothing
	}
	
}
