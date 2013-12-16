package nachos.filesys;

import java.util.ArrayList;
import java.util.Hashtable;

import nachos.machine.Lib;
import nachos.threads.Lock;

/**
 * Folder is a special type of file used to implement hierarchical filesystem.
 * It maintains a map from filename to the address of the file.
 * There's a special folder called root folder with pre-defined address.
 * It's the origin from where you traverse the entire filesystem.
 * 
 * @author starforever
 */
public class Folder extends File
{
	/** the static address for root folder */
	public static int STATIC_ADDR = 1;
  
	//private int size;
  
	/** mapping from filename to folder entry */
	private Hashtable<String, FolderEntry> fileTable = new Hashtable<String, FolderEntry>();
	Lock lock = new Lock();	//when add entry the lock can be held outside. o.w. only this can lock.
	private static Hashtable<String, Folder> folderTable = new Hashtable<String, Folder>();
	private static Lock tableLock = new Lock();
	
	
	public static Folder getExistingFolder(String absName) {
		//Lib.debug(RealFileSystem.dbgFilesys, "get exsting folder: "+absName);
		tableLock.acquire();
		Folder folder = folderTable.get(absName);
		tableLock.release();
		return folder;
	}
	public static Folder getFolder(String name, INode inode, boolean load) {
		tableLock.acquire();
		Folder ans = folderTable.get(name);
		if (ans != null) {
			tableLock.release();
			Lib.debug(RealFileSystem.dbgFilesys, "exist:"+name+"\n"+folderTable.toString());
			return ans;
		}
		
		if (INode.exist(inode.getAddr())) {
			ArrayList<Folder> folders =  new ArrayList<Folder>(folderTable.values());
			int addr = inode.getAddr();
			for (Folder f:folders) {
				if (f.inode.getAddr() == addr) {
					tableLock.release();
					Lib.debug(RealFileSystem.dbgFilesys, "exist addr:"+name+"\n"+folderTable.toString());
					return f;
				}
			}
		}
		
		ans = new Folder(inode);
		folderTable.put(name, ans);
		if (load)
			ans.load();
		Lib.debug(RealFileSystem.dbgFilesys, "create new folder"+name+":\n"+folderTable.toString());
		tableLock.release();
		return ans;
	}
	private Folder (INode inode)
	{
	    super(inode);
	    fileTable = new Hashtable<String, FolderEntry>();
	}
	  
	  /** open a file in the folder and return its address */
	  /*public int open (String filename)
	  {
	    //TODO implement this
	    return 0;
	  }*/
	  
	  /** create a new file in the folder and return its address */
	  /*public int create (String filename)
	  {
	    //TODO implement this
	    return 0;
	  }*/
	  
	  /** add an entry with specific filename and address to the folder */
	  /**
	   * add link to the inode automatically
	   *
	   * @param filename
	   * @param addr
	   * @param type
	   * @return the newly added inode or null if the inode type is wrong, or the inode is marked del(only when symlink/link, 
	   * this will not happen when create a new inode)
	   */
	  public INode addEntry (String filename, int addr, int type, boolean load)
	  {
		  Lib.assertTrue(lock.isHeldByCurrentThread());
	    //TODO implement this
		  INode inode = INode.getINode(addr, type, load);
		  if (inode != null) {
			  inode.increaseLinkCount();
			  fileTable.put(filename, new FolderEntry(filename,addr));
		  }
		  return inode;
	  }
	  
	  public boolean isEmpty() {
		  return fileTable.isEmpty();
	  }
	  
	/** remove an entry from the folder */
	/**
	 * remove link to the inode automatically, and try free the inode.
	 * only can be called by remove or remove folder
	 * @param filename
	 * @param type
	 * @return true iff the table contains the file with the correct type
	 */
    public boolean removeEntry (String filename, Integer type)
    {
    	lock.acquire();
    	FolderEntry entry = fileTable.get(filename);
    	if (entry != null) {
    		INode inode = INode.getINode(entry.addr, type, true);
    		if (inode != null) {
    			fileTable.remove(filename);
    			lock.release();
    			inode.decreaseLinkCount();
    			inode.tryFree(); 
    			return true;
    		}
    	}
    	lock.release();
    	return false;
    }
	  
   
    
	/** save the content of the folder to the disk */
	public void save ()
	{
		lock.acquire();
		write(Lib.bytesFromInt(fileTable.size()), 0, INode.WordSize);
		for (FolderEntry entry: fileTable.values()) {
			write(RealFileSystem.extFixedLenStr(entry.name),0, FileStat.FILE_NAME_MAX_LEN);
			write(Lib.bytesFromInt(entry.addr), 0, INode.WordSize);
		}
		lock.release();
	}
	
	/** load the content of the folder from the disk */
	public void load ()
	{
		lock.acquire();
		byte[] num = new byte[INode.WordSize];
		read(num, 0, INode.WordSize);
		int n = Lib.bytesToInt(num, 0);
		fileTable.clear();
		byte[] str = new byte[FileStat.FILE_NAME_MAX_LEN];
		for (int i = 0; i < n; ++i) {
			read(str, 0, FileStat.FILE_NAME_MAX_LEN);
			read(num, 0, INode.WordSize);
			String name = new String(str);
			fileTable.put(name, new FolderEntry(name, Lib.bytesToInt(num, 0)));
		}
		lock.release();
	}
	  
	/**
	 * even if the filetable is empty, new String[0] is returned;
	 * @return the filesnames in the table.
	 */
	String[] readDir() {
		lock.acquire();
		String[] filenames = new String[fileTable.size()];
		int i = 0;
		for (FolderEntry entry: fileTable.values()) {
			filenames[i] = entry.name;
		}
		lock.release();
		return filenames;
	}
	 
	public Integer getFileSec(String name) {
		FolderEntry entry = fileTable.get(name);
		return entry == null? null: entry.addr;
	}
	
	public static boolean exist(String absName) {
		tableLock.acquire();
		Folder folder = folderTable.get(absName);
		tableLock.release();
		if (folder == null) return false;
		return true;
	}
	  
	public static void saveAll() {
		for (Folder folder:folderTable.values()) {
			folder.save();
		}
	}
	  
}
