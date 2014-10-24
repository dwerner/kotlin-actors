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


object Actors {
  private val threadFactory: ThreadFactory? = ThreadFactoryBuilder().setDaemon(true)?.build()
  // 10 threads max seems reasonable on android
  private val pool = Executors.newScheduledThreadPool(10, threadFactory as ThreadFactory)
  public val executor: ListeningScheduledExecutorService = MoreExecutors.listeningDecorator(pool)
}

public class UiThreadExecutor() : Executor {
  private val handler:Handler = Handler(Looper.getMainLooper())
  override fun execute(command: Runnable) {
    handler.post(command)
  }
}

public abstract class Actor() {

  private data class Message(val message: Any?, val future: SettableFuture<Any>)

  private val running = AtomicBoolean(false)
  private val mailbox:BlockingQueue<Message> = LinkedBlockingQueue<Message>()

  abstract fun receive(m: Any?): Any?

  fun runOnMainThread(r:Runnable) {
    UiThreadExecutor().execute {
      r.run()
    }
  }

  fun runOnMainThread(f:()->Unit) {
    UiThreadExecutor().execute {
      f()
    }
  }

  private fun <T> promise(): SettableFuture<T> {
    val promise = SettableFuture.create<T>()
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

  open fun rescue(error: ActorExecutionException) {
    throw error
  }

  fun after(interval:Long, m:Any?): ListenableFuture<Any?> {
    val afterPromise = promise<Any?>()
    println("Scheduling task for ${interval} millis from now")
    Actors.executor.schedule({ ()->
      println("Sending after timeout.")
      try {
        afterPromise.set((this send m).get())
      } catch (e:Exception) {
        afterPromise.setException(e)
      }
    }, interval, TimeUnit.MILLISECONDS)
    return afterPromise
  }

  fun send(m: Any?): ListenableFuture<Any> {
    val result = promise<Any>()
    mailbox.put(Message(m, result))
    dispatch()
    return result
  }

  private fun dispatch() {
    if (!running.get() && !mailbox.isEmpty()) {
      if (running.compareAndSet(false, true)) {
        val messages = ArrayList<Message>()
        //val num =
        mailbox.drainTo(messages, 5)
        Actors.executor submit { () ->
          for (msg in messages) {
            try {
              msg.future.set(receive(msg.message))
            } catch (t: Throwable) {
              msg.future.setException(ActorExecutionException("Failed to execute message.", msg, t))
            }
          }
          running.set(false)
          dispatch() // Must run to completion, otherwise we will wait for the next send
        }
      }
    }
  }
}

public class ActorExecutionException(msg: String, val message: Any?, throwable: Throwable) : Exception(msg, throwable)

public fun actor(f: (Any?) -> Any?): Actor {
  return object: Actor() {
    override fun receive(m: Any?): Any? {
      return f(m)
    }
  }
}

