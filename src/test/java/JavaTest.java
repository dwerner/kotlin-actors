/**
 * Created by dan on 01/10/14.
 */
import com.hubble.actors.Actor;

import org.junit.Test;
import org.junit.Assert;
import org.robolectric.annotation.Config;
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
        println("Ping from ThreadID" + Thread.currentThread().getId());
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
        println("Pong from ThreadID" + Thread.currentThread().getId());
        ((PongMessage) m).getA().send(new PingMessage(this));
      } else {
        Assert.fail("Didn't get the right message");
      }
      return null;
    }
  }

  @Test
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
}
