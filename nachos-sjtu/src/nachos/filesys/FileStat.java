package nachos.filesys;

import nachos.machine.Lib;

public class FileStat
{
	public static final int FILE_NAME_MAX_LEN = 256;
	public static final int NORMAL_FILE_TYPE = 0;
	public static final int DIR_FILE_TYPE = 1;
	public static final int LINK_FILE_TYPE = 2;
  
	public String name;
	public int size;
	public int sectors;
	public int type;
	public int inode;
	public int links;
	
	
	FileStat(String name, int size, int sectors, int type, int inodeAddr, int links) {
		this.name = name;
		this.size = size;
		this.sectors = sectors;
		this.inode = inodeAddr;
		this.links = links;
		switch (type) {
		case INode.TYPE_FILE: case INode.TYPE_FILE_DEL:
			type = NORMAL_FILE_TYPE;
			break;
		case INode.TYPE_FOLDER: case INode.TYPE_FOLDER_DEL:
			type = DIR_FILE_TYPE;
			break;
		case INode.TYPE_SYMLINK:
			type = LINK_FILE_TYPE;
			break;
		default:
			Lib.assertNotReached();
		}
  }
}
