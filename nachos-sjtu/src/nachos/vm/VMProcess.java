package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.machine.CoffSection;
import nachos.machine.Config;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.userprog.UserKernel;
import nachos.userprog.UserProcess;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		for (int i = 0; i < Machine.processor().getTLBSize();++i)
			myTLB[i] = null;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		// TODO:so far just invalidate them, don't store myTLB
		// will updating the 4 tlb entries be interrupt??
		Lib.debug(dbgVM, "====="+pid+" context switch=====");
		Processor p = Machine.processor();
		TranslationEntry entry = null;
		for (int i = 0; i < p.getTLBSize(); ++i) {
			entry = p.readTLBEntry(i);
			if (entry == null || !entry.valid)
				myTLB[i] = null;
			else  {
				myTLB[i] = entry.vpn;
				VMKernel.updateIPTByTLBEntry(pid, entry);
			}
		}
////		printTLB();
//		VMKernel.TLBLock.acquire();
//		VMKernel.updateIPTByTLB(pid, true);
//		VMKernel.TLBLock.release();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		// TODO: 
		//super.restoreState();
//		boolean status = Machine.interrupt().disable();
		String tlb = myTLB[0]+","+myTLB[1]+","+myTLB[2]+","+myTLB[3];
		Lib.debug(dbgVM, pid+" myTLB:"+tlb);
		for (int i = 0; i < Machine.processor().getTLBSize(); ++i) {
			if (myTLB[i] != null) 
				VMKernel.setTLB(i,pid,myTLB[i]);
			else VMKernel.setTLB(i, pid, -1);
		}
		Lib.debug(dbgVM, "===========context switch back to "+pid+"===============");
//		printTLB();
//		VMKernel.print("");
//		Machine.interrupt().restore(status);
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		//TODO: do nothing but simple checks, set all pages unloaded

		printTLB();
		for (int i = 0; i < Machine.processor().getTLBSize(); ++i)
			myTLB[i] = null;
//		VMKernel.updateIPTByTLB(pid, true);
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		VMKernel.invalidateTLB();
		VMKernel.unloadProcessPages(pid, numPages);
		coff.close();
	}
	
	protected int handleExecute(int fileNameVaddr, int argc, int argvVaddr) {
		saveState();
		VMKernel.updateIPTByTLB(pid, true);
		return super.handleExecute(fileNameVaddr, argc, argvVaddr);
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
		Lib.debug(dbgVM, "exception "+cause);
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			handleTLBMiss();
			break;
		default:
			super.handleException(cause);
			break;
		}
	}
	
	@Override
	protected int copyVirualMemory(int vaddr, byte[] data, int offset, int length, boolean read) {
		//System.out.println("offset:"+offset+",length:"+length+",data.len:"+data.length);
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (!validVaddr(vaddr))
			return -1;

		int pageOffset, ppn, vpn, paddr;
		int totalAmount = 0, amount;
		
		
		while (length > 0) {
			pageOffset = Processor.offsetFromAddress(vaddr);
			vpn = Processor.pageFromAddress(vaddr);
			
			VMKernel.IPTLock.acquire();
			TranslationEntry entry = reqPage(vpn);
			Lib.assertTrue(entry != null && entry.valid);
			// otherwise a lock should be added here
			if (!read && entry.readOnly) {
				VMKernel.IPTLock.release();
				return -1;
			}
			
			ppn = entry.ppn;
			entry.used = true;
			if (!read) entry.dirty = true;
			
			Lib.debug(dbgVM, "======copy from"+vpn+"========");

			paddr = ppn*pageSize+pageOffset;
			amount = Math.min(length, pageSize-pageOffset);
			if (read) {
				System.arraycopy(memory, paddr, data, offset, amount);
			} else {
				System.arraycopy(data, offset, memory, paddr, amount);
			}
			VMKernel.IPTLock.release();
			
			vaddr += amount;
			length -= amount;
			offset += amount;
			totalAmount += amount;
		}
		return totalAmount;
	}
	
	protected void handleTLBMiss() {
		int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
		int vpn = Processor.pageFromAddress(vaddr);
		if (vpn < 0) handleException(Processor.exceptionBusError);	//FIXME
		
		Lib.debug(dbgVM, "vaddr = "+vaddr);
		Lib.debug(dbgVM, "=====miss <"+pid+","+vpn+">======");
		Lib.assertTrue(0<=vpn && vpn < numPages, "process "+pid+"req vpn:"+vpn+" while numPages="+numPages);

		VMKernel.IPTLock.acquire();
		TranslationEntry entry = reqPage(vpn);

		Lib.debug(dbgVM,"===replace TLB with "+entry+"===");
		VMKernel.replaceTLB(pid, entry);
		VMKernel.IPTLock.release();	//ERROR: have to be released until here. o.w. may be context switch; kick the page; then replace
		printTLB();
		
	}
	private TranslationEntry reqPage(int vpn) {
		Lib.assertTrue(VMKernel.IPTLock.isHeldByCurrentThread());
		TranslationEntry entry = VMKernel.getIPTEntry(pid, vpn);
		if (entry != null) return entry;
		
		int ppn = VMKernel.obtainFreePPN(vpn);	// swap out if need
		Lib.assertTrue(ppn >= 0 && ppn < Processor.pageSize);
		entry = VMKernel.swapIn(ppn, pid, vpn);
		if (entry != null) return entry;

		//load new page
		entry = new TranslationEntry(vpn,ppn,true,false,true,false);
		VMKernel.addIPTPage(pid, entry);
		
		if (vpn < numPages-stackPages-argPages) {
			Lib.debug(dbgVM, "====req coff page"+vpn+"====");

			int index = getCoffIndex(vpn);
			CoffSection s = coff.getSection(index);
			Lib.assertTrue(s.getFirstVPN()<=vpn && vpn < s.getFirstVPN()+s.getLength());
			entry.readOnly = s.isReadOnly();
			
			Lib.debug(dbgVM, pid+"ready to load <vpn:"+vpn+
							"from the "+(vpn-s.getFirstVPN())+"th page in" +
							"sec["+index+"]("+coff.getNumSections()+")="+s+
							"to "+entry);
			
			s.loadPage(vpn-s.getFirstVPN(),ppn);
		} else {
			Lib.debug(dbgVM,"====req stack page"+vpn+"====");
		}
		return entry;
	}
	
	int getCoffIndex(int vpn) {
		int numSections = coff.getNumSections();
		CoffSection section = coff.getSection(numSections-1);
		if (section.getFirstVPN()<=vpn)
			return numSections-1;
		int l = 0, r = numSections-1, mid;
		while (r-l>1) {
			mid = (l+r)/2;
			section = coff.getSection(mid);
			if (section.getFirstVPN() <= vpn)
				l = mid;
			else r = mid;
		}
		return l;
	}
	void printTLB() {
		String TLB = pid+" TLB:";
		Processor p = Machine.processor();
		for (int i = 0; i < p.getTLBSize(); ++i) {
			TranslationEntry entry = p.readTLBEntry(i);
			TLB+=entry+" ,";
		}
		TLB += "\n";
		Lib.debug(dbgVM, TLB);
	}
	
	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	
//	private boolean[] loaded; 
	protected final int argPages = Config.getInteger("Processor.numArgPages", 1);
	private Integer[] myTLB = new Integer[Machine.processor().getTLBSize()];
}

