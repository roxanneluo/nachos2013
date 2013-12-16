package nachos.filesys;

import nachos.machine.Disk;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;

/**
 * File provide some basic IO operations.
 * Each File is associated with an INode which stores the basic information for the file.
 * 
 * @author starforever
 */
public class File extends OpenFile
{
	//inode == null <=> closed
	INode inode;
  
	private int pos = 0;
  
	public File (INode inode)
	{
		this.inode = inode;
		pos = 0;
	}
  
	public int length ()
	{
		return inode.fileSize;
	}
  
	public void close ()
	{
		// don't need to deal with flush
	  if (inode == null) return;
	  inode.decreaseUseCnt();
	  Lib.debug(RealFileSystem.dbgFilesys,
			  "in close inode at "+inode.getAddr()+", link:"+inode.linkCount+",useCount:"+inode.useCount);
	  inode.tryFree();
	  inode = null;
  }
  
	public void seek (int pos)
	{
		//FIXME: so far I don't think any lock is needed here
		if (pos >= inode.fileSize) return;
		this.pos = pos;
	}
  
	public int tell ()
	{
		return pos;
	}
  
	public int read (byte[] buffer, int start, int limit)
	{
		int ret = read(pos, buffer, start, limit);
		return ret;
	}
  
	public int write (byte[] buffer, int start, int limit)
	{
		int ret = write(pos, buffer, start, limit);
		return ret;
	}

  	
  	public int read (int position, byte[] buffer, int start, int limit)
  	{
  		if (inode == null || start+limit > buffer.length) return -1;
  		inode.readWriteLock.acquireRead();
  		if (position >= inode.fileSize) {
  			inode.readWriteLock.release();
  			return -1;
  		}
  		seek(position);
  		limit = Math.min(limit, inode.fileSize-position);
  		copy(buffer, start, limit, false);
  		inode.readWriteLock.release();
  		//Lib.debug(RealFileSystem.dbgFilesys, "read:pos:"+pos+",fileSize:"+inode.fileSize);
  		return limit;
  	}
  
  	public int write (int position, byte[] buffer, int start, int limit)
  	{
  		if (inode == null || start+limit > buffer.length) return -1;
  		inode.readWriteLock.acquireWrite();
  		setFileSize(Math.max(inode.fileSize, position+limit));
  		if (position >= inode.fileSize) {
  			inode.readWriteLock.release();
  			return -1;
  		}
  		seek(position);
  		limit = Math.min(limit, inode.fileSize-position);
  		copy(buffer, start, limit, true);
  		inode.readWriteLock.release();
  		Lib.debug(RealFileSystem.dbgFilesys, "write:pos:"+this.pos+",fileSize:"+inode.fileSize+",limit:"+limit);
  		return limit;
  	}
  	
  	private void setFileSize(int size) {
  		inode.setFileSize(size);
  	}
  
  	private void copy(byte[] buffer, int offset, int length, boolean write) {
  		Lib.assertTrue(inode.readWriteLock.isHeldByCurrentThread(), "read write lock is not held when copy");
  		
  		while (length > 0) {
  			if (pos % Disk.SectorSize == 0 && length >= Disk.SectorSize) {
  				if (write)
  					Machine.synchDisk().writeSector(inode.getSector(pos), buffer, offset);
  				else Machine.synchDisk().readSector(inode.getSector(pos), buffer, offset);
  				length -= Disk.SectorSize;
  				pos += Disk.SectorSize;
  				offset += Disk.SectorSize;
  			} else {
  				byte[] data = new byte[Disk.SectorSize];
  				Machine.synchDisk().readSector(inode.getSector(pos), data, 0);
  				int secOffset = pos % Disk.SectorSize;
  				int cnt = Math.min(length, Disk.SectorSize-secOffset);
  				if (!write) {
  					System.arraycopy(data, secOffset, buffer, offset, cnt);
  				} else {
  					System.arraycopy(buffer, offset, data, secOffset, cnt);
  					Machine.synchDisk().writeSector(inode.getSector(pos),data, 0);
  				}
  				length -= cnt;
  				pos += cnt;
  				offset += cnt;
  			}
  		}
  	}
}
