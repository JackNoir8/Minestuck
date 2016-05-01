package com.mraof.minestuck.item;

import java.util.Iterator;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.mraof.minestuck.Minestuck;
import com.mraof.minestuck.MinestuckConfig;

import static com.mraof.minestuck.MinestuckConfig.artifactRange;

import com.mraof.minestuck.block.BlockGate;
import com.mraof.minestuck.block.MinestuckBlocks;
import com.mraof.minestuck.event.ServerEventHandler;
import com.mraof.minestuck.network.skaianet.SburbConnection;
import com.mraof.minestuck.network.skaianet.SkaianetHandler;
import com.mraof.minestuck.tileentity.TileEntityComputer;
import com.mraof.minestuck.tileentity.TileEntityGate;
import com.mraof.minestuck.tracker.MinestuckPlayerTracker;
import com.mraof.minestuck.util.ColorCollector;
import com.mraof.minestuck.util.Debug;
import com.mraof.minestuck.util.ITeleporter;
import com.mraof.minestuck.util.MinestuckAchievementHandler;
import com.mraof.minestuck.util.PostEntryTask;
import com.mraof.minestuck.util.Teleport;
import com.mraof.minestuck.util.UsernameHandler;
import com.mraof.minestuck.world.GateHandler;
import com.mraof.minestuck.world.MinestuckDimensionHandler;
import com.mraof.minestuck.world.lands.LandAspectRegistry;

public abstract class ItemCruxiteArtifact extends Item implements ITeleporter
{
	
	public ItemCruxiteArtifact() 
	{
		this.setCreativeTab(Minestuck.tabMinestuck);
		setUnlocalizedName("cruxiteArtifact");
		this.maxStackSize = 1;
		setHasSubtypes(true);
	}
	
	protected void onArtifactActivated(World world, EntityPlayer player)
	{
		if(!world.isRemote && player.worldObj.provider.getDimensionId() != -1)
		{
			SburbConnection c = SkaianetHandler.getMainConnection(UsernameHandler.encode(player), true);
			
			if(c == null || !c.enteredGame() || !MinestuckDimensionHandler.isLandDimension(player.worldObj.provider.getDimensionId()))
			{
				int destinationId;
				if(c != null && c.enteredGame())
					destinationId = c.getClientDimension();
				else destinationId = LandAspectRegistry.createLand(player);
				
				player.triggerAchievement(MinestuckAchievementHandler.enterMedium);
				Teleport.teleportEntity(player, destinationId, this, false);
				MinestuckPlayerTracker.sendLandEntryMessage(player);
			}
		}
	}
	
	public void makeDestination(Entity entity, WorldServer worldserver0, WorldServer worldserver1)
	{
		if(entity instanceof EntityPlayerMP)
		{
			int x = (int) entity.posX;
			if(entity.posX < 0) x--;
			int y = (int) entity.posY;
			int z = (int) entity.posZ;
			if(entity.posZ < 0) z--;
			
			int topY = MinestuckConfig.adaptEntryBlockHeight ? getTopHeight(worldserver0, x, y, z) : y + artifactRange;
			int yDiff = 128 - topY;
			MinestuckDimensionHandler.setSpawn(worldserver1.provider.getDimensionId(), new BlockPos(x, y + yDiff, z));	//Set again, but with a more precise now that the y-coordinate is properly decided.
			
			Debug.print("Loading spawn chunks...");
			for(int chunkX = ((x - artifactRange) >> 4) - 1; chunkX <= ((x + artifactRange) >> 4) + 2; chunkX++)	//Prevent anything to generate on the piece that we move
				for(int chunkZ = ((z - artifactRange) >> 4) - 1; chunkZ <= ((z + artifactRange) >> 4) + 2; chunkZ++)	//from the overworld.
					worldserver1.theChunkProviderServer.loadChunk(chunkX, chunkZ);
			
			Debug.print("Placing blocks...");
			long time = System.currentTimeMillis();
			int bl = 0;
			int nextZWidth = 0;
			for(int blockX = x - artifactRange; blockX <= x + artifactRange; blockX++)
			{
				int zWidth = nextZWidth;
				nextZWidth = (int) Math.sqrt(artifactRange * artifactRange - (blockX - x + 1) * (blockX - x + 1));
				for(int blockZ = z - zWidth; blockZ <= z + zWidth; blockZ++)
				{
					Chunk chunk = worldserver1.getChunkFromChunkCoords(blockX >> 4, blockZ >> 4);
					Chunk chunk2 = worldserver0.getChunkFromChunkCoords(blockX >> 4, blockZ >> 4);
					int height = (int) Math.sqrt(artifactRange * artifactRange - (((blockX - x) * (blockX - x) + (blockZ - z) * (blockZ - z)) / 2));
					for(int blockY = Math.max(0, y - height); blockY <= Math.min(topY, y + height); blockY++)
					{
						BlockPos pos = new BlockPos(blockX, blockY, blockZ);
						BlockPos pos1 = pos.up(yDiff);
						IBlockState block = worldserver0.getBlockState(pos);
						TileEntity te = worldserver0.getTileEntity(pos);
						long t = System.currentTimeMillis();
						if(block.getBlock() != Blocks.bedrock && block.getBlock() != Blocks.portal)
						{
							copyBlockDirect(chunk, chunk2, blockX & 15, blockY + yDiff, blockY, blockZ & 15);
						}
						bl += System.currentTimeMillis() - t;
						if((te) != null)
						{
							TileEntity te1 = null;
							try {
								te1 = te.getClass().newInstance();
							} catch (Exception e) {e.printStackTrace();	continue;}
							NBTTagCompound nbt = new NBTTagCompound();
							te.writeToNBT(nbt);
							nbt.setInteger("y", pos1.getY());
							te1.readFromNBT(nbt);
							worldserver1.removeTileEntity(pos1);
							worldserver1.setTileEntity(pos1, te1);
							if(te instanceof TileEntityComputer)
								SkaianetHandler.movingComputer((TileEntityComputer) te, (TileEntityComputer) te1);
						}
					}
					for(int blockY = Math.min(topY, y + height) + yDiff; blockY < 256; blockY++)
						worldserver1.setBlockState(new BlockPos(blockX, blockY, blockZ), Blocks.air.getDefaultState(), 0);
				}
			}
			
			int total = (int) (System.currentTimeMillis() - time);
			Debug.printf("Total: %d, block: %d", total, bl);
			
			Debug.print("Teleporting entities...");
			List<?> list = entity.worldObj.getEntitiesWithinAABBExcludingEntity(entity, entity.getEntityBoundingBox().expand((double)artifactRange, artifactRange, (double)artifactRange));
			Iterator<?> iterator = list.iterator();
			
			entity.setPositionAndUpdate(entity.posX, entity.posY + yDiff, entity.posZ);
			while (iterator.hasNext())
			{
				Entity e = (Entity)iterator.next();
				if(MinestuckConfig.entryCrater || e instanceof EntityPlayer || e instanceof EntityItem)
				{
					e.setPosition(e.posX, e.posY + yDiff, e.posZ);
					Teleport.teleportEntity(e, worldserver1.provider.getDimensionId(), null, false);
				}
				else	//Copy instead of teleport
				{
					Entity newEntity = EntityList.createEntityByName(EntityList.getEntityString(entity), worldserver1);
					if (newEntity != null)
					{
						newEntity.copyDataFromOld(entity);
						newEntity.dimension = worldserver1.provider.getDimensionId();
						newEntity.setPosition(newEntity.posX, newEntity.posY + yDiff, newEntity.posZ);
						worldserver1.spawnEntityInWorld(newEntity);
					}
				}
			}
			
			Debug.print("Removing old blocks...");
			for(int blockX = x - artifactRange; blockX <= x + artifactRange; blockX++)
			{
				int zWidth = (int) Math.sqrt(artifactRange * artifactRange - (blockX - x) * (blockX - x));
				for(int blockZ = z - zWidth; blockZ <= z + zWidth; blockZ++)
				{
					double radius = Math.sqrt(((blockX - x) * (blockX - x) + (blockZ - z) * (blockZ - z)) / 2);
					int height = (int) (Math.sqrt(artifactRange * artifactRange - radius*radius));
					int minY =  y - height;
					minY = minY < 0 ? 0 : minY;
					int maxY = MinestuckConfig.entryCrater ? Math.min(topY, y + height) + 1 : 256;
					for(int blockY = minY; blockY < maxY; blockY++)
					{
						BlockPos pos = new BlockPos(blockX, blockY, blockZ);
						if(MinestuckConfig.entryCrater)
						{
							if(worldserver0.getBlockState(pos).getBlock() != Blocks.bedrock)
								worldserver0.setBlockState(pos, Blocks.air.getDefaultState(), 2);
						} else
							if(worldserver0.getTileEntity(pos) != null)
								worldserver0.setBlockState(pos, Blocks.air.getDefaultState(), 2);
					}
				}
			}
			SkaianetHandler.clearMovingList();
			
			Debug.print("Removing entities created from removing blocks...");	//Normally only items in containers
			list = entity.worldObj.getEntitiesWithinAABBExcludingEntity(entity, entity.getEntityBoundingBox().expand((double)artifactRange, artifactRange, (double)artifactRange));
			iterator = list.iterator();
			while (iterator.hasNext())
				if(MinestuckConfig.entryCrater)
					((Entity)iterator.next()).setDead();
				else
				{
					Entity e = (Entity) iterator.next();
					if(e instanceof EntityItem)
						e.setDead();
				}
			
			Debug.print("Placing gates...");
			
			GateHandler.findGatePlacement(worldserver1);
			placeGate(1, new BlockPos(x, GateHandler.gateHeight1, z), worldserver1);
			placeGate(2, new BlockPos(x, GateHandler.gateHeight2, z), worldserver1);
			
			ServerEventHandler.tickTasks.add(new PostEntryTask(worldserver1.provider.getDimensionId(), x, y + yDiff, z, artifactRange, (byte) 0));
			
			Debug.print("Entry finished");
		}
	}
	
	private static void copyBlockDirect(Chunk c, Chunk c2, int x, int y, int y2, int z)
	{
		int j = y & 15, j2 = y2 & 15;
		ExtendedBlockStorage blockStorage = getBlockStorage(c, y >> 4);
		ExtendedBlockStorage blockStorage2 = getBlockStorage(c2, y2 >> 4);
		
		blockStorage.set(x, j, z, blockStorage2.get(x, j2, z));
		blockStorage.getBlocklightArray().set(x, j, z, blockStorage2.getBlocklightArray().get(x, j2, z));
		blockStorage.getSkylightArray().set(x, j, z, blockStorage2.getSkylightArray().get(x, j2, z));
	}
	
	private static ExtendedBlockStorage getBlockStorage(Chunk c, int y)
	{
		ExtendedBlockStorage blockStorage = c.getBlockStorageArray()[y];
		if(blockStorage == null)
			blockStorage = c.getBlockStorageArray()[y] = new ExtendedBlockStorage(y << 4, !c.getWorld().provider.getHasNoSky());
		return blockStorage;
	}
	
	private static int getTopHeight(WorldServer world, int x, int y, int z)
	{
		Debug.print("Getting maxY..");
		int maxY = y;
		for(int blockX = x - artifactRange; blockX <= x + artifactRange; blockX++)
		{
			int zWidth = (int) Math.sqrt(artifactRange * artifactRange - (blockX - x) * (blockX - x));
			for(int blockZ = z - zWidth; blockZ <= z + zWidth; blockZ++)
			{
				int height = (int) (Math.sqrt(artifactRange * artifactRange - (((blockX - x) * (blockX - x) + (blockZ - z) * (blockZ - z)) / 2)));
				for(int blockY = Math.min(255, y + height); blockY > maxY; blockY--)
					if(!world.isAirBlock(new BlockPos(blockX, blockY, blockZ)))
					{
						maxY = blockY;
						break;
					}
			}
		}
		
		Debug.print("maxY: "+ maxY);
		return maxY;
	}
	
	private static void placeGate(int gateCount, BlockPos pos, WorldServer world)
	{
		for(int i = 0; i < 9; i++)
			if(i == 4)
			{
				world.setBlockState(pos, MinestuckBlocks.gate.getDefaultState().cycleProperty(BlockGate.isMainComponent), 0);
				TileEntityGate tileEntity = (TileEntityGate) world.getTileEntity(pos);
				tileEntity.gateCount = gateCount;
			}
			else world.setBlockState(pos.add((i % 3) - 1, 0, i/3 - 1), MinestuckBlocks.gate.getDefaultState(), 0);
	}
	
	@Override
	public int getColorFromItemStack(ItemStack stack, int renderPass)
	{
		if(stack.getMetadata() == 0)
			return -1;
		else return ColorCollector.getColor(stack.getMetadata() - 1);
	}
}
