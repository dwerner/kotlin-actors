/**
 * Created by dan on 01/10/14.
 */
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.hubble.actors.Actor;

import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.Assert;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import jet.runtime.typeinfo.JetValueParameter;

import static kotlin.io.IoPackage.println;

@Config(emulateSdk = 18)
public class JavaTest {

  private class Message {
    Actor a;
    public Message(final Actor a){
      this.a = a;
    }
    public Actor getA(){
      return a;
    }
  }

  private class PingMessage extends Message {
    public PingMessage(Actor a) {
      super(a);
    }
  }

  private class PongMessage extends Message {
    public PongMessage(Actor a) {
      super(a);
    }
  }

  private class Pong extends Actor {
    @Override
    public Object receive(Object m) {
      Assert.assertTrue(m instanceof PingMessage);
      if (m instanceof PingMessage) {
        //println("Ping from ThreadID" + Thread.currentThread().getId());
        ((PingMessage)m).getA().send(new PongMessage(this));
      } else {
        Assert.fail("Didn't get the right message");
      }
      return null;
    }
  }

  private class Ping extends Actor {
    @Override
    public Object receive(Object m) {
      Assert.assertTrue(m instanceof PongMessage);
      if (m instanceof PongMessage) {
        //println("Pong from ThreadID" + Thread.currentThread().getId());
        ((PongMessage) m).getA().send(new PingMessage(this));
      } else {
        Assert.fail("Didn't get the right message");
      }
      return null;
    }
  }

  //@Test
  public void ActorPingPongTest(){
    println("Starting Ping/Ping test from ThreadID" + Thread.currentThread().getId());
    Ping ping = new Ping();
    Pong pong = new Pong();
    ping.send(new PongMessage(pong));
    try {
      Thread.sleep(10);
    } catch (Exception e) {
      Assert.fail("exception during ping-pong");
    }
  }

  private class TimeoutActor extends Actor{
    private long time;
    public TimeoutActor(long time) {
      this.time = time;
    }
    @Override
    public Object receive(@Nullable Object m) {
      long observedInterval = new Date().getTime() - this.time;
      println("<---- TimeoutActor: Tested/observed interval: "+observedInterval + " - " + m);
      Assert.assertTrue("This actor executed the message AFTER 100 ms", observedInterval >= 100 );
      this.time = new Date().getTime();
      ((PingMessage)m).getA().after(100, new Message(this));
      return null;
    }
  }

  @Test(expected=AssertionError.class)
  public void ShouldCaptureExceptionsFromAfter_Test(){
    TimeoutActor p1 = new TimeoutActor(new Date().getTime());
    TimeoutActor p2 = new TimeoutActor(new Date().getTime());
    ListenableFuture<?> future = p1.after(0, new Message(p2)); // in TimeoutActor we assert that the interval must be longer than 100 ms...
    try {
      future.get();
    } catch (Exception e) {
      Assert.fail("exception during realization of future");
      e.printStackTrace();
    }
  }

  @Test
  public void ShouldSupportAfterTest(){
    TimeoutActor p1 = new TimeoutActor(new Date().getTime());
    TimeoutActor p2 = new TimeoutActor(new Date().getTime());
    ListenableFuture<?> future = p1.after(100, new Message(p2));
    try {
      future.get();
    } catch (Exception e) {
      Assert.fail("exception during realization of future");
      e.printStackTrace();
    }
  }
}
