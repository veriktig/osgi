/*
 * Copyright (c) OSGi Alliance (2014). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.osgi.util.promise;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Promise implementation.
 * 
 * <p>
 * This class is not used directly by clients. Clients should use
 * {@link Deferred} to create a resolvable {@link Promise}.
 * 
 * @param <T> The result type associated with the Promise.
 * 
 * @ThreadSafe
 * @author $Id$
 */
final class PromiseImpl<T> implements Promise<T> {
	/**
	 * A ConcurrentLinkedQueue to hold the callbacks for this Promise, so no
	 * additional synchronization is required to write to or read from the
	 * queue.
	 */
	private final ConcurrentLinkedQueue<Runnable>	callbacks;
	/**
	 * A CountDownLatch to manage the resolved state of this Promise.
	 * 
	 * <p>
	 * This object is used as the synchronizing object to provide a critical
	 * section in {@link #resolve(Object, Throwable)} so that only a single
	 * thread can write the resolved state variables and open the latch.
	 * 
	 * <p>
	 * The resolved state variables, {@link #value} and {@link #error}, must
	 * only be written when the latch is closed (getCount() != 0) and must only
	 * be read when the latch is open (getCount() == 0). The latch state must
	 * always be checked before writing or reading since the resolved state
	 * variables' memory consistency is guarded by the latch.
	 */
	private final CountDownLatch					resolved;
	/**
	 * The value of this Promise if successfully resolved.
	 * 
	 * @GuardedBy("resolved")
	 * @see #resolved
	 */
	private T										value;
	/**
	 * The failure of this Promise if resolved with a failure or {@code null} if
	 * successfully resolved.
	 * 
	 * @GuardedBy("resolved")
	 * @see #resolved
	 */
	private Throwable								error;

	/**
	 * Initialize this Promise.
	 */
	PromiseImpl() {
		callbacks = new ConcurrentLinkedQueue<Runnable>();
		resolved = new CountDownLatch(1);
	}

	/**
	 * Initialize and resolve this Promise with the specified value.
	 * 
	 * @param v The value of this resolved Promise.
	 */
	PromiseImpl(T v) {
		value = v;
		error = null;
		callbacks = new ConcurrentLinkedQueue<Runnable>();
		resolved = new CountDownLatch(0);
	}

	/**
	 * Resolve this Promise.
	 * 
	 * @param v The value of this Promise.
	 * @param t The failure of this Promise.
	 */
	void resolve(T v, Throwable t) {
		// critical section: only one resolver at a time
		synchronized (resolved) {
			if (resolved.getCount() == 0) {
				throw new IllegalStateException("Already resolved");
			}
			/*
			 * The resolved state variables must be set before opening the
			 * latch. This safely publishes them to be read by other threads
			 * that must verify the latch is open before reading.
			 */
			value = v;
			error = t;
			resolved.countDown();
		}
		notifyCallbacks(); // call any registered callbacks
	}

	/**
	 * Call any registered callbacks if this Promise is resolved.
	 */
	private void notifyCallbacks() {
		if (resolved.getCount() != 0) {
			return; // return if not resolved
		}

		/*
		 * Note: multiple threads can be in this method removing callbacks from
		 * the queue and calling them, so the order in which callbacks are
		 * called cannot be specified.
		 */
		for (Runnable callback = callbacks.poll(); callback != null; callback = callbacks.poll()) {
			try {
				callback.run();
			} catch (Exception e) { // TODO catch Throwable?
				e.printStackTrace(); // TODO what should we really do here?
			}
		}
	}

	/**
	 * Returns whether this Promise has been resolved.
	 * 
	 * <p>
	 * This Promise may be successfully resolved or resolved with a failure.
	 * 
	 * @return {@code true} if this Promise was resolved either successfully or
	 *         with a failure; {@code false} if this Promise is unresolved.
	 */
	public boolean isDone() {
		return resolved.getCount() == 0;
	}

	/**
	 * Returns the value of this Promise.
	 * 
	 * <p>
	 * If this Promise has been {@link Promise#isDone() resolved}, this method
	 * immediately returns with the value of this Promise if this Promise was
	 * successfully resolved. If this Promise was resolved with a failure, this
	 * method will throw an {@code InvocationTargetException} with the
	 * {@link Promise#getError() failure exception} as the cause. If this
	 * Promise is unresolved, this method will block and wait for this Promise
	 * to be resolved before completing.
	 * 
	 * @return The value of this resolved Promise.
	 * @throws InvocationTargetException If this Promise was resolved with a
	 *         failure. The cause of the {@code InvocationTargetException} is
	 *         the failure exception.
	 * @throws InterruptedException If the current thread was interrupted while
	 *         waiting.
	 */
	public T getValue() throws InvocationTargetException, InterruptedException {
		resolved.await();
		if (error != null) {
			throw new InvocationTargetException(error);
		}
		return value;
	}

	/**
	 * Returns the failure of this Promise.
	 * 
	 * <p>
	 * If this Promise has been {@link Promise#isDone() resolved}, this method
	 * immediately returns with the failure of this Promise if this Promise was
	 * resolved with a failure. If this Promise was successfully resolved, this
	 * method will return {@code null}. If this Promise is unresolved, this
	 * method will block and wait for this Promise to be resolved before
	 * completing.
	 * 
	 * @return The failure of this resolved Promise or {@code null} if this
	 *         Promise was successfully resolved.
	 * @throws InterruptedException If the current thread was interrupted while
	 *         waiting.
	 */
	public Throwable getError() throws InterruptedException {
		resolved.await();
		return error;
	}

	/**
	 * Register a callback to be called when this Promise is resolved.
	 * 
	 * <p>
	 * The specified callback is called when the Promise is resolved either
	 * successfully or with a failure.
	 * 
	 * <p>
	 * This method may be called at any time including before and after this
	 * Promise has been resolved.
	 * 
	 * <p>
	 * Resolving this Promise <i>happens-before</i> any registered callback is
	 * called. That is, in a registered callback, {@link Promise#isDone()} must
	 * return {@code true} and {@link Promise#getValue()} and
	 * {@link Promise#getError()} must not block.
	 * 
	 * <p>
	 * A callback may be called on a different thread than the thread which
	 * registered the callback. So the callback must be thread safe but can rely
	 * upon that the registration of the callback <i>happens-before</i> the
	 * registered callback is called.
	 * 
	 * @param callback A callback to be called when this Promise is resolved.
	 * @throws NullPointerException If the specified callback is {@code null}.
	 */
	public void onResolve(Runnable callback) {
		callbacks.offer(callback);
		notifyCallbacks(); // call any registered callbacks
	}

	/**
	 * Chain a new Promise to this Promise with Success and Failure callbacks.
	 * 
	 * <p>
	 * The specified {@link Success} callback is called when this Promise is
	 * successfully resolved and the specified {@link Failure} callback is
	 * called when this Promise is resolved with a failure.
	 * 
	 * <p>
	 * This method returns a new Promise which is chained to this Promise. The
	 * returned Promise will be resolved when this Promise is resolved after the
	 * specified Success or Failure callback is executed. The result of the
	 * executed callback will be used to resolve the returned Promise. Multiple
	 * calls to this method can be used to create a chain of promises which are
	 * resolved in sequence.
	 * 
	 * <p>
	 * If this Promise is successfully resolved, the Success callback is
	 * executed and the result Promise, if any, or thrown exception is used to
	 * resolve the returned Promise from this method. If this Promise is
	 * resolved with a failure, the Failure callback is executed and the
	 * returned Promise from this method is failed.
	 * 
	 * <p>
	 * This method may be called at any time including before and after this
	 * Promise has been resolved.
	 * 
	 * <p>
	 * Resolving this Promise <i>happens-before</i> any registered callback is
	 * called. That is, in a registered callback, {@link Promise#isDone()} must
	 * return {@code true} and {@link Promise#getValue()} and
	 * {@link Promise#getError()} must not block.
	 * 
	 * <p>
	 * A callback may be called on a different thread than the thread which
	 * registered the callback. So the callback must be thread safe but can rely
	 * upon that the registration of the callback <i>happens-before</i> the
	 * registered callback is called.
	 * 
	 * @param success A Success callback to be called when this Promise is
	 *        successfully resolved. May be {@code null} if no Success callback
	 *        is required. In this case, the returned Promise will be resolved
	 *        with the value {@code null} when this Promise is successfully
	 *        resolved.
	 * @param failure A Failure callback to be called when this Promise is
	 *        resolved with a failure. May be {@code null} if no Failure
	 *        callback is required.
	 * @return A new Promise which is chained to this Promise. The returned
	 *         Promise will be resolved when this Promise is resolved after the
	 *         specified Success or Failure callback, if any, is executed.
	 */
	public <R> Promise<R> then(Success<R, ? super T> success, Failure<? super T> failure) {
		PromiseImpl<R> chained = new PromiseImpl<R>();
		@SuppressWarnings("unchecked")
		Runnable then = new Then<R>(chained, (Success<R, T>) success, (Failure<T>) failure);
		onResolve(then);
		return chained;
	}

	/**
	 * Chain a new Promise to this Promise with a Success callback.
	 * 
	 * <p>
	 * This method performs the same function as calling
	 * {@link Promise#then(Success, Failure)} with the specified Success
	 * callback and {@code null} for the Failure callback.
	 * 
	 * @param success A Success callback to be called when this Promise is
	 *        successfully resolved. May be {@code null} if no Success callback
	 *        is required. In this case, the returned Promise will be resolved
	 *        with the value {@code null} when this Promise is successfully
	 *        resolved.
	 * @return A new Promise which is chained to this Promise. The returned
	 *         Promise will be resolved when this Promise is resolved after the
	 *         specified Success, if any, is executed.
	 * @see Promise#then(Success, Failure)
	 */
	public <R> Promise<R> then(Success<R, ? super T> success) {
		return then(success, null);
	}

	/**
	 * A callback used to chain promises for the {@link #then(Success, Failure)}
	 * method.
	 * 
	 * @Immutable
	 */
	private final class Then<R> implements Runnable {
		private final PromiseImpl<R>	chained;
		private final Success<R, T>		success;
		private final Failure<T>		failure;

		Then(PromiseImpl<R> chained, Success<R, T> success, Failure<T> failure) {
			this.chained = chained;
			this.success = success;
			this.failure = failure;
		}

		public void run() {
			final boolean interrupted = Thread.interrupted();
			try {
				Throwable t = null;
				try {
					t = getError();
				} catch (InterruptedException e) {
					/*
					 * This can't happen since (1) we are a callback on a
					 * resolved Promise and (2) we cleared the interrupt status
					 * above.
					 */
					throw new RuntimeException(e);
				}
				if (t != null) {
					if (failure != null) {
						try {
							failure.fail(PromiseImpl.this);
						} catch (Throwable e) {
							// propagate new exception
							t = e;
						}
					}
					// fail chained
					chained.resolve(null, t);
					return;
				}
				Promise<R> returned = null;
				if (success != null) {
					try {
						returned = success.call(PromiseImpl.this);
					} catch (Throwable e) {
						chained.resolve(null, e);
						return;
					}
				}
				if (returned == null) {
					// resolve chained with null value
					chained.resolve(null, null);
				} else {
					// resolve chained after returned promise is resolved
					returned.onResolve(new Chain<R>(chained, returned));
				}
			} finally {
				if (interrupted) { // restore interrupt status
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/**
	 * A callback used to resolve the chained Promise when the Promise returned
	 * by the Success callback is resolved.
	 * 
	 * @Immutable
	 */
	private final static class Chain<R> implements Runnable {
		private final PromiseImpl<R>	chained;
		private final Promise<R>		returned;

		Chain(PromiseImpl<R> chained, Promise<R> returned) {
			this.chained = chained;
			this.returned = returned;
		}

		public void run() {
			final boolean interrupted = Thread.interrupted();
			try {
				Throwable t = null;
				try {
					t = returned.getError();
				} catch (InterruptedException e) {
					/*
					 * This can't happen since (1) we are a callback on a
					 * resolved Promise and (2) we cleared the interrupt status
					 * above.
					 */
					throw new RuntimeException(e);
				}
				if (t != null) {
					chained.resolve(null, t);
					return;
				}
				R value;
				try {
					value = returned.getValue();
				} catch (InvocationTargetException e) {
					// This can't happen since we checked error above
					throw new RuntimeException(e);
				} catch (InterruptedException e) {
					/*
					 * This can't happen since (1) we are a callback on a
					 * resolved Promise and (2) we cleared the interrupt status
					 * above.
					 */
					throw new RuntimeException(e);
				}
				chained.resolve(value, null);
			} finally {
				if (interrupted) { // restore interrupt status
					Thread.currentThread().interrupt();
				}
			}
		}
	}
}