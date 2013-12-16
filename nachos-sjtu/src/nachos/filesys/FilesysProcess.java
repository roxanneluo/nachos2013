package nachos.filesys;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.userprog.UserKernel;
import nachos.vm.VMProcess;

/**
 * FilesysProcess is used to handle syscall and exception through some callback methods.
 * 
 * @author starforever
 */
public class FilesysProcess extends VMProcess
{
	protected static final int SYSCALL_MKDIR = 14;
	protected static final int SYSCALL_RMDIR = 15;
	protected static final int SYSCALL_CHDIR = 16;
	protected static final int SYSCALL_GETCWD = 17;
	protected static final int SYSCALL_READDIR = 18;
	protected static final int SYSCALL_STAT = 19;
	protected static final int SYSCALL_LINK = 20;
	protected static final int SYSCALL_SYMLINK = 21;
  
	/**
	 * Create a directory named pathname.
	 *
	 * Return zero on success, or -1 if an error occurred.
	 */
	protected int handleMkDir(int pathNameVaddr) {
		try {
			String path = readVirtualMemoryString(pathNameVaddr, maxFileNameLength);
			if (path == null) return -1;
			return FilesysKernel.realFileSystem.createFolder(path)? 0: -1;
		} catch(Exception e) {
			return -1;
		}
	}
	
	/**
	 * Delete a directory, which must be empty.
	 *
	 * On success, zero is returned. On error, -1 is returned.
	 */
	protected int handleRmDir(int pathNameVaddr) {
		try {
			String path = readVirtualMemoryString(pathNameVaddr, maxFileNameLength);
			if (path == null) return -1;
			return FilesysKernel.realFileSystem.removeFolder(path)? 0: -1;
		} catch(Exception e) {
			return -1;
		}
	}
	
	/**
	 * Change the working directory of the calling process to the directory
	 * specified in pathname.
	 * 
	 * On success, zero is returned. On error, -1 is returned.
	 */
	protected int handleChDir(int pathNameVaddr) {
		try {
			String path = readVirtualMemoryString(pathNameVaddr, maxFileNameLength);
			if (path == null) return -1;
			return FilesysKernel.realFileSystem.changeCurFolder(path)? 0: -1;
		} catch(Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
	/**
	 * Get current working directory.
	 *
	 * The getcwd() copies an absolute pathname of the current  working directory
	 * to the array pointed by buf, which is of length size.
	 *
	 * Return -1 on failure (for example, if the current absolute path name would
	 * require a buffer longer than size elements), and the number of characters
	 * stored in buf on success.
	 */
	protected int handleGetCWD(int buffPathVaddr, int size) {
		try {
			String path = FilesysKernel.realFileSystem.getCWD();
			if (path.length()+1>size) return -1;
			int cnt = writeVirtualMemory(buffPathVaddr, getNullTerminatedBytes(path));
			if (cnt <= size) return -1;
			return 0;
		} catch(Exception e) {
			return -1;
		}
	}
	
	protected byte[] getNullTerminatedBytes(String str) {
		byte[] data = new byte[str.length()+1];
		for (int i = 0; i < str.length(); ++i)
			data[i] = (byte)str.charAt(i);
		data[str.length()] = 0;
		return data;
	}
	/**
	 * Get directory entries name in the directory.
	 *
	 * The readdir() copies all entries's name  in the directory named dirname to
	 * the array pointed by buf, which is char[size][namesize]
	 * 
	 * Return -1 on failure (for example, if directory named dirname doesn't exist or
	 * entry name longer than namesize elements or the number of entries bigger
	 * than size), and the number of entries stored in buf on success.
	 * 
	 */
	protected int handleReadDir(int dirNameVaddr, int buffVaddr, int size, int nameSize) {
		try {
			String dirName = readVirtualMemoryString(dirNameVaddr,maxFileNameLength);
			if (dirName == null) return -1;
			String[] fileNames = FilesysKernel.realFileSystem.readDir(dirName);
			if (fileNames.length>size) return -1;
			for (int i = 0; i < fileNames.length; ++i) {
				if (fileNames[i].length()+1 > size) return -1;
				writeVirtualMemory(buffVaddr, getNullTerminatedBytes(fileNames[i]));
				buffVaddr += nameSize;
			}
			return 0;
		} catch (Exception e) {
			return -1;
		}
	}
	/**
	 * Get file statistic
	 *
	 * The stat() copies all file statistic to the stat
	 * 
	 *  Return 0 on success, and -1 on failure.
	 *  
	 *  typedef struct FileStatType {
     *		char name[FileNameMaxLen]; // name
     *		int size; // size in bytes
     *		int sectors; // number of sectors occupied
     *		int type; // NormalFileType(0), DirFileType(1) or LinkFileType(2)
     *		int inode; // the address of the (first) iNode
     *		int links; // number of links (with regard to hard link, not symbolic link)
	 *	} FileStat;
	 */
	protected int handleStat(int fileNameVaddr, int FileStatVaddr) {
		try {
			String fileName = readVirtualMemoryString(fileNameVaddr,maxFileNameLength);
			if (fileName == null) return -1;
			FileStat fileStat = FilesysKernel.realFileSystem.getStat(fileName);
			if (fileStat == null) return -1;
			if (writeVirtualMemory(FileStatVaddr,RealFileSystem.extFixedLenStr(fileStat.name)) <= maxFileNameLength) return -1;
			if (writeVirtualMemory(FileStatVaddr += maxFileNameLength, Lib.bytesFromInt(fileStat.size)) < INode.WordSize)
				return -1;
			if (writeVirtualMemory(FileStatVaddr += INode.WordSize, Lib.bytesFromInt(fileStat.sectors)) < INode.WordSize)
				return -1;
			if (writeVirtualMemory(FileStatVaddr += INode.WordSize, Lib.bytesFromInt(fileStat.type)) < INode.WordSize)
				return -1;
			if (writeVirtualMemory(FileStatVaddr += INode.WordSize, Lib.bytesFromInt(fileStat.inode)) < INode.WordSize)
				return -1;
			if (writeVirtualMemory(FileStatVaddr += INode.WordSize, Lib.bytesFromInt(fileStat.links)) < INode.WordSize)
				return -1;
			return 0;
		} catch (Exception e) {
			return -1;
		}
	}
	
	/**
	 *    The link() creates a new link (also known as a hard link) to an existing file.
	 * 
	 *    If newpath exists it will not be overwritten.
	 *
	 *    This new name may be used exactly as the old one for any operation; both names
	 *    refer to the same file (and so have the same permissions and ownership) and it
	 *    is impossible to tell which name was the "original".
	 *
	 *    On success, zero is returned.  On error, -1 is returned.
	 **/
	protected int handleLink(int oldNameVaddr, int newNameVaddr) {
		try {
			String oldName = readVirtualMemoryString(oldNameVaddr, maxFileNameLength);
			String newName = readVirtualMemoryString(newNameVaddr, maxFileNameLength);
			if (oldName == null || newName == null)
				return -1;
			return FilesysKernel.realFileSystem.createLink(oldName, newName)? 0:-1;
		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
			return -1;
		}
	}
	
	/**
	 *    The symlink() creates a new symbolic link to an existing file.
	 * 
	 *    If newpath exists it will not be overwritten.
	 *    
	 *     A  symbolic link is a special type of file whose contents are a string that is
	 *     the pathname another file, the file to which the link refers.  In other words,
	 *     a symbolic link is a pointer to another name, and not to an underlying object.
	 *     For this reason, symbolic links may refer to directories and  may  cross  file
	 *     system boundaries.
	 *
	 *    There  is  no  requirement  that  the  pathname referred to by a symbolic link
	 *    should exist.  A symbolic link that refers to a pathname that does  not  exist
	 *    is said to be a dangling link.
	 *
	 *    On success, zero is returned.  On error, -1 is returned.
	 **/
	protected int handleSymLink(int oldNameVaddr, int newNameVaddr) {
		try {
			String oldName = readVirtualMemoryString(oldNameVaddr, maxFileNameLength);
			String newName = readVirtualMemoryString(newNameVaddr, maxFileNameLength);
			if (oldName == null || newName == null) return -1;
			return FilesysKernel.realFileSystem.createSymlink(oldName, newName)? 0:-1;
		} catch (Exception e){
			return -1;
		}
	}
	
	public int handleSyscall (int syscall, int a0, int a1, int a2, int a3)
	{
		switch (syscall)
		{
		/*case syscallCreate:
			return handleCreateOpen(a0,true);
		case syscallOpen:
			return handleCreateOpen(a0, false);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);*/
		case SYSCALL_MKDIR:
			return handleMkDir(a0);
		case SYSCALL_RMDIR:
			return handleRmDir(a0);
		case SYSCALL_CHDIR:
			return handleChDir(a0);
		case SYSCALL_GETCWD:
			return handleGetCWD(a0, a1);
	    case SYSCALL_READDIR:
	    	return handleReadDir(a0, a1, a2, a3);
	    case SYSCALL_STAT:
	    	return handleStat(a0, a1);
	    case SYSCALL_LINK:
	    	return handleLink(a0, a1);
	    case SYSCALL_SYMLINK:
	    	return handleSymLink(a0, a1);
	    default:
	    	return super.handleSyscall(syscall, a0, a1, a2, a3);
		}
	}
  
	public void handleException (int cause)
	{
		Processor processor = Machine.processor();
		if (cause == Processor.exceptionSyscall) {
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0), processor
							.readRegister(Processor.regA1), processor
							.readRegister(Processor.regA2), processor
							.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
		} else
			super.handleException(cause);
	}
  
}
