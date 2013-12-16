package nachos.filesys;

import java.util.LinkedList;
import nachos.machine.Disk;
import nachos.machine.Lib;

/**
 * FreeList is a single special file used to manage free space of the filesystem.
 * It maintains a list of sector numbers to indicate those that are available to use.
 * When there's a need to allocate a new sector in the filesystem, call allocate().
 * And you should call deallocate() to free space at a appropriate time (eg. when a file is deleted) for reuse in the future.
 * 
 * @author starforever
 */
public class FreeList extends File
{
	/** the static address */
	public static int STATIC_ADDR = 0;
  
  	/** size occupied in the disk (bitmap) */
  	static int size = Lib.divRoundUp(Disk.NumSectors, 8);
  
  	/** maintain address of all the free sectors */
  	private LinkedList<Integer> freeList;
  	private BitMap bitmap;
  
  	public FreeList (INode inode)
  	{
  		super(inode);
  		freeList = new LinkedList<Integer>();
  	}
  
  	public void init ()
  	{
  		for (int i = 2; i < Disk.NumSectors; ++i)
  			freeList.add(i);
  	}
  
  	/** allocate a new sector in the disk */
  	public Integer allocate ()
  	{
  		if (freeList.isEmpty()) return null;
  		return freeList.removeFirst();
  	}
  
  	/** deallocate a sector to be reused */
  	public void deallocate (int sec)
  	{
  		freeList.add(sec);
  	}
  
  	/** save the content of freelist to the disk */
  	public void save ()
  	{
  		bitmap  = new BitMap(Disk.NumSectors);
  		for (Integer i:freeList) {
  			bitmap.set(i,false);
  		}
  		bitmap.writeBack(this);
  		inode.save();
  	}
  
  	/** load the content of freelist from the disk */
  	public void load ()
  	{
  		bitmap = new BitMap(Disk.NumSectors);
  		bitmap.readFrom(this);
  		for (int i = 0; i < Disk.NumSectors; ++i) {
  			if (bitmap.isFree(i))
  				freeList.add(i);
  		}
  	}
  	
  	public int freeSize() {
  		return freeList.size();
  	}
}
