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

package com.simsilica.ethereal.zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *  Uses a background thread to periodically collect the accumulated
 *  state for the zones and send the blocks of state to zone state 
 *  listeners. 
 *
 *  @author    Paul Speed
 */
public class StateCollector {

    static Logger log = LoggerFactory.getLogger(StateCollector.class);

    private static final long NANOS_PER_SEC = 1000000000L;
    public static final long DEFAULT_PERIOD = NANOS_PER_SEC / 20;   
    
    private ZoneManager zones;
    private long collectionPeriod;
    private long idleSleepTime = 1; // for our standard 20 FPS that's more than fine
    private final Runner runner = new Runner();

    private final Set<StateListener> listeners = new CopyOnWriteArraySet<>();
    private final ConcurrentLinkedQueue<StateListener> removed = new ConcurrentLinkedQueue<>();
    
    /**
     *  This is the actual zone interest management.  It's only used by the
     *  background thread which is why it is unsynchronized.  All interactio
     *  is done through the listeners set and removed queue.
     */
    private final Map<ZoneKey,List<StateListener>> zoneListeners = new HashMap<>();
    
    public StateCollector( ZoneManager zones ) {
        this(zones, DEFAULT_PERIOD); 
    }
    
    public StateCollector( ZoneManager zones, long collectionPeriod ) {
        this.zones = zones;
        this.collectionPeriod = collectionPeriod == 0 ? DEFAULT_PERIOD : collectionPeriod;
    }

    public void start() {
        log.info("Starting state collector.");    
        runner.start();
    }
 
    public void shutdown() {
        log.info("Shuttong down state collector.");    
        runner.close();
    }

    /**
     *  Adds a listener that self-indicates which specific zones
     *  it is interested in from one frame to the next.  This is necessary
     *  so that it syncs with the background state collection in a way
     *  that does not cause partial state, etc... it's also nicer to
     *  to the background threads and synchronization if zone interest
     *  is synched with updates.
     */
    public void addListener( StateListener l ) {
        listeners.add(l);
    }
    
    public void removeListener( StateListener l ) {
        listeners.remove(l);
        removed.add(l);
    }
    
    /**
     *  Sets the sleep() time for the state collector's idle periods.
     *  This defaults to 1 which keeps the CPU happier while also providing
     *  timely checks against the interval time.  However, for higher rates of 
     *  state collection (lower collectionPeriods such as 16 ms or 60 FPS) on windows, 
     *  sleep(1) may take longer than 1/60th of a second or close enough to still 
     *  cause collection frame drops.  In that case, it can be configured to 0 which 
     *  should provide timelier updates.
     *  Set to -1 to avoid sleeping at all in which case the thread will consume 100%
     *  of a single core in order to busy wait between collection intervals.
     */
    public void setIdleSleepTime( long millis ) {
        this.idleSleepTime = millis;
    }
    
    public long getIdleSleepTime() {
        return idleSleepTime;
    } 

    protected List<StateListener> getListeners( ZoneKey key, boolean create ) {
        List<StateListener> result = zoneListeners.get(key);
        if( result == null && create ) {
            result = new ArrayList<>();
            zoneListeners.put(key, result);
        }
        return result;
    }

    protected void watch( ZoneKey key, StateListener l ) {
        if( log.isTraceEnabled() ) {
            log.trace("watch(" + key + ", " + l + ")");
        }
        getListeners(key, true).add(l);
    }
     
    protected void unwatch( ZoneKey key, StateListener l ) {
        if( log.isTraceEnabled() ) {
            log.trace("unwatch(" + key + ", " + l + ")" );
        }    
        List<StateListener> list = getListeners(key, false);
        if( list == null ) {
            return;
        }
        list.remove(l);
    } 

    protected void unwatchAll( StateListener l ) {
        if( log.isTraceEnabled() ) {
            log.trace("unwatchAll(" + l + ")" );
        }    
        for( List<StateListener> list : zoneListeners.values() ) {
            list.remove(l);
        }
    }

    /**
     *  Called from the background thread when it first starts up.
     */
    protected void initialize() {
        // Let the zone manager know that it can start collecting history
        zones.setCollectHistory(true);       
    }

    protected void publish( StateBlock b ) {
        List<StateListener> list = getListeners(b.getZone(), false);
        if( list == null ) {
            return;
        }
        for( StateListener l : list ) {
            l.stateChanged(b);
        }
    }
 
    /**
     *  Adjusts the per-listener zone interest based on latest
     *  listener state, then publishes the state frame to all
     *  interested listeners. 
     */   
    protected void publishFrame( StateFrame frame ) {
        log.trace("publishFrame()");

        for( StateListener l : listeners ) {
            if( l.hasChangedZones() ) {
                List<ZoneKey> exited = l.getExitedZones();
                for( ZoneKey k : exited ) {
                    unwatch( k, l );
                }
                
                List<ZoneKey> entered = l.getEnteredZones();
                for( ZoneKey k : entered ) {
                    watch( k, l );
                }
            }            
            l.beginFrame(frame.getTime());
        }
            
        for( StateBlock b : frame ) {
            publish( b );
        }
            
        for( StateListener l : listeners ) {
            l.endFrame(frame.getTime());
        }            
        log.trace("end publishFrame()");
    }
 
    /**
     *  Called by the background thread to collect all
     *  of the accumulated state since the last collection and
     *  distribute it to the state listeners.  This is called
     *  once per "collectionPeriod".
     */   
    protected void collect() {
        log.trace("collect()");
 
        // Purge any pending removals
        StateListener remove;
        while( (remove = removed.poll()) != null ) {
            unwatchAll(remove);
        }
 
        // Collect all state since the last time we asked   
//        long start = System.nanoTime();
        StateFrame[] frames = zones.purgeState();
//        long end = System.nanoTime();        
//        System.out.println( "State purged in:" + ((end - start)/1000000.0) + " ms" );

        for( StateListener l : listeners ) {
            l.beginFrameBlock();
        }
            
  //      start = end;
        for( StateFrame f : frames ) {
            if( f == null ) {
                continue;
            }
            publishFrame(f);
        }
            
        for( StateListener l : listeners ) {
            l.endFrameBlock();
        }

//        end = System.nanoTime();                    
//        System.out.println( "State published in:" + ((end - start)/1000000.0) + " ms" );
        log.trace("end collect()");    
    }
 
    /**
     *  Called by the background thread when it is shutting down.
     *  Currently does nothing.
     */   
    protected void terminate() {
        // Let the zone manager know that it should stop collecting
        // history because we won't be purging it anymore.
        zones.setCollectHistory(false);       
    }
    
    protected void collectionError( Exception e ) {
        log.error("Collection error", e);
    }
    
    private class Runner extends Thread {
        private final AtomicBoolean go = new AtomicBoolean(true);
        
        public Runner() {
            setName( "StateCollectionThread" );
            //setPriority( Thread.MAX_PRIORITY );    
        }
 
        public void close() {
            go.set(false);
            try {
                join();
            } catch( InterruptedException e ) {
                throw new RuntimeException( "Interrupted while waiting for physic thread to complete.", e );
            }
        }
        
        @Override
        public void run() {        
            initialize();
            long lastTime = System.nanoTime();
long counter = 0;            
long nextCountTime = lastTime + 1000000000L;
            while( go.get() ) {
                long time = System.nanoTime();
                long delta = time - lastTime; 
                if( delta >= collectionPeriod ) {
                    // Time to collect 
                    lastTime = time;                                        
                    try {
                        collect();
                        counter++;
                        //long end = System.nanoTime();
                        //delta = end - time;
                    } catch( Exception e ) {
                        collectionError(e);
                    }
                    
if( lastTime > nextCountTime ) {
    if( counter < 20 ) {
        System.out.println("collect underflow FPS:" + counter);        
    }
//System.out.println("collect FPS:" + counter);    
    counter = 0;            
    nextCountTime = lastTime + 1000000000L;
}                   
                    // Don't sleep when we've processed in case we need
                    // to process again immediately.
                    continue;
                }
                
                // Wait just a little.  This is an important enough thread
                // that we'll poll instead of smart-sleep.
                try {
                    if( idleSleepTime > 0 ) {
                        Thread.sleep(idleSleepTime);
                    }
                } catch( InterruptedException e ) {
                    throw new RuntimeException("Interrupted sleeping", e);
                }
            }                    
            terminate();
        }
    }       
}


