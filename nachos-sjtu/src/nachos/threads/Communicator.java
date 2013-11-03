package nachos.threads;

import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) {
		//System.out.println("in speak");
		lock.acquire();
		//System.out.println("speak acquired lock");
		--listenersOverSpeakers;
		words.add(new Integer(word));
		if (listenersOverSpeakers >= 0)	
			listener.wake();
		else
			speaker.sleep();
		
		lock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		//System.out.println("in listen");
		lock.acquire();
		++listenersOverSpeakers;
		if (listenersOverSpeakers <= 0) 
			speaker.wake();
		else listener.sleep();
		
		int word = words.removeFirst().intValue();
		//System.out.println("in listen: listened word "+word);
		lock.release();
//		System.out.println("in listen: to return word "+word);
		return word;
	}
	
	private Lock lock = new Lock();
	private Condition2 listener = new Condition2(lock);
	private Condition2 speaker = new Condition2(lock);
	private int listenersOverSpeakers = 0;
	private LinkedList<Integer> words = new LinkedList<Integer>();
	
}
