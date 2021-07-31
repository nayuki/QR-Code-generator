/* 
 * Fast QR Code generator library
 * 
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/fast-qr-code-generator-library
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package io.nayuki.fastqrcodegen;

import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


// A thread-safe cache based on soft references.
final class Memoizer<T,R> {
	
	private final Function<T,R> function;
	Map<T,SoftReference<R>> cache = new ConcurrentHashMap<>();
	private Set<T> pending = new HashSet<>();
	
	
	// Creates a memoizer based on the given function that takes one input to compute an output.
	public Memoizer(Function<T,R> func) {
		function = func;
	}
	
	
	// Computes function.apply(arg) or returns a cached copy of a previous call.
	public R get(T arg) {
		// Non-blocking fast path
		{
			SoftReference<R> ref = cache.get(arg);
			if (ref != null) {
				R result = ref.get();
				if (result != null)
					return result;
			}
		}
		
		// Sequential slow path
		while (true) {
			synchronized(this) {
				SoftReference<R> ref = cache.get(arg);
				if (ref != null) {
					R result = ref.get();
					if (result != null)
						return result;
					cache.remove(arg);
				}
				assert !cache.containsKey(arg);
				
				if (pending.add(arg))
					break;
				
				try {
					this.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		try {
			R result = function.apply(arg);
			cache.put(arg, new SoftReference<>(result));
			return result;
		} finally {
			synchronized(this) {
				pending.remove(arg);
				this.notifyAll();
			}
		}
	}
	
}
