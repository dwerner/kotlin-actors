Android Actors
==============


The goal of this library is to provide a simple DSL-like API for concurrency, implementing actors similar to Erlang or Akka. While this library could easily be ported to other JVM environments, my primary interest is in using it with Android, along with Kotlin and the related plugins.

# Introduction

I didn't want to decide on a larger system to control actors, beyond simply having them available in the JVM for building modular code. Originally based on a github gist by mgodave, I wanted to contribute my work back to the community in a similar vein.

# Simple examples

# Bird
 A normal object might look like

```kotlin
public class Bird() {
  fun flapWings(rate:Int): String {
    return "now flapping wings at a rate of ${rate} flaps per minute!"
  }
}
```

This is a Kotlin version of what commonly appears in OOP languages. Perfectly understandable, and the interface is purely it's list of public methods. In this case a constructor, and a method called flapWings which takes an argument to describe the rate at which to flap.

While useful for encapsulation, this object does not really represent a real bird very well. It would be nice if the entire world didn't come to a stop when the bird flaps it's wings, for instance.

```kotlin
// Given a message
data class FlapWings(val rate:Int)

// ... and an Actor
public class Bird() : Actor() {
  override fun receive( m: Any? ): Any? {
    return when (m) { // <-- Return the eventual value of a given computation
      is FlapWings -> "now flapping wings at a rate of ${m.rate} flaps per second!"
      else -> {}
    }
  }
}

...
val bird = Bird()
bird send FlapWings(rate = 5) // Fire and forget, but we don't capture the computation

// Or

val promise = (bird send FlapWings(rate = 10)) // Capture the ListenableFuture<Any> 
promise.get() == "now flapping wings at a rate of 10 flaps per second!" //... and resolve it

```

In this contrived example, there is an actor class (Bird), as well as the message FlapWings. Messages become the interface to actors, rather than public methods. Similar to dynamic binding of method calls, we must do some checking to ensure that we know how deal with messages that the actor is sent. However, we do have the type safety of the message itself, as Kotlin's `is Type` operation automatically casts the message to what we want if it matches.

This interface, while a bit strange at first, allows actors to work in a very composable way; Actors need not know each other's types at runtime, but simply the messages which they would like to send and receive from one another.

# Anatomy of an actor

Actors have several distinctions from those of normal objects.

- They have a mailbox which is used to queue messages in the order they are received.
- Unlike normal objects, messages are executed on a pool of threads asynchronously.
- They execute all messages in the order they are received, unless the actor is killed.
- Once killed, they cannot be used again.
- Sending an actor a message results immediately in a promise or future, which can be resolved synchronously, or not at all. The value of the resulting promise is determined by the return value of receive.

# Guava futures

Google's Guava library is utilized to provide the promise implementation in SettableFuture and ListenableFuture. When a message is sent to an actor, as in the above example, it returns a promise. That promise is one of the ways to interact with an actor. With the promise, the sender of the message can choose to block it's own thread of execution by calling `.get()` on it.

# PublishSubscribeActor

To simplify the creation of actor systems with pub/sub style implementation (typically sending messages to each other or a list of clients, rather than resolving promises in sequential code), the PublishSubscribeActor class provides a base on which to build actors that keep track of other actors via their `register`, `unregister` and `publish` methods.

# Implementation Patterns

As discussed, actors can make use of promises, but there are several other methods for actors to interact with each other and the general ecosystem around them. I'm laying them out here as a general guide of the types of systems I have built, but this lacks higher-level concepts like supervision trees from more sophistocated actor based systems like Erlang.

- Promise resolution -> Send a message to an actor, cature the promise, and at some point block your own execution until it can be resolved. Note that promises can be chained.
- Message responses -> Something useful that actors can do is... send more messages to other actors. When you are expecting a value from a long-running computation, and you don't find it convenient to capture the promise behind the sending of the message, you can set up a capturing actor to receive responses from the actor responsible for computation.
- Java-style callbacks -> Make your actor an abstract base class, with methods defining the callbacks, and where you want to get called back, implement them. I find this to be the most convenient when interoperating with other parts of the Android framework, as much of the API is implemented in this manner anyhow.
- Java Runnable/Kotlin function literal callbacks -> Pass Runnables or function literals in your messages, as callbacks.

# Errors and exceptions

Exceptions that occur when executing messages will be wrapped in an ActorExecutionException and proxied to the main thread. This will cause your Android app to crash if you do not override `rescue`.
