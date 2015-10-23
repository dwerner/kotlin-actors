import com.google.common.util.concurrent.ListenableFuture;
import com.hubble.actors.Actor;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Date;


/**
 * Created by dan on 11/11/14.
 */
public class RingTest {

	static RingActor ring50head;
	static RingActor ring500head;
	static RingActor ring5000head;

	@BeforeClass
	public static void beforeTest() {
		ring50head = createRing(50);
		ring500head = createRing(500);
		ring5000head = createRing(5000);
	}


	// 50 workers N passes

	@Test
	public void workers50passes500() throws Exception {
		doRingTest(ring50head, 500);
	}

	@Test
	public void workers50passes5000() throws Exception {
		doRingTest(ring50head, 5000);
	}

	@Test
	public void workers50passes50000() throws Exception {
		doRingTest(ring50head, 50000);
	}

	@Test
	public void workers50passes500000() throws Exception {
		doRingTest(ring50head, 500000);
	}

	@Test
	public void workers50passes5000000() throws Exception {
		doRingTest(ring50head, 5000000);
	}

	@Test
	public void workers50passes50000000() throws Exception {
		doRingTest(ring50head, 5000000);
	}


	// 500 workers, N passes
	@Test
	public void workers500passes500() throws Exception {
		doRingTest(ring500head, 500);
	}

	@Test
	public void workers500passes5000() throws Exception {
		doRingTest(ring500head, 5000);
	}

	@Test
	public void workers500passes50000() throws Exception {
		doRingTest(ring500head, 50000);
	}

	@Test
	public void workers500passes500000() throws Exception {
		doRingTest(ring500head, 500000);
	}

	@Test
	public void workers500passes5000000() throws Exception {
		doRingTest(ring500head, 5000000);
	}

	@Test
	public void workers500passes50000000() throws Exception {
		doRingTest(ring500head, 5000000);
	}

	// 5000 workers, N passes

	@Test
	public void workers5000passes500() throws Exception {
		doRingTest(ring5000head, 500);
	}

	@Test
	public void workers5000passes5000() throws Exception {
		doRingTest(ring5000head, 5000);
	}

	@Test
	public void workers5000passes50000() throws Exception {
		doRingTest(ring5000head, 50000);
	}

	@Test
	public void workers5000passes500000() throws Exception {
		doRingTest(ring500head, 500000);
	}

	@Test
	public void workers5000passes5000000() throws Exception {
		doRingTest(ring500head, 5000000);
	}

	@Test
	public void workers5000passes50000000() throws Exception {
		doRingTest(ring500head, 5000000);
	}

	static void doRingTest(RingActor head, int passes) throws Exception {
		long startTime = new Date().getTime();
		RingToken token = new RingToken();
		token.maxPasses = passes;
		ListenableFuture<?> promise = head.send(token);

		int promiseCount = 0;
		while (promise != null) {
			promiseCount += 1;
			promise = (ListenableFuture<?>) promise.get();
		}
		long endTime = new Date().getTime();
		//println("Worker count: " + token.maxPasses + " total time = " + (endTime - startTime) + " with " + promiseCount + " promises resolved.");
	}

	static class RingToken {
		public int passCount = 0;
		public int maxPasses = 0;
	}

	static class RingActor extends Actor {
		private RingActor link;

		void setLink(RingActor link) {
			this.link = link;
		}

		@Override
		public Object receive(Object m) {
			if (m instanceof RingToken) {
				RingToken token = (RingToken) m;
				if (token.passCount <= token.maxPasses) {
					token.passCount += 1;
					return this.link.send(token);
				}
			}
			return null;
		}
	}

	static RingActor createRing(int workers) {
		RingActor first = new RingActor();
		RingActor last = first;
		for (int i = 0; i < workers; i++) {
			RingActor tail = new RingActor();
			tail.setLink(last);
			last = tail;
		}
		first.setLink(last);
		return first;
	}
}
