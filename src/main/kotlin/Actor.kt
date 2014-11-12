package com.hubble.actors

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors
import java.util.ArrayList
import java.util.concurrent.LinkedBlockingQueue
import com.google.common.util.concurrent.ListeningExecutorService
import java.util.concurrent.ThreadFactory
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ScheduledExecutorService
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException
import com.google.common.util.concurrent.ListenableScheduledFuture
import com.google.common.base.Throwables
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.FutureCallback
import java.util.concurrent.Executor
import android.os.Handler
import android.os.Looper


object Aether {
  private val threadFactory: ThreadFactory? = ThreadFactoryBuilder().setDaemon(true)?.build()
  // 10 threads max seems reasonable on android
  private val pool = Executors.newScheduledThreadPool(8, threadFactory as ThreadFactory)
  internal val executor: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(pool)
}

public class UiThreadExecutor() : Executor {
  private val handler: Handler = Handler(Looper.getMainLooper())
  override fun execute(command: Runnable) {
    handler.post(command)
  }
}

public abstract class Actor() {

  private data class Message(val message: Any?, val future: SettableFuture<Any>)

  private val running = AtomicBoolean(false)
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
    val result = promise<Any>()
    mailbox.put(Message(m, result))
    dispatch()
    return result
  }

  /**
   * schedule a message to be sent after a given delay
   */
  fun after(interval: Long, m: Any?): ListenableFuture<Any?> {
    val afterPromise = promise<Any?>()
    Aether.executor.schedule({ () ->
      try {
        afterPromise.set( (this send m).get() )
      } catch (e: Exception) {
        afterPromise.setException(
            ActorExecutionException("Failed to execute scheduled message.", m, e)
        )
      }
    }, interval, TimeUnit.MILLISECONDS)
    return afterPromise
  }

  // STUB
  fun kill() {
  }

  // STUB
  fun stop() {
  }

  // HACK: this is really a hack used to delegate any exception to the main thread
  // This is useful in the case when you want to get an exception that ocurred
  // without calling ListenableFuture.get()
  fun runOnMainThread(r: Runnable) {
    UiThreadExecutor().execute(r)
  }

  fun runOnMainThread(f: () -> Unit) {
    UiThreadExecutor().execute(f)
  }

  // Run our mailbox to completion, and then return and wait for more messages
  private fun dispatch() {
    if (!running.get() && !mailbox.isEmpty()) {
      if (running.compareAndSet(false, true)) {
        val messages = ArrayList<Message>()
        mailbox.drainTo(messages, 8)
        Aether.executor submit { () ->
          for (msg in messages) {
            try {
              msg.future.set(receive(msg.message))
            } catch (t: Throwable) {
              msg.future.setException(
                  ActorExecutionException("Failed to execute message.", msg.message, t)
              )
            }
          }
          running.set(false)
          dispatch() // Must run to completion, otherwise we will wait for the next send
        }
      }
    }
  }

  private fun <T> promise(): SettableFuture<T> {
    val promise = SettableFuture.create<T>()
    // HACK: Wrap all promises in a handler that captures exceptions and proxies them to the main thread.
    Futures.addCallback(promise, object : FutureCallback<T> {
      override fun onSuccess(result: T?) { }
      override fun onFailure(t: Throwable?) {
        runOnMainThread {
          rescue(t!! as ActorExecutionException)
        }
      }
    })
    return promise
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

