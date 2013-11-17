package nachos.threads;


import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.PriorityScheduler.PriorityQueue;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}
	

	/**
	 * Return the scheduling Lottery state of the specified thread.
	 * 
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected LotteryState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new LotteryState(thread);

		return (LotteryState) thread.schedulingState;
	}
	
	public static int priorityMinimum = 1;
	public static int priorityMaximum = Integer.MAX_VALUE;
	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}
	
	public class LotteryQueue extends PriorityQueue {
		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}
		
		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (timeTable.containsKey(thread)) return;
//			System.out.println(thread+" wait for "+this);
//			
			int ep = getThreadState(thread).getEffectivePriority();
			if (transferPriority) 
				getThreadState(owner).delta = (owner == thread)? -sum:ep;
			int temp = owner == thread? ep: sum+ep;
			
			super.waitForAccess(thread);
			
//			print();
			sum = temp;	
		}
		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (transferPriority && owner != null) 
				getThreadState(owner).delta = -sum;
			LotteryState threadState = getThreadState(thread); 
			if (thread == owner) {
				sum -= threadState.getEffectivePriority();
			} 
			if (transferPriority)
				threadState.delta = sum;
			
			super.acquire(thread);
//			ThreadState state = getThreadState(thread);
//			System.out.println("sum: "+sum+" after "+thread+"("+(state==null? "null":state.getEffectivePriority())+")acquire"+"("+transferPriority+")"+this);
		}
		
		public LotteryState pickNextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (waitQueue.isEmpty()) return null;
			int n = Lib.random(sum);
			Iterator<KThread> iter = waitQueue.iterator();
			int s = 0;
			LotteryState cur = null;
			while (iter.hasNext()) {
				cur = getThreadState(iter.next());
				s += cur.getEffectivePriority();
				if (n < s) return cur;
			}
			Lib.assertTrue(false, "in pick next Thread: should not reach here");
			return null;
		}
		
		public KThread nextThread() {
			LotteryState nextState = pickNextThread();
			KThread next = nextState == null? null: nextState.thread;
//			System.out.println("sum: "+sum+" before "+next+"is the next thread of"+this);
			if (transferPriority && owner != null) 
				getThreadState(owner).delta = -sum;
			if (nextState != null) {
				sum -= nextState.getEffectivePriority();
				if (transferPriority)
					nextState.delta = sum;
			}
			next = updateUsingNext(next);
			return next;
		}
		
		
		private int sum = 0;
	}
	
	public class LotteryState extends ThreadState {
		public LotteryState(KThread thread) {
			super(thread);
		}
		
		@Override
		protected void updatePriority() {
			if (delta == 0) return;
			for (PriorityQueue queue:waiting) {
				queue.waitQueue.remove(thread);
			}
			effectivePriority += delta;
			for (PriorityQueue queue:waiting)
				queue.waitQueue.add(thread);
			/**
			 * ERROR FIXED: although I don't care about the order of ep in waitQueue and I use timeTable
			 * to decide contains;
			 * but when I want to use the treeSet to add and remove, the original one was in a wrong place,
			 * so I can't remove them correctly
			 */
			LotteryQueue queue = null;
			for (PriorityQueue q:waiting) {
				queue = (LotteryQueue)q;
				queue.sum += delta;
				if (!queue.transferPriority || queue.owner == null) continue;
					LotteryState ownerState = getThreadState(queue.owner);
					ownerState.delta = delta;
					ownerState.updatePriority();
			}
			delta = 0;
		}
		
		public int delta = 0;
		/**
		 * ERROR FIXED: whatever the type, new filed will overwrite the field of the same name in the parent class
		 * so I cannot redefine the owning and waiting queue with lotteryQueue
		 */
		
	}
	
}
