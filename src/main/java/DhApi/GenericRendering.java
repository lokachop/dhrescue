package DhApi;

import com.dhrescue.DHRescue;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;

import java.awt.*;
import java.util.ArrayList;

/**
 * All test objects will be rendered around (0, 150, 0) in the level.
 * 
 *  @version 2024-08-02 
 */
public class GenericRendering
{
	private static final int MAX_MC_LIGHT = 15;
	
	
	
	public static void registerForLevel(IDhApiLevelWrapper levelWrapper)
	{
		IDhApiCustomRenderObjectFactory factory = DhApi.Delayed.customRenderObjectFactory;
		IDhApiCustomRenderRegister renderRegister = levelWrapper.getRenderRegister();
		
		
		
		//==================//
		// single giant box //
		//==================//
		
		IDhApiRenderableBoxGroup singleGiantBoxGroup = factory.createForSingleBox(
				DHRescue.NAME + ":CyanChunkBox",
				new DhApiRenderableBox(
						new DhApiVec3d(0,0,0), new DhApiVec3d(16,190,16),
						new Color(Color.CYAN.getRed(), Color.CYAN.getGreen(), Color.CYAN.getBlue(), 125),
						EDhApiBlockMaterial.WATER)
		);
		singleGiantBoxGroup.setSkyLight(MAX_MC_LIGHT);
		singleGiantBoxGroup.setBlockLight(MAX_MC_LIGHT);
		renderRegister.add(singleGiantBoxGroup);
		
		
		
		//====================//
		// single slender box //
		//====================//
		
		IDhApiRenderableBoxGroup singleTallBoxGroup = factory.createForSingleBox(
				DHRescue.NAME + ":GreenBeacon",
				new DhApiRenderableBox(
						new DhApiVec3d(16,0,31), new DhApiVec3d(17,2000,32),
						new Color(Color.GREEN.getRed(), Color.GREEN.getGreen(), Color.GREEN.getBlue(), 125),
						EDhApiBlockMaterial.ILLUMINATED)
		);
		singleTallBoxGroup.setSkyLight(MAX_MC_LIGHT);
		singleTallBoxGroup.setBlockLight(MAX_MC_LIGHT);
		renderRegister.add(singleTallBoxGroup);
		
		
		
		//====================//
		// absolute box group //
		//====================//
		
		ArrayList<DhApiRenderableBox> absBoxList = new ArrayList<>();
		for (int i = 0; i < 18; i++)
		{
			absBoxList.add(new DhApiRenderableBox(
							new DhApiVec3d(i,150+i,24), new DhApiVec3d(1+i,151+i,25),
							new Color(Color.ORANGE.getRed(), Color.ORANGE.getGreen(), Color.ORANGE.getBlue()),
							EDhApiBlockMaterial.LAVA
					)
			);
		}
		IDhApiRenderableBoxGroup absolutePosBoxGroup = factory.createAbsolutePositionedGroup(DHRescue.NAME + ":OrangeStairs", absBoxList);
		renderRegister.add(absolutePosBoxGroup);
		
		
		
		//====================//
		// relative box group //
		//====================//
		
		ArrayList<DhApiRenderableBox> relBoxList = new ArrayList<>();
		for (int i = 0; i < 8; i+=2)
		{
			relBoxList.add(new DhApiRenderableBox(
							new DhApiVec3d(0,i,0), new DhApiVec3d(1,1+i,1),
							new Color(Color.MAGENTA.getRed(), Color.MAGENTA.getGreen(), Color.MAGENTA.getBlue()),
							EDhApiBlockMaterial.METAL
					)
			);
		}
		IDhApiRenderableBoxGroup relativePosBoxGroup = factory.createRelativePositionedGroup(
				DHRescue.NAME + ":MovingMagentaGroup",
				new DhApiVec3d(24, 140, 24),
				relBoxList);
		relativePosBoxGroup.setPreRenderFunc((event) ->
		{
			DhApiVec3d pos = relativePosBoxGroup.getOriginBlockPos();
			pos.x += event.partialTicks / 2;
			pos.x %= 32;
			relativePosBoxGroup.setOriginBlockPos(pos);
		});
		renderRegister.add(relativePosBoxGroup);
		
		
		
		//===========================//
		// many relative boxes group //
		//===========================//
		
		ArrayList<DhApiRenderableBox> massRelBoxList = new ArrayList<>();
		for (int x = 0; x < 50*2; x+=2)
		{
			for (int z = 0; z < 50*2; z+=2)
			{
				massRelBoxList.add(new DhApiRenderableBox(
								new DhApiVec3d(-x, 0, -z), new DhApiVec3d(1-x, 1, 1-z),
								new Color(Color.RED.getRed(), Color.RED.getGreen(), Color.RED.getBlue()),
								EDhApiBlockMaterial.TERRACOTTA
						)
				);
			}
		}
		IDhApiRenderableBoxGroup massRelativePosBoxGroup = factory.createRelativePositionedGroup(
				DHRescue.NAME + ":MassRedGroup",
				new DhApiVec3d(-25, 140, 0),
				massRelBoxList);
		massRelativePosBoxGroup.setPreRenderFunc((event) ->
		{
			DhApiVec3d blockPos = massRelativePosBoxGroup.getOriginBlockPos();
			blockPos.y += event.partialTicks / 4;
			if (blockPos.y > 150f)
			{
				blockPos.y = 140f;
				
				Color newColor = (massRelativePosBoxGroup.get(0).color == Color.RED) ? Color.RED.darker() : Color.RED;
				massRelativePosBoxGroup.forEach((box) -> { box.color = newColor; });
				massRelativePosBoxGroup.triggerBoxChange();
			}
			
			massRelativePosBoxGroup.setOriginBlockPos(blockPos);
		});
		renderRegister.add(massRelativePosBoxGroup);
	}
	
}
