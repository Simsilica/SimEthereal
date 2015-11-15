/*
 * $Id$
 * 
 * Copyright (c) 2015, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.simsilica.ethereal;

//import com.simsilica.lemur.core.VersionedObject;
//import com.simsilica.lemur.core.VersionedReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 *
 *
 *  @author    Paul Speed
 */
public class Statistics {

    private static final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();    
    private static final ConcurrentHashMap<String, Tracker> trackers = new ConcurrentHashMap<>();    
    private static final ConcurrentHashMap<String, Sequence> sequences = new ConcurrentHashMap<>();    

    public static Counter getCounter( String name, boolean create ) {
        Counter result = counters.get(name);
        if( result == null && create ) {
            synchronized(counters) {
                result = counters.get(name);
                if( result == null ) {
                    result = new Counter(name);
                    counters.put(name, result);
                }
            }
        }
        return result;
    }
 
    public static long getCounterValue( String name ) {
        Counter result = counters.get(name);
        return result == null ? -1 : result.get();
    } 

/*    public static VersionedReference<Long> createCounterRef( String name, boolean create ) {
        Counter counter = getCounter(name, create);
        if( counter == null ) {
            throw new IllegalArgumentException("Counter not found for:" + name);
        }
        return counter.createReference();
    }*/

    public static Set<String> counterNames() {
        return Collections.unmodifiableSet(counters.keySet());
    }

    public static Tracker getTracker( String name, boolean create ) {
        return getTracker(name, 0, create);
    }
    
    public static Tracker getTracker( String name, int windowSize, boolean create ) {
        Tracker result = trackers.get(name);
        if( result == null && create ) {
            synchronized(trackers) {
                result = trackers.get(name);
                if( result == null ) {
                    result = new Tracker(name, windowSize);
                    trackers.put(name, result);
                }
            }
        }
        return result;
    }
 
    public static long getTrackerValue( String name ) {
        Tracker result = trackers.get(name);
        return result == null ? -1 : result.get();
    } 

    /*public static VersionedReference<Long> createTrackerRef( String name, boolean create ) {
        Tracker tracker = getTracker(name, create);
        if( tracker == null ) {
            throw new IllegalArgumentException("Tracker not found for:" + name);
        }
        return tracker.createReference();
    }*/

    public static Set<String> trackerNames() {
        return Collections.unmodifiableSet(trackers.keySet());
    }
    
    public static Sequence getSequence( String name, boolean create ) {
        Sequence result = sequences.get(name);
        if( result == null && create ) {
            synchronized(sequences) {
                result = sequences.get(name);
                if( result == null ) {
                    result = new Sequence(name);
                    sequences.put(name, result);
                } 
            }
        }
        return result; 
    }

    public static Set<String> sequenceNames() {
        return Collections.unmodifiableSet(sequences.keySet());
    }

    protected abstract static class AbstractValue<T> { //implements VersionedObject<T> {
        private final String name;
        private final AtomicLong version;
        
        protected AbstractValue( String name ) {
            this.name = name;
            this.version = new AtomicLong();
        }        
 
        public String getName() {
            return name;
        }
 
        protected void incrementVersion() {
            version.incrementAndGet();
        }
 
        /*@Override
        public long getVersion() {
            return version.get();                        
        }

        @Override
        public abstract T getObject();

        @Override
        public VersionedReference<T> createReference() {
            return new VersionedReference<>(this);
        }*/
    }

    public static class Counter extends AbstractValue<Long> {
        private final AtomicLong counter;
        
        public Counter( String name ) {
            super(name);
            this.counter = new AtomicLong();
        }
 
        public long get() {
            return counter.get();
        }
        
        public long increment() {
            incrementVersion();
            return counter.incrementAndGet();
        }
        
        public long decrement() {
            incrementVersion();
            return counter.decrementAndGet();
        }
        
        public long add( long value ) {
            incrementVersion();
            return counter.incrementAndGet();
        }

        /*@Override
        public Long getObject() {
            return get();
        }*/

        @Override
        public String toString() {
            return "Counter[" + getName() + "=" + counter.get() + "]";
        }        
    }
    
    public static class Tracker extends AbstractValue<Long> {
        private long value;
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private long size;
        private final long windowSize;

        public Tracker( String name, long windowSize ) {
            super(name);
            this.windowSize = windowSize;
        }

        public long get() {
            lock.readLock().lock();
            try {
                return value;
            } finally {
                lock.readLock().unlock();
            }
        }

        public long update( long newValue ) {
            lock.writeLock().lock();
            try {
                // Calculate a weighted average given a statistical window size
                long count = Math.min(size, windowSize); 
                size++;
                value = (value * count + newValue) / (count + 1);
                return value;
            } finally {
                lock.writeLock().unlock();
            }
        }

        /*@Override
        public Long getObject() {
            return get();           
        }*/
        
        @Override
        public String toString() {
            return "Tracker[" + getName() + "=" + get() + "]";
        }        
    }
    
    public static class Sequence {
        private final String name;
        private final ConcurrentLinkedQueue<Long> values = new ConcurrentLinkedQueue<>();
        private final int maxSize = 1000;  // keep from gobbling all available space.
 
        public Sequence( String name ) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
 
        public boolean isEmpty() {
            return values.isEmpty();
        }
 
        public void add( Long value ) {
            values.add(value);
            while(values.size() > maxSize) {
                values.poll();
            }
        }
        
        public Long poll() {
            return values.poll();
        }
        
        @Override
        public String toString() {
            return "Sequence[" + getName() + " size=" + values.size() + "]";
        }       
    } 
} 
