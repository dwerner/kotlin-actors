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


fun <T> promise(): SettableFuture<T> {
  return SettableFuture.create<T>() as SettableFuture<T>
}

object Actors {
  private val threadFactory: ThreadFactory? = ThreadFactoryBuilder().setDaemon(true)?.build()
  private val pool = Executors.newCachedThreadPool(threadFactory as ThreadFactory)
  public val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(pool) as ListeningExecutorService;
}

public abstract class Actor() {

  private data class Message(val message: Any?, val future: SettableFuture<Any>)

  private val running = AtomicBoolean(false)
  private val mailbox:BlockingQueue<Message> = LinkedBlockingQueue<Message>()

  abstract fun receive(m: Any?): Any?

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
        val num = mailbox.drainTo(messages, 10)
        Actors.executor submit {
          for (msg in messages) {
            try {
              msg.future.set(receive(msg.message))
            } catch (t: Throwable) {
              msg.future.setException(t)
            }
          }
          running.set(false)
          dispatch()
        }
      }
    }
  }
}

public fun actor(f: (Any?) -> Any?): Actor {
  return object: Actor() {
    override fun receive(m: Any?): Any? {
      return f(m)
    }
  }
}

