package nachos.filesys;

import nachos.machine.Config;
import nachos.vm.VMKernel;

/**
 * A kernel with file system support.
 * 
 * @author starforever
 */
public class FilesysKernel extends VMKernel
{
  public static final char DEBUG_FLAG = 'f';
  
  public static RealFileSystem realFileSystem;
  
  public FilesysKernel ()
  {
    super();
    swapFileName = "/SWAP";
  }
  
  public void initialize (String[] args)
  {
	  super.initExceptSwap(args);
	  boolean format = Config.getBoolean("FilesysKernel.format");
	  fileSystem = realFileSystem = new RealFileSystem();
	  realFileSystem.init(format);
	  swapFile = fileSystem.open(swapFileName, true);
  }
  
  public void selfTest ()
  {
    super.selfTest();
  }
  
  public void terminate ()
  {
    realFileSystem.finish();
    super.terminate();
  }
}
