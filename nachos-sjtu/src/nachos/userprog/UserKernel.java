package nachos.userprog;

import java.util.HashSet;
import java.util.Iterator;

import nachos.machine.*;
import nachos.threads.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		//pageTable
		phyPageTableLock = new Lock();
		int totalPageNum = Machine.processor().getNumPhysPages();
		for (int i = 0; i < totalPageNum; ++i) 
			freePages.add(i);
		
		runningProcessesLock = new Lock();
		
		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
//		super.selfTest();
//
//		System.out.println("Testing the console device. Typed characters");
//		System.out.println("will be echoed until q is typed.");
//
//		char c;
//
//		do {
//			c = (char) console.readByte(true);
//			console.writeByte(c);
//		} while (c != 'q');
//
//		System.out.println("");
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		KThread.finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}
	
	public static int nextFreePage() {
		int ppn;
		phyPageTableLock.acquire();
		if (freePages.isEmpty()) 
			ppn = -1;
		else {
			Iterator<Integer> iter = freePages.iterator();
			ppn = iter.next().intValue();
			iter.remove();
		}
		phyPageTableLock.release();
		return ppn;
	}
	
	public static boolean freePage(int ppn) {
		if (ppn < 0 || ppn > Machine.processor().getNumPhysPages())
			return false;
		phyPageTableLock.acquire();
		if (freePages.contains(ppn)) {
			phyPageTableLock.release();
			return false;
		}
		freePages.add(ppn);
		phyPageTableLock.release();
		return true;
	}
	
	public static void addRunningProcess() {
		runningProcessesLock.acquire();
		++runningProcesses;
		runningProcessesLock.release();
	}
	public static void removeRunningProcesses() {
		runningProcessesLock.acquire();
		--runningProcesses;
		Lib.assertTrue(runningProcesses>=0);
		runningProcessesLock.release();
	}
	
	public void terminateIfIsLastProcess() {
		runningProcessesLock.acquire();
		if (runningProcesses == 1) terminate();
		runningProcessesLock.release();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;
	
	private static Lock phyPageTableLock = null;
	private static HashSet<Integer> freePages = new HashSet<Integer>();
	
	private static Lock runningProcessesLock = null;
	private static int runningProcesses = 0;

}
