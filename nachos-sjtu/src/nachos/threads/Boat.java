package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
	static BoatGrader bg;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		// begin(1, 2, b);

		// System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		// begin(3, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		/*Runnable r = new Runnable() {
			public void run() {
				SampleItinerary();
			}
		};
		KThread t = new KThread(r);
		t.setName("Sample Boat Thread");
		t.fork();*/
		for (int i = 0; i < adults; ++i) {
			KThread thread = new KThread(new Adult(i));
			thread.setName("adult "+i);
			thread.fork();
		}
		for (int i = 0; i < children; ++i) {
			KThread t = new KThread(new Child(i));
			t.setName("child "+i);
			t.fork();
		}
		doneLock.acquire();
		done.sleep();
		doneLock.release();
	}

	static void AdultItinerary() {
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */
	}

	static void ChildItinerary() {
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out
				.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}
	
	
	static Lock doneLock = new Lock();
	static Condition2 done = new Condition2(doneLock);
	
	
}

class Person {
	protected static int MChild = 0;
	protected static int MAdult = 0;
	protected static int OChild = 0;
	protected static int OAdult = 0;
	protected static int totalChild = 0;
	protected static int totalAdult = 0;
	protected static boolean sendChildren = true;
	protected static Lock lock = new Lock();
	protected static boolean hasFirstController = false;
	protected static Condition2 OAdults = new Condition2(lock);
	protected static Condition2 MChildren = new Condition2(lock);
	protected static Condition2 OChildren = new Condition2(lock);
	protected static Condition2 rideOver = new Condition2(lock);
	
	protected boolean Oahu = true;
	protected int id;
	Person(int index) {
		id = index;
	}
}
class Child extends Person implements Runnable {
	Child(int index) {
		super(index);
	}
	public void run() {
		init();
		while(true) {
			if (Oahu) {	// be waken up to ride
				Boat.bg.ChildRideToMolokai(); //??
				Oahu = false;
				//System.out.println("child "+id+" ride to M");
				lock.acquire();
				++MChild;
				--OChild;
				rideOver.wake();
				MChildren.sleep();
				lock.release();
			} else {// waken up be adult row to M
				Boat.bg.ChildRowToOahu();
				Oahu = true;
				//System.out.println("child "+id + " row to O");
				lock.acquire();
				++OChild;
				--MChild;
				childCheckOahu();
				lock.release();
			}
		}
	}
	void init() {
		lock.acquire();
			++OChild;
			++totalChild;
		lock.release();
		KThread.yield();
		lock.acquire();
			if (hasFirstController) OChildren.sleep();
			else {
				hasFirstController = true;
				lock.release();
				KThread.yield();
				lock.acquire();
				//System.out.println("children:"+totalChild+" adults:"+totalAdult);
				childCheckOahu();
			}
			//System.out.println("wake up from init");
		lock.release();
	}
	void childCheckOahu() {
		//is in a closed lock acquire and realse pair;
		Lib.assertTrue(lock.isHeldByCurrentThread());
		if (sendChildren) {
			if (OChild > 1)
				childSendChild();
			else if (OChild == 1)
					if (OAdult == 0)
						childSendChild();
					else {
						sendChildren = false;
						childCallAdult();
					}
			else {
				Lib.assertTrue(false, "OChildren == 0 error");
			}
		} else {
			if (OChild < totalChild && OAdult > 0)
				childCallAdult();
			else {
				sendChildren = true;
				childSendChild();
			}
		}
	}
	void childSendChild() {
		Lib.assertTrue(lock.isHeldByCurrentThread());
		//System.out.println("child "+id+" row to M");
		Boat.bg.ChildRowToMolokai();
		--OChild;
		if (OChild > 0) {
			OChildren.wake();
			rideOver.sleep();
		}
		if (allArriveButMe()) done();
		Boat.bg.ChildRowToOahu();
		//System.out.println("child "+id+" row to O");
		++OChild;
		childCheckOahu();
	}
	boolean allArriveButMe() {
		Lib.assertTrue(lock.isHeldByCurrentThread());
		return MAdult == totalAdult && MChild == totalChild-1;
	}
	
	void childCallAdult() {
		//when the child row back to Oahu and want to send an adult
		OAdults.wake();
		OChildren.sleep();
	}
	
	void done() {
		Lib.assertTrue(lock.isHeldByCurrentThread());
		Boat.doneLock.acquire();
		Boat.done.wake();
		Boat.doneLock.release();
		MChildren.sleep();
	}
}

class Adult extends Person implements Runnable {
	Adult(int index) {
		super(index);
	}

	void init() {
		lock.acquire();
		++OAdult;
		++totalAdult;
		//System.out.println("Oadults about to sleep");
		OAdults.sleep();
		lock.release();
	}
	
	public void run() {
		init();
		Boat.bg.AdultRowToMolokai();
		Oahu = false;
		//System.out.println("adult "+id+" row to M");
		lock.acquire();
		++MAdult;
		--OAdult;
		MChildren.wake();
		lock.release();
	}
}