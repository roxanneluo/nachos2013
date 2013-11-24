package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.VMProcess;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		
		fileTable.add(UserKernel.console.openForReading());
		fileTable.add(UserKernel.console.openForWriting());	//reserved fd for std in and out
		
		UserKernel.addRunningProcess();
		
		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
//	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		Lib.assertTrue(offset >= 0 && length >= 0
//				&& offset + length <= data.length);
//
//		byte[] memory = Machine.processor().getMemory();
//
//		// for now, just assume that virtual addresses equal physical addresses
//		if (vaddr < 0 || vaddr >= memory.length)
//			return 0;
//
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(memory, vaddr, data, offset, amount);
//
//		return amount;
//	}
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return copyVirualMemory(vaddr, data, offset, length, true);
	}
	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
//	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
//		Lib.assertTrue(offset >= 0 && length >= 0
//				&& offset + length <= data.length);
//
//		byte[] memory = Machine.processor().getMemory();
//
//		// for now, just assume that virtual addresses equal physical addresses
//		if (vaddr < 0 || vaddr >= memory.length)
//			return 0;
//
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(data, offset, memory, vaddr, amount);
//
//		return amount;
//	}
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return copyVirualMemory(vaddr, data, offset, length, false);
	}
//check kernel check final terminate, check wiki page
	private int copyVirualMemory(int vaddr, byte[] data, int offset, int length, boolean read) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (!validVaddr(vaddr))
			return -1;

		int pageOffset, ppn, vpn, paddr;
		int totalAmount = 0, amount;
		while (length > 0) {
			pageOffset = vaddr & (pageSize-1);
			vpn = vaddr/pageSize;

			pageTableLock.acquire();
			if (!pageTable[vpn].valid) {
				pageTableLock.release(); 
				return -1;
			} else {
				if (!read && pageTable[vpn].readOnly) {
					pageTableLock.release();
					return -1;
				}
			}
			
			ppn = pageTable[vpn].ppn;
			pageTable[vpn].used = true;
			if (!read) pageTable[vpn].dirty = true;
			pageTableLock.release();
			
			paddr = ppn*pageSize+pageOffset;
			amount = Math.min(length, pageSize-pageOffset);
			if (read) {
				System.arraycopy(memory, paddr, data, offset, amount);
			} else {
				System.arraycopy(data, offset, memory, paddr, amount);
			}
			vaddr += amount;
			length -= amount;
			offset += amount;
			totalAmount += amount;
		}
		return totalAmount;
	}
	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib
					.assertTrue(writeVirtualMemory(entryOffset,
							stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset,
							new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		pageTable = new TranslationEntry[numPages];
		int pageCnt = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				++pageCnt;
				int vpn = section.getFirstVPN() + i;
				int ppn = UserKernel.nextFreePage();
				if (ppn < 0) return false;
				section.loadPage(i, ppn);
				addTranslationEntry(vpn, ppn, section.isReadOnly());
			}
		}
		for (int i = pageCnt; i < numPages; ++i) {
			int ppn = UserKernel.nextFreePage();
			if (ppn < 0) return false;
			addTranslationEntry(i, ppn, false);
		}

		return true;
	}
	
	protected void addTranslationEntry(int vpn, int ppn, boolean isReadOnly) {
		//TODO: so far I don't think here needs any lock;
		pageTable[vpn] = new TranslationEntry(vpn, ppn, true, isReadOnly, false, false);
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for (int i = 0; i < numPages; ++i)
			if (pageTable[i].valid) {
				// how could it be invalid
				Lib.assertTrue(UserKernel.freePage(pageTable[i].ppn));
				pageTable[i].valid = false;
			}
		coff.close();
	}
	
	private void closeAllFiles() {
		// TODO: do I need the lock here; So far I don't think so;
		lock.acquire();
			for (int i = 0; i < fileTable.size(); ++i) {
				OpenFile file = fileTable.get(i);
				if (file != null) {
					Lib.assertTrue(!freeFD.contains(i));
					file.close();
				}
			}
//				freeFD.clear();
		lock.release();
	}
	
	/**
	 *  need to be added somewhere so that whenever the process exits
	 *  (whether it exits normally, via the syscall exit(), or abnormally,
	 *  due to an illegal operation).  finish() is called.
	 */
	private void clear() {
		closeAllFiles();
		unloadSections();
		exited = true;
		UserKernel.removeRunningProcesses();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if (id == 0) {
			clear();
			Machine.halt();
			return 0;
		} else return -1;

//		Lib.assertNotReached("Machine.halt() did not halt machine!");
//		return -1;
	}
	
//	private int byte2int(byte[] data) {
//		Lib.assertTrue(data.length == intSize);
//		int num = 0;
//		int byteLen = byteSize*8;
//		for (int i = intSize-1; i >=0; --i) {
//			num <<= byteLen;
//			num |= (0x00FF & data[i]);
//		}
//		return num;
//	}
//	private byte[] int2byte(int num) {
//		byte data[] = new byte[intSize];
//		int byteLen = byteSize*8;
//		for (int i = 0; i < intSize; ++i){
//			data[i] = (byte) (num&(1<<byteLen-1));
//			num = num>>8;
//		}
//		return data;
//	}
	private void handleExit(int status) {
		clear();
		exitStatus = status;
		((UserKernel)Kernel.kernel).terminateIfIsLastProcess();
		UThread.finish();
	}
	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * file is a null-terminated string that specifies the name of the file
	 * containing the executable. Note that this string must include the ".coff"
	 * extension.
	 *
	 * argc specifies the number of arguments to pass to the child process. This
	 * number must be non-negative.
	 *
	 * argv is an array of pointers to null-terminated strings that represent the
	 * arguments to pass to the child process. argv[0] points to the first
	 * argument, and argv[argc-1] points to the last argument.
	 *
	 * exec() returns the child process's process ID, which can be passed to
	 * join(). On error, returns -1.
	 */
	protected int handleExecute(int fileNameVaddr, int argc, int argvVaddr) {
		// read file name, check .coff, openfile 
		String fileName = readVirtualMemoryString(fileNameVaddr, maxFileNameLength);
		if (fileName == null || !fileName.endsWith(".coff")) return -1;
		
		// prepare args,argc>=0 new String[argc], for argc read args
		if (argc<0) return -1;
		String[] args = new String[argc];
		byte[] data = new byte[intSize];
		for (int i = 0; i < argc; ++i) {
			if (readVirtualMemory(argvVaddr, data) < intSize) return -1;
			int vaddr = Lib.bytesToInt(data, 0);
			args[i] = readVirtualMemoryString(vaddr, maxFileNameLength);
			if (args[i] == null) return -1;
			argvVaddr+=intSize;
 		}
		// lockProcessTable(); new process, parent record the childID
		processLock.acquire();
		UserProcess child = newUserProcess();
		processLock.release();
		
		// execute
		if (!child.execute(fileName, args)) return -1;
		processLock.acquire();
		children.put(child.id, child);
		processLock.release();
		return child.id;
	}
	
	/**
	 * Suspend execution of the current process until the child process specified
	 * by the processID argument has exited. If the child has already exited by the
	 * time of the call, returns immediately. When the current process resumes, it
	 * disowns the child process, so that join() cannot be used on that process
	 * again.
	 *
	 * processID is the process ID of the child process, returned by exec().
	 *
	 * status points to an integer where the exit status of the child process will
	 * be stored. This is the value the child passed to exit(). If the child exited
	 * because of an unhandled exception, the value stored is not defined.
	 *
	 * If the child exited normally, returns 1. If the child exited as a result of
	 * an unhandled exception, returns 0. If processID does not refer to a child
	 * process of the current process, returns -1.
	 */
	private int handleJoin(int processID, int statusVaddr) {
		processLock.acquire();
		if (!children.containsKey(processID)) {
			processLock.release();
			return -1;
		}
		
		// setTheExitStatusVaddr of the child process.
		UserProcess child = children.get(processID);
		
		children.remove(processID);
		processLock.release();
		
//		System.out.println("child:"+child+", child thread:"+child.thread+", childStatus:"+child.getStatus());
		if (!child.exited && child.thread != null) {
			child.thread.join();
		}
		
		// readExitStatus, if -1 then abnormal return 0;
		int childExitStatus = child.getExitStatus();
//		System.out.println(childExitStatus);
		byte[] data = Lib.bytesFromInt(childExitStatus);
		if (writeVirtualMemory(statusVaddr, data) != intSize) return 0;	//FIXME: will this happen?
		
		if (child.exitSuccess) return 1;
		else return 0;
	}
//	private boolean hasExited() {
//		if (status == exist && thread != null) return false;
//		return true;
//	}
//	private boolean hasExitedNormally() {
//		if (status == exitNormally) return true;
//		if (status == exist && thread == null) return true;
//		return false;
//	}
	private int getNewFileDescriptor() {
		Lib.assertTrue(lock.isHeldByCurrentThread());
		if (!freeFD.isEmpty()) {
			int fd = freeFD.first();
			freeFD.remove(fd);
			return fd;
		}
		return fileTable.size();
	}
	public int getExitStatus() {
		return exitStatus;
	}
//	public int getStatus() {
//		return status;
//	}
	
	public boolean validVaddr(int vaddr) {
		if (vaddr < 0 || vaddr >= numPages*pageSize) return false;
		return true;
	}
	
	private int addFile(OpenFile file) {
		if (file == null) return -1;
		lock.acquire();
		int fd = getNewFileDescriptor();	// this and the next line must be done atomically
		fileTable.add(fd, file);
		lock.release();
		return fd;
	}
	
	/**
	 * Attempt to open the named disk file, creating it if it does not exist,
	 * and return a file descriptor that can be used to access the file.
	 *
	 * Note that creat() can only be used to create files on disk; creat() will
	 * never return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	private int handleCreateOpen(int nameVaddr, boolean create) {
		try {
			String fileName = readVirtualMemoryString(nameVaddr, maxFileNameLength);
			if (fileName == null) return -1;
			OpenFile file = UserKernel.fileSystem.open(fileName, create);
			return addFile(file);
		} catch (Exception e) {
//			System.out.println(e);
//			e.printStackTrace();
			// I don't think it will ever reach here;
			if (lock.isHeldByCurrentThread()) lock.release();
			return -1;
		}
	}
	

	/**
	 * Close a file descriptor, so that it no longer refers to any file or stream
	 * and may be reused.
	 *
	 * If the file descriptor refers to a file, all data written to it by write()
	 * will be flushed to disk before close() returns.
	 * If the file descriptor refers to a stream, all data written to it by write()
	 * will eventually be flushed (unless the stream is terminated remotely), but
	 * not necessarily before close() returns.
	 *
	 * The resources associated with the file descriptor are released. If the
	 * descriptor is the last reference to a disk file which has been removed using
	 * unlink, the file is deleted (this detail is handled by the file system
	 * implementation).
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleClose(int fd) {
		try {
			OpenFile file = getFile(fd);
			if (file == null) return -1;
			
			lock.acquire();
			file.close();
			freeFD.add(fd);
			fileTable.set(fd, null);
			lock.release();
			return 0;
		} catch (Exception e) {
			if (lock.isHeldByCurrentThread()) lock.release();
			return -1;
		} 
	}
	
	/**
	 * Delete a file from the file system. If no processes have the file open, the
	 * file is deleted immediately and the space it was using is made available for
	 * reuse.
	 *
	 * If any processes still have the file open, the file will remain in existence
	 * until the last file descriptor referring to it is closed. However, creat()
	 * and open() will not be able to return new file descriptors for the file
	 * until it is deleted.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 * 
	 * I: only these simple lines is fine
	 * if this process has opened the file, the file will not be deleted until this process
	 * close the file. If the user forgets to close the file, the file will be closed by finally 
	 * calling closeAllFile();
	 * o.w. It's not opened by this process, other process should handle the open and close itself
	 */
	
	private int handleUnlink(int nameVaddr) {
		try {
			String fileName = readVirtualMemoryString(nameVaddr, maxFileNameLength);
			if (UserKernel.fileSystem.remove(fileName)) return 0;
			else return -1;
		} catch (Exception e) {
			return -1;
		} 
	}
	
	private boolean validFD(int fd) {
		Lib.assertTrue(lock.isHeldByCurrentThread());
		
		if (fd < 0) return false;
		if (fd >= fileTable.size()) return false;
		OpenFile file = fileTable.get(fd);
		if (file == null) {
			Lib.assertTrue(freeFD.contains(fd));
			return false;
		} else {
			Lib.assertTrue(!freeFD.contains(fd));
			return true;
		}
	}
	
	
	private OpenFile getFile(int fd) {
		lock.acquire();
		OpenFile file = null;
		if (validFD(fd)) 
			file = fileTable.get(fd);
		lock.release();
		return file;
	}
	/**
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid (these are handled by writeVirtualMemory, it will return -1 on 
	 * these cases), or if a network stream has been terminated by the remote host
	 * and no more data is available.

	 * @param fd
	 * @param bufferVaddr
	 * @param size
	 * @return
	 */
	private int handleRead(int fd, int bufferVaddr, int size) {
		try {
			OpenFile file = getFile(fd);
			if (file == null) return -1;
			
			byte data[] = new byte[size];
			int readCnt = file.read(data, 0, size);
			return writeVirtualMemory(bufferVaddr, data, 0, readCnt);
		} catch (Exception e) {
			if (lock.isHeldByCurrentThread()) lock.release();
			return -1;
		} 
	}
	
	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host. I think
	 * it can also be the case where the disk is full, or the file can't be written
	 */
	private int handleWrite(int fd, int bufferVaddr, int size) {
		try {
			OpenFile file = getFile(fd);
			if (file == null) return -1;
			
			byte data[] = new byte[size];
			int readCnt = readVirtualMemory(bufferVaddr, data);
			if (readCnt < size) return -1;	// can this happen ?? TODO
			int writeCnt = file.write(data, 0, size);
			if (writeCnt < size) return -1;
			return writeCnt;
		} catch (Exception e) {
			if (lock.isHeldByCurrentThread()) lock.release();
			return -1;
		} 		
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
//		System.out.println("syscall:"+syscall);
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			handleExit(a0);
			return 0;
		case syscallExec:
			return handleExecute(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallCreate:
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
			return handleUnlink(a0);

		default:
			exitSuccess = false;
			handleExit(-1);
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0), processor
							.readRegister(Processor.regA1), processor
							.readRegister(Processor.regA2), processor
							.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;
		default:
			exitSuccess = false;
			handleExit(-1);
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
//			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	protected Lock pageTableLock = new Lock();
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = Config.getInteger("Processor.numStackPages", 8);

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	
	//for phase2 pagetable
	private static final int maxFileNameLength = 256;
//	private static final int maxFileNum = 16;
	private ArrayList<OpenFile> fileTable = new ArrayList<OpenFile>();
	private TreeSet<Integer> freeFD = new TreeSet<Integer>();	//FD stands for file descriptor
	private Lock lock = new Lock();
	private static int numCreated = 0;
	private int id = numCreated++;
	
	//for phase2 multiprogramming handle the processes
	UThread thread = null;
	private Lock processLock = new Lock();
	/** on exit, the child process just set its status to nothing but exist instead of explicitly notify the parent.
	 *  parent may check whether the child is exist or not by looking it up children and check status;
	 *  only after join will the parent disown the child, i.e. children.remove(child);
	 */
	private HashMap<Integer, UserProcess> children = new HashMap<Integer, UserProcess>();
	protected boolean exited = false;
	protected boolean exitSuccess = true;
	protected final int intSize = 4, byteSize = 1;
	private int exitStatus = -1;
}
