package nachos.threads;

import java.util.LinkedList;

import nachos.machine.Lib;
import nachos.machine.Machine;

public class ReadWriteLock {
	public ReadWriteLock() {
		
	}
	
	public void acquireWrite() {
		 Lib.assertTrue(!isHeldByCurrentThread());
		 boolean intStatus = Machine.interrupt().disable();
		 KThread thread = KThread.currentThread();
		 
		 if (holders.isEmpty()) {
			 waitQueue.acquire(thread);
			 holders.add(thread);
			 holderWriteLabel = true;
		 } else {
			 waitQueue.waitForAccess(thread);
			 waitWriteLabel.add(true);
			 KThread.sleep();			 
		 }
		 Machine.interrupt().restore(intStatus);
	}
	
	public void acquireRead() {
		Lib.assertTrue(!isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = KThread.currentThread();
		if (holders.isEmpty() || waitWriteLabel.isEmpty() && !holderWriteLabel) {
			waitQueue.acquire(thread);
			holders.add(thread);
		} else {
			waitQueue.waitForAccess(thread);
			waitWriteLabel.add(false);
			KThread.sleep();
		}
		Machine.interrupt().restore(intStatus);
	}
	
	public void release() {
		Lib.assertTrue(isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		holders.clear();
		if (!waitWriteLabel.isEmpty()) {
			if (waitWriteLabel.getFirst()) {
				waitWriteLabel.removeFirst();
				holderWriteLabel = true;
				KThread thread = waitQueue.nextThread();
				holders.add(thread);
				thread.ready();
			} else {
				holderWriteLabel = false;
				while (!waitWriteLabel.getFirst()) {
					waitWriteLabel.removeFirst();
					KThread thread = waitQueue.nextThread();
					holders.add(thread);
					thread.ready();
				}
			}
		}
		Machine.interrupt().restore(intStatus);
	}
	/**
	 * 
	 * @return true if currentThread held the write or read Lock
	 */
	public boolean isHeldByCurrentThread() {
		return holders.contains(KThread.currentThread());
	}
	
	private static LinkedList<KThread> holders = new LinkedList<KThread>();
	private static boolean holderWriteLabel = false;
	private static ThreadQueue waitQueue = new RoundRobinScheduler().newThreadQueue(true);
	private static LinkedList<Boolean> waitWriteLabel = new LinkedList<Boolean>();

}
