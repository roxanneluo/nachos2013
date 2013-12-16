package nachos.filesys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import nachos.machine.Disk;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.Lock;
import nachos.threads.ReadWriteLock;

/**
 * INode contains detail information about a file.
 * Most important among these is the list of sector numbers the file occupied, 
 * it's necessary to find all the pieces of the file in the filesystem.
 * 
 * @author starforever
 */
public class INode
{
  /** represent a system file (free list) */
  public static final int TYPE_SYSTEM = 0;
  
  /** represent a folder */
  public static final int TYPE_FOLDER = 1;
  
  /** represent a normal file */
  public static final int TYPE_FILE = 2;
  
  /** represent a normal file that is marked as delete */
  public static final int TYPE_FILE_DEL = 3;
  
  /** represent a symbolic link file */
  public static final int TYPE_SYMLINK = 4;
  
  /** represent a folder that are not valid */
  public static final int TYPE_FOLDER_DEL = 5;
  
  
  /** the reserve size (in byte) in the first sector */
  private static final int FIRST_SEC_RESERVE = 16;
  
  /** size of the file in bytes */
  int fileSize;
  
  /** the type of the file */
  int fileType;
  
  /** the number of programs that have access on the file */
  int useCount;
  
  /** the number of links on the file */
  int linkCount;
  
  /** maintain all the sector numbers this file used in order */
  private ArrayList<Integer> secAddr;
  
  /** the first address */
  private int addr;
  
  /** the extended address */
  private LinkedList<Integer> addrExt;
  ReadWriteLock readWriteLock = new ReadWriteLock();
  private Lock lock = new Lock();
  
  private static HashMap<Integer, INode> inodeTable = new HashMap<Integer, INode>();
  private static Lock tableLock = new Lock();
  
  public static final int WordSize = 4;
  
  	// all open file use its inode through its pointer, DON'T use this func
  	// return existing inode or create one if not exist
  	// return null if the inode is marked del or type is wrong;
  	// load is false iff the inode is newly created. even if one does not need to know the
  	// content of the node, load should be true also.
  	// if type == null, return an existing node of any type
  	public static INode getINode(int addr, Integer type, boolean load) {
	  	tableLock.acquire(); 
	  	INode node = inodeTable.get(addr);
	  	
	  	if (node != null) {
		  	tableLock.release();
		  	if (type != null 
		  			&& (node.fileType != type || node.fileType == TYPE_FILE_DEL 
		  			|| node.fileType == TYPE_FOLDER_DEL))
			  	return null;
		  	else
			  	return node;
	  	} else if (type == null)
	  		return null;
	  	tableLock.release();
	  	node = new INode(addr, type);
	  	if (load)
		  	node.load();
	  	if (node.fileType != type) return null;	// TODO: symlink not deal with
	  
	  	tableLock.acquire();
	  	inodeTable.put(addr, node);
	  	tableLock.release();
	  	return node;
  	}
  
  	public static boolean exist(int addr) {
  		tableLock.acquire();
	 	boolean ans = inodeTable.containsKey(addr);
	 	tableLock.release();
	 	return ans;
  	}
  	private INode (int addr, int type)
  	{
  		fileSize = 0;
    	fileType = type;
    	useCount = 0;
    	linkCount = 0;
    	secAddr = new ArrayList<Integer>();
    	this.addr = addr;
    	addrExt = new LinkedList<Integer>();
  	}
  
  	/** get the sector number of a position in the file  */
  	public int getSector (int pos)
  	{	
  		//FIXME: do I need a lock here
  		Lib.assertTrue(fileType != TYPE_FOLDER_DEL && fileType != TYPE_FILE_DEL);
	  	Lib.assertTrue(pos < secAddr.size()*Disk.SectorSize);
	  	return secAddr.get(pos/Disk.SectorSize);
  	}
  
  	private static int getFileSecNum(int size) {
  		return size/Disk.SectorSize+(size%Disk.SectorSize == 0? 0:1);
  	}
  	
 
  	/** change the file size and adjust the content in the inode accordingly */
  	public void setFileSize (int size)
  	{
  		Lib.assertTrue(fileType != TYPE_FOLDER_DEL && fileType != TYPE_FILE_DEL);
  		Lib.assertTrue(size >= fileSize, "set to smaller file size");
  		// so far I don't think I need to lock here;
  		Lib.assertTrue(readWriteLock.isHeldByCurrentThread(), "readwritelock is not hel in setfilesize");
		int oldFileSecNum = getFileSecNum(fileSize);
		int newFileSecNum = getFileSecNum(size);
		if (oldFileSecNum == newFileSecNum) {
			fileSize = size;
			return;
		}
		int addNum = addSec(newFileSecNum-oldFileSecNum);
		if (addNum == newFileSecNum-oldFileSecNum)
			fileSize = size;
		else {
			fileSize = (oldFileSecNum+addNum)*Disk.SectorSize;
		}
		
  	}
  
  	/** free the disk space occupied by the file (including inode) */
  	public void free ()
	{
  		Lib.debug(RealFileSystem.dbgFilesys, "free inode at "+addr);
  		// FIXME: after successfully debug, this assert can be removed
  		fileSize = 0;
  		for (Integer addr :secAddr) {
  			FilesysKernel.realFileSystem.getFreeList().deallocate(addr);
  		}
  		secAddr.clear();
  		for (Integer addr :addrExt) {
  			FilesysKernel.realFileSystem.getFreeList().deallocate(addr);
  		}
  		addrExt.clear();
  		FilesysKernel.realFileSystem.getFreeList().deallocate(addr);

  		tableLock.acquire();
  		inodeTable.remove(addr);
  		tableLock.release();
  		addr = -1;	// FIXME: I don't think it's needed
	}
  
  	/** load inode content from the disk */
  	public void load ()
  	{
  		//FIXME the following assert can be removed
  		Lib.assertTrue(fileType != TYPE_FOLDER_DEL && fileType != TYPE_FILE_DEL);
  		
  		lock.acquire();
  		byte[] buffer = new byte[Disk.SectorSize];
  		int pos = 0;
  		fileSize = Disk.intInt(buffer, pos);
  		pos+=WordSize;
  		fileType = Disk.intInt(buffer, pos);
  		pos += WordSize;
  		linkCount = Disk.intInt(buffer, pos);
  		pos += WordSize;
  		int secAddrSize = Disk.intInt(buffer, pos);
  		int addrExtSize = Disk.intInt(buffer, pos);
	  
  		int curSec = addr;
  		addrExt.clear();
  		secAddr.clear();
  		Iterator<Integer> secIter = addrExt.iterator();
  		for (int i = 0; i < addrExtSize; ++i) {
  			if (pos == Disk.SectorSize) {
  				pos = 0;
  				Machine.synchDisk().readSector(curSec, buffer, 0);
  				curSec = secIter.next();
  			}
  			addrExt.add(Disk.intInt(buffer, pos));
  			pos+=WordSize;
  		}
  		for (int i = 0; i < secAddrSize; ++i) {
  			if (pos == Disk.SectorSize) {
  				pos = 0;
  				Machine.synchDisk().readSector(curSec, buffer, 0);
  				curSec = secIter.next();
  			}
  			secAddr.add(Disk.intInt(buffer, pos));
  			pos+=WordSize;
  		}
  		lock.release();
	}
  
  /** save inode content to the disk */
  	public void save ()
  	{
  		//FIXME the following assert can be removed  		
  		lock.acquire();
  		int len = (5+addrExt.size()+secAddr.size())*WordSize;
  		while (len > (1+addrExt.size())*Disk.SectorSize) {
  			addrExt.add(FilesysKernel.realFileSystem.getFreeList().allocate());
  		}
  		int pos = 0;
  		byte [] buffer = new byte[Disk.SectorSize];
  		Disk.extInt(fileSize, buffer, pos);
  		pos+=WordSize;
  		Disk.extInt(fileType, buffer, pos);
  		pos += WordSize;
  		Disk.extInt(linkCount,buffer, pos);
  		pos+=WordSize;
  		Disk.extInt(secAddr.size(),buffer, pos);
  		pos+=WordSize;
	  	Disk.extInt(addrExt.size(),buffer, pos);
	  	pos+=WordSize;
	  
	  	Integer curSec = addr;
	  	Iterator<Integer> iter = addrExt.iterator();
	  	Iterator<Integer> secIter = addrExt.iterator();
	  	while(iter.hasNext()) {
	  		if (pos == Disk.SectorSize) {
	  			pos = 0;
	  			Machine.synchDisk().writeSector(curSec, buffer, 0);
	  			curSec = secIter.next();
	  		}
	  		Disk.extInt(iter.next(), buffer, pos);
	  		pos+=WordSize;
	  	}
	  
	  	iter = secAddr.iterator();
	  	while(iter.hasNext()) {
	  		if (pos == Disk.SectorSize) {
	  			pos = 0;
	  			Machine.synchDisk().writeSector(curSec, buffer, 0);
	  			curSec = secIter.next();
	  		}
	  		Disk.extInt(iter.next(), buffer, pos);
	  		pos+=WordSize;
	  	}
	  	if (pos != 0)
	  		Machine.synchDisk().writeSector(curSec, buffer, 0);
	  	lock.release();
  	}
  	public void decreaseLinkCount() {
  		//FIXME the following assert can be removed
  		Lib.assertTrue(fileType != TYPE_FOLDER_DEL && fileType != TYPE_FILE_DEL);
  		
  		Lib.assertTrue(linkCount >= 1, "inode link count < 0");
  		lock.acquire();
  		if (--linkCount == 0) 
  			fileType = TYPE_FILE_DEL;
  		lock.release();
  	}
  	public int getAddr() {
  	//FIXME the following assert can be removed  		
  		return addr;
  	}
  	public void tryFree() {
  	//FIXME the following assert can be removed
  		//Lib.debug(RealFileSystem.dbgFilesys, "try free inode@"+addr);
  		lock.acquire();
  		if (useCount == 0 && linkCount == 0) 
  			free();
  		lock.release();
  	}
  	public void increaseLinkCount() {
  	//FIXME the following assert can be removed
  		Lib.assertTrue(fileType != TYPE_FOLDER_DEL && fileType != TYPE_FILE_DEL);
  		
  		lock.acquire();
  		++linkCount;
  		lock.release();
  	}
  
  	public void increaseUseCnt() {
  		//FIXME the following assert can be removed
		Lib.assertTrue(fileType != TYPE_FOLDER_DEL && fileType != TYPE_FILE_DEL);
		
		lock.acquire();
		++useCount;
		lock.release();
  	}
  	public void decreaseUseCnt() {
		//FIXME the following assert can be removed
		lock.acquire();
		--useCount;
		lock.release();
	}
  	
  	public int getFileSize() {
  		return fileSize;
  	}
  	
  	public int getSectorNum() {
  		return secAddr.size();
  	}
  	
  	int addSec(int addNum) {
  		Lib.assertTrue(readWriteLock.isHeldByCurrentThread(), 
  				"read write lock is not held by current thread");
  		//FIXME: I think the following lock is not needed
  		lock.acquire();
  		int addCnt = 0;
  		for (int i = 0; i < addNum; ++i) {
  			Integer addr = FilesysKernel.realFileSystem.getFreeList().allocate();
  			if (addr == null) {
  				lock.release();
  				return addCnt;
  			}
  			secAddr.add(addr);
  			++addCnt;
  		}
  		lock.release();
  		return addCnt;
  	}
  	
  	
  	static void saveAll() {
  		for (INode inode: INode.inodeTable.values()) {
  			inode.save();
  		}
  	}
  	
  	
}
