package com.hubble.actors

import java.util.ArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ThreadFactory

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.FutureCallback

import android.os.Handler
import android.os.Looper


object Aether {
  private val threadFactory: ThreadFactory? = ThreadFactoryBuilder().setDaemon(true)?.build()
  private val pool = Executors.newScheduledThreadPool(8, threadFactory as ThreadFactory)
  internal val executor: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(pool)
  // TODO: private val mainThreadExecutor = getPlatformMainThreadExecutor()
  internal val mainThreadExecutor = MainThreadExecutor()
}

public class MainThreadExecutor() : Executor {
  private val handler: Handler = Handler(Looper.getMainLooper())
  override fun execute(command: Runnable) {
    handler.post(command)
  }
}

public abstract class Actor() {

  // Internal messages
  private data class Message(val message: Any?, val future: SettableFuture<Any>)
  private data class Death()

  // atomics
  private val running = AtomicBoolean(false)
  private val alive = AtomicBoolean(true)
  private val mailbox: BlockingQueue<Message> = LinkedBlockingQueue<Message>()

  /**
   * receive messages - implement this to define the actor
   */
  abstract fun receive(m: Any?): Any?

  /**
   * rescue from an exception
   */
  open fun rescue(error: ActorExecutionException) {
    throw error
  }

  /**
   * send a message to this actor
   */
  fun send(m: Any?): ListenableFuture<Any> {
    if (!this.alive.get()) {
      throw ActorExecutionException("Cannot send messages to a dead actor.", m, Exception())
    }
    val result = promise<Any>()
    mailbox.put(Message(m, result))
    dispatch()
    return result
  }

  /**
   * schedule a message to be sent to the actor's mailbox after a given delay
   * Since this relies on the messagebox still, will not execute if the actor has been killed.
   */
  fun after(interval: Long, m: Any?): ListenableFuture<Any?> {
    val afterPromise = promise<Any?>()
    Aether.executor.schedule({() ->
      try {
        if (alive.get()) {
          afterPromise.set((this send m).get())
        }
      }
      catch (e: Exception) {
        afterPromise.setException(
            ActorExecutionException("Failed to execute scheduled message.", m, e)
        )
      }
    }, interval, TimeUnit.MILLISECONDS)
    return afterPromise
  }

  /***
   * kill this actor; immediately stop processing new messages.
   * Any currently executing promises will finish normally.
   */
  open fun kill() {
    this.alive.set( false ) // immediately stop processing messages
    this.mailbox.clear()
  }

  // HACK: used to delegate any exception to the main thread
  // This is useful in the case when you want to get an exception that ocurred
  // without calling ListenableFuture.get()
  fun runOnMainThread( r: Runnable ) {
    Aether.mainThreadExecutor.execute( r )
  }

  // Kotlin helper - take a function literal and do the same as the Runnable case
  fun runOnMainThread( f: () -> Unit ) {
    Aether.mainThreadExecutor.execute( f )
  }

  /**
   * Send a promise to the main thread, and resolve it
   */
  fun getFromMainThread( f: () -> Any ): Any {
    val promise = promise<Any>()
    Aether.mainThreadExecutor.execute {
      promise.set(f())
    }
    return promise.get()
  }

  // Run our mailbox to completion, and then return and wait for more messages
  private fun dispatch() {
    if ( !running.get() && !mailbox.isEmpty() && alive.get() ) {
      if ( running.compareAndSet(false, true) ) {
        val messages = ArrayList<Message>()
        mailbox.drainTo(messages, 8)
        Aether.executor submit {() ->
          for (msg in messages) {
            try {
              if ( alive.get() ) {
                msg.future.set(receive(msg.message))
              }
            }
            catch (t: Throwable) {
              msg.future.setException(
                  ActorExecutionException("Failed to execute message.", msg.message, t)
              )
            }
          }
          running.set(false)
          dispatch()
        }
      }
    }
  }
  /***
   * creates a Listenable/Settable future representing an eventual value and adds
   * a handler that captures exceptions and proxies them to the main thread.
   * (This is needed, otherwise we eat Throwables.)
   */
  public fun <T> promise(): SettableFuture<T> {
    val promise = SettableFuture.create<T>()
    Futures.addCallback(promise, object : FutureCallback<T> {
      override fun onFailure(t: Throwable?) {

        runOnMainThread {
          rescue( t!! as ActorExecutionException )
        }

      }
      override fun onSuccess(result: T) {}
    })
    return promise
  }

}


public abstract class LinkedActor(val id: String, val link: Actor) : Actor() {

  public data class LinkedDeath(val id: String)

  fun sendLink(m: Any?): Any? {
    return link send m
  }

  override fun kill() {
    super<Actor>.kill()
    link send LinkedDeath(id) // Actor{id} was killed
  }

}

/***
 * Actor extension - Publish/Subscribe capabilities
 */
public abstract class PublishSubscribeActor() : Actor() {
  private val clients = ArrayList<Actor>()
  fun publish(m: Any?) {
    for (a in clients) {
      a send m
    }
  }

  fun subscribe(a: Actor) {
    if (!clients.contains(a) ) {
      clients.add(a)
    }
  }

  fun unsubscribe(a: Actor) {
    if ( clients.contains(a) ) {
      clients.remove(a)
    }
  }
}

public class ActorExecutionException(
    msg: String,
    val message: Any?,
    throwable: Throwable
) : Exception(msg, throwable)

public fun actor(f: (Any?) -> Any?): Actor {
  return object : Actor() {
    override fun receive(m: Any?): Any? {
      return f(m)
    }
  }
}

