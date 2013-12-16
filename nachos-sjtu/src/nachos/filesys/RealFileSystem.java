package nachos.filesys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import nachos.machine.FileSystem;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;

/**
 * RealFileSystem provide necessary methods for filesystem syscall.
 * The FileSystem interface already define two basic methods, you should implement your own to adapt to your task.
 * 
 * @author starforever
 */
public class RealFileSystem implements FileSystem
{ 
	/** the free list */
  private FreeList freeList;

  /** the root folder */
  private Folder rootFolder;

  /** the current folder */
  //private Folder curFolder;

  /** the string representation of the current folder */
  private LinkedList<String> curPath = new LinkedList<String>();
  private String nameInside ;

  public static final String Cur = ".";
  public static final String Parent="..";
  public static final String PathSplitor = "/";
  public static final String Root = "/";
  public static final String RootFolderName = "MYROOT";

  public static final char dbgFilesys = 'f';
  
  public RealFileSystem() {
	  curPath.add(RootFolderName);
	  nameInside = pathList2String(curPath);
  }
  /**
   * initialize the file system
   * 
   * @param format
   *          whether to format the file system
   */
  public void init (boolean format)
  {
    if (format)
    {
    	INode inodeFreeList = INode.getINode(FreeList.STATIC_ADDR, INode.TYPE_SYSTEM, false);
    	freeList = new FreeList(inodeFreeList);
    	freeList.init();
    	
    	INode inodeRootFolder = INode.getINode(Folder.STATIC_ADDR, INode.TYPE_FOLDER,false);
    	rootFolder = Folder.getFolder(RootFolderName, inodeRootFolder, false);
    	importStub();

    	// new an empty freelist
    	// create root folder
    	// importStub
    }
    else
    {
      INode inode_free_list = INode.getINode(FreeList.STATIC_ADDR,INode.TYPE_SYSTEM,true);
      freeList = new FreeList(inode_free_list);
      freeList.load();
      
      INode inode_root_folder = INode.getINode(Folder.STATIC_ADDR, INode.TYPE_FOLDER,true);
      rootFolder = Folder.getFolder(RootFolderName, inode_root_folder, true);
    }
  }
  
  	public void finish ()
  	{
  		rootFolder.save();
  		freeList.save();
  		Folder.saveAll();
  		INode.saveAll();
  	}
  
  	/** import from stub filesystem */
  	private void importStub ()
  	{
  		FileSystem stubFS = Machine.stubFileSystem();
  		FileSystem realFS = FilesysKernel.realFileSystem;
  		String[] file_list = Machine.stubFileList();
  		for (int i = 0; i < file_list.length; ++i)
  		{
  			if (!file_list[i].endsWith(".coff"))
  				continue;
  			OpenFile src = stubFS.open(file_list[i], false);
  			if (src == null)
  			{
  				continue;
  			}
  			OpenFile dst = realFS.open(file_list[i], true);
  			int size = src.length();
  			byte[] buffer = new byte[size];
  			src.read(0, buffer, 0, size);
  			dst.write(0, buffer, 0, size);
  			src.close();
  			dst.close();
  		}
  		Lib.debug(dbgFilesys, "import done");
  	}
  
  	/** get the only free list of the file system */
  	public FreeList getFreeList ()
  	{
  		return freeList;
  	}

  	/** get the only root folder of the file system */
  	public Folder getRootFolder ()
  	{
  		return rootFolder;
  	}

  	public OpenFile open (String name, boolean create) {
  		// TODO not deal with symlink
  		String absFileName = getAbsFileName(name);
  		PathResult result = getResult(absFileName);	// folders should be loaded
  		if (!result.success)
  			return null;
  		
  		result.parentFolder.lock.acquire();
  		Integer fileSec = result.parentFolder.getFileSec(result.name);
  		INode inode = null;
  		boolean load = true;
  		if (fileSec == null)
  			if (!create)  {
  				result.parentFolder.lock.release();
  				return null;
  			} else {
  				fileSec = freeList.allocate();
  				if (fileSec == null) {
  					Lib.debug(dbgFilesys, "disk full when create "+name);
  					result.parentFolder.lock.release();
  					return null;
  				}
  				load = false;
  				inode = result.parentFolder.addEntry(result.name, fileSec, INode.TYPE_FILE, false);
  			}
  		else {
  			inode = INode.getINode(fileSec, INode.TYPE_FILE, load);
  			if (inode == null)
  				inode = INode.getINode(fileSec, INode.TYPE_SYMLINK, load);
  		}
  		result.parentFolder.lock.release();
  		//inode is null when the name corresponds to a folder
  		if (inode == null) return null;
  		
  		if (inode.fileType == INode.TYPE_SYMLINK) 
  			return openSymLink(inode);
  		
  		
  		inode.increaseUseCnt();
  		Lib.debug(dbgFilesys, "open "+absFileName+" @"+fileSec);
  		return new File(inode);	
  	}

  	protected OpenFile openSymLink(INode inode) {
  		File file = new File(inode);
  		byte[] num = new byte[INode.WordSize];
  		file.read(num, 0, INode.WordSize);
  		int n = Lib.bytesToInt(num, 0);
  		byte[] path = new byte[n];
  		file.read(path, 0, n);
  		String trueName = new String(path);
  		return open(new String(path), false);
  	}
  	
  	public boolean remove (String name)
  	{
  		//TODO: symlink not deal with
  		PathResult result = getResult(getAbsFileName(name));
  		Lib.debug(dbgFilesys, "try to remove "+getAbsFileName(name));
  		if (!result.success) return false;
  		return result.parentFolder.removeEntry(result.name, INode.TYPE_FILE)
  				|| result.parentFolder.removeEntry(result.name, INode.TYPE_SYMLINK);
  	}

  	public boolean createFolder (String name)
  	{
  		String absName = getAbsFileName(name);
  		PathResult result = getResult(absName);
  		if (!result.success) return false;
  		result.parentFolder.lock.acquire();
  		Integer addr = result.parentFolder.getFileSec(result.name);
  		if (addr == null) {
  			addr = freeList.allocate();
  			if (addr != null) {
  				Lib.assertTrue(result.parentFolder.addEntry(result.name, addr, INode.TYPE_FOLDER, false) != null);
  				result.parentFolder.lock.release();
  				return true;
  			}
  		}
  		result.parentFolder.lock.release();
  		return false;  		
  	}

  	public boolean removeFolder (String name)
  	{
  		String absName = getAbsFileName(name);
  		PathResult result = getResult(absName);
  		if (!result.success)
  			return false;
  		result.parentFolder.lock.acquire();
  		Integer addr = result.parentFolder.getFileSec(result.name);
  		if (addr != null) {
  			INode inode = INode.getINode(addr, INode.TYPE_FOLDER, true);
  			Folder folder = Folder.getFolder(absName, inode, true);
  			if (folder.isEmpty()) {
  				result.parentFolder.lock.release();
  				result.parentFolder.removeEntry(result.name, INode.TYPE_FOLDER);
  				return true;
  			}
  		}
  		result.parentFolder.lock.release();
  		return false;
  	}

  	public String getCWD() {
  		return getPathNameOutside(nameInside);
  	}
  	public boolean changeCurFolder (String name)
  	{
  		String absName = getAbsFileName(name);
  		PathResult result = getResult(absName);
  		if (!result.success) return false;
  		if (result.parentFolder == null) {
  			curPath.clear();
  			curPath.add(RootFolderName);
  			nameInside = RootFolderName;
  			return true;
  		}
  		result.parentFolder.lock.acquire();// FIXME: do I really need the lock of folder?
  		Integer addr = result.parentFolder.getFileSec(result.name);
  		result.parentFolder.lock.release();
  		if (addr == null) return false;
  		INode inode = INode.getINode(addr, INode.TYPE_FOLDER, true);
  		if (inode == null) return false;
  		Lib.assertTrue(Folder.getFolder(absName, inode, true) != null, 
  				"change dir to a null folder");
  		curPath = new LinkedList<String>(Arrays.asList(absName.split(PathSplitor)));
  		nameInside = absName;
  		Lib.debug(dbgFilesys, "change dir to "+nameInside);
  		return true;
  	}

  	public String[] readDir (String name)
  	{
  		String absName = getAbsFileName(name);
  		PathResult result = getResult(absName);
  		if (!result.success)
  			return null;
  		
  		result.parentFolder.lock.acquire();
  		Integer addr = result.parentFolder.getFileSec(result.name);
  		if (addr != null ){
  			INode inode = INode.getINode(addr, INode.TYPE_FOLDER, true);
  			if (inode != null) {
  				Folder folder = Folder.getFolder(absName, inode, true);
  				result.parentFolder.lock.release();
  				return folder.readDir();
  			}
  		}
  		result.parentFolder.lock.release();
  		return null;
  	}

  	public FileStat getStat (String name)
  	{
  		PathResult result = getResult(getAbsFileName(name));
  		if (!result.success) return null;
  		result.parentFolder.lock.acquire();
  		Integer addr = result.parentFolder.getFileSec(result.name);
  		if (addr != null) {
  			INode inode = INode.getINode(addr, null, true);
  			result.parentFolder.lock.release();
  			return new FileStat(result.name, inode.fileSize, inode.getSectorNum(),inode.fileType,inode.getAddr(), inode.linkCount);
  		}
  		
  		result.parentFolder.lock.release();
  		return null;
  	}
  	
  	// succeed iff src exists and is file and dst's parent exist, dst not exist
  	public boolean createLink (String src, String dst)
  	{
  		PathResult srcResult = getResult(getAbsFileName(src));
  		if (!srcResult.success) return false;
  		Integer addr = srcResult.parentFolder.getFileSec(srcResult.name);
  		if (addr == null) return false;
  		
  		PathResult dstResult = getResult(getAbsFileName(dst));
  		if (!dstResult.success) return false;
  		Lib.debug(dbgFilesys, dstResult.parentFolder.toString());
  		dstResult.parentFolder.lock.acquire();
  		Integer dstAddr = dstResult.parentFolder.getFileSec(dstResult.name);
  		if (dstAddr == null) {
  			INode inode = dstResult.parentFolder.addEntry(dstResult.name, addr, INode.TYPE_FILE, true);
  			if (inode != null) {
  				dstResult.parentFolder.lock.release();
  				return true;
  			}
  		}
  		dstResult.parentFolder.lock.release();
  		return false;
  	}
  	

  	public static String getPathNameOutside(String nameInside) {
  		char splitor = PathSplitor.charAt(0);
  		for (int i = 0; i < nameInside.length(); ++i) {
  			if (nameInside.charAt(i)==splitor) {
  				return nameInside.substring(i);
  			}
  		}
  		return PathSplitor;
  	}
  	
  	// old name refers to an "existing" "file", 
  	public boolean createSymlink (String src, String dst)
  	{
  		String srcAbsName = getAbsFileName(src);
  		PathResult srcResult = getResult(srcAbsName);
  		if (!srcResult.success) return false;
  		Integer srcAddr = srcResult.parentFolder.getFileSec(srcResult.name);
  		if (srcAddr == null) return false;
  		
  		PathResult dstResult = getResult(getAbsFileName(dst));
  		if (!dstResult.success) return false;
  		dstResult.parentFolder.lock.acquire();
  		Integer dstAddr = dstResult.parentFolder.getFileSec(dstResult.name);
  		if (dstAddr == null) {
  			Integer addr = freeList.allocate();
  			if (addr != null) {
	  			INode inode = dstResult.parentFolder.addEntry(dstResult.name, addr, INode.TYPE_SYMLINK, false);
	  			if (inode != null) {
	  				dstResult.parentFolder.lock.release();
	  				File file = new File(inode);
	  				srcAbsName = getPathNameOutside(srcAbsName);
	  				file.write(Lib.bytesFromInt(srcAbsName.length()), 0, INode.WordSize);
	  				byte[] nameArray = srcAbsName.getBytes();
	  				int cnt = file.write(nameArray, 0, nameArray.length);
	  				if (cnt == srcAbsName.length())
	  					return true;
	  				else {
	  					// FIXME: do i need this?
	  					remove(srcAbsName);
	  					return false;
	  				}
	  			}
  			}
  		}
  		dstResult.parentFolder.lock.release();
  		return false;
  	}

  	public int getFreeSize()
  	{
  		return freeList.freeSize();
  	}

  	public int getSwapFileSectors()
  	{
  		return getStat(FilesysKernel.getSwapFileName()).sectors;
  	}
  
  	// the last Entry has no "/"
	public String getAbsFileName(String name) {
		LinkedList<String> cwdPath = null;
		if (name.startsWith(Root)) {
			cwdPath = new LinkedList<String>();
			cwdPath.add(RootFolderName);
		} else {
			cwdPath = (LinkedList<String>) curPath.clone();
		}
		
		ArrayList<String> path = new ArrayList<String>(Arrays.asList(name.split(PathSplitor)));
		String si;
		for (int i = 0; i < path.size(); ++i) {
			si = path.get(i);
			if (si.isEmpty()) continue;
			switch(si) {
			case Parent:
				cwdPath.removeLast();
				break;
			case Cur:
				continue;
			default:
				cwdPath.add(si);
			}
		}
		return pathList2String(cwdPath);
	}
	
	private static String pathList2String(LinkedList<String> path) {
		String ans = RootFolderName;
		Iterator<String> iter = path.iterator();
		iter.next();
		while (iter.hasNext()) {
			ans += PathSplitor+iter.next();
		}
		return ans;
	}
	
	public static byte[] extFixedLenStr(String name) {
    	byte[] str = new byte[FileStat.FILE_NAME_MAX_LEN];
    	for (int i = 0; i < name.length(); ++i) {
    		str[i] = (byte) name.charAt(i);
    	}
    	return str;
	}
	
	protected PathResult getResult(String absName) {
		Lib.debug(dbgFilesys, "get result of "+absName);
		PathResult result = new PathResult();
		
		LinkedList<String> path = new LinkedList<String>(Arrays.asList(absName.split(PathSplitor)));
		result.name = path.removeLast();
		if (path.size() == 0) {
			result.success = true;
			result.parentFolder = null;
			return result;
		}
		
		ListIterator<String> iter = path.listIterator();
		String absFolderName = "",lastFolderName = "";
		if (absName.contains(nameInside)) {
			absFolderName = lastFolderName = nameInside;
			for (int i = 0; i < curPath.size(); ++i)
				iter.next();
		} else {
			absFolderName = RootFolderName;
			iter.next();
		}
		while(iter.hasNext()) {
			lastFolderName = absFolderName;
			absFolderName += PathSplitor+iter.next();
			if (!Folder.exist(absFolderName)) {
				absFolderName = lastFolderName;
				iter.previous();
				break;
			}
		}
		
		Folder folder = Folder.getExistingFolder(absFolderName);
		Lib.assertTrue(folder != null, "in getResult, existingFolder:"+absFolderName+" == null");
		String cur = null;
		while (iter.hasNext()) {
			cur = iter.next();
			Lib.debug(dbgFilesys, cur);
			Integer addr = folder.getFileSec(cur);
			if (addr == null) return result;
			INode inode = INode.getINode(addr, INode.TYPE_FOLDER, true);
			if (inode == null)	return result;	// when the inode does not correspond to a folder
			folder = Folder.getFolder(absFolderName += PathSplitor+cur, inode, true);
		}
		
		result.success = true;
		result.parentFolder = folder;
		return result;
		
	}
	
	private class PathResult {
		boolean success = false;
		String name = null;	// can be of folder's or file's
		Folder parentFolder = null;	
	}
}
