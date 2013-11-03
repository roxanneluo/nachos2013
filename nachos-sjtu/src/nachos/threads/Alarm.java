package nachos.threads;

import java.util.TreeSet;

import nachos.machine.*;


/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		lock.acquire();
		if (waitingQueue.isEmpty() || waitingQueue.first().time > Machine.timer().getTime())
			KThread.yield();
		else {
			WaitingThread pair;
			while (!waitingQueue.isEmpty() && waitingQueue.first().time <= Machine.timer().getTime()) {
				pair = waitingQueue.first();
				pair.thread.ready();
				waitingQueue.remove(pair);
				//System.out.println("wake up "+" "+pair.time+" at "+Machine.timer().getTime());
			}
			KThread.yield();
		}
		lock.release();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x
	 *            the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
//		while (wakeTime > Machine.timer().getTime())
//			KThread.yield();
		
		Lib.assertTrue(Machine.interrupt().enabled());
		
		Machine.interrupt().disable();
		waitingQueue.add(new WaitingThread(wakeTime, KThread.currentThread()));
		//System.out.println("to sleep:"+wakeTime);
		KThread.sleep();
		Machine.interrupt().enable();		
	}
	class WaitingThread implements Comparable<WaitingThread> {
		long time;
		KThread thread;
		
		WaitingThread(long t, KThread th) {
			time = t; thread = th;
		}
		
		public int compareTo(WaitingThread wt) {
			if (time < wt.time) return -1;
			if (time > wt.time) return 1;
			return 0;
		}
	}
	TreeSet<WaitingThread> waitingQueue = new TreeSet<WaitingThread>();
	Lock lock = new Lock();
}
