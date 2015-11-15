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

package com.simsilica.ethereal.net;

import com.simsilica.ethereal.DebugUtils;
import com.simsilica.ethereal.Statistics;
import com.simsilica.ethereal.Statistics.Sequence;
import com.simsilica.ethereal.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *  Keeps track of the latest time included in messages from
 *  the server to guess at a consistent time offset for this client.
 *  The idea is to roughly simulate that the times coming in from the
 *  server represent "now" when they arrive.
 *
 *  @author    Paul Speed
 */
public class RemoteTimeSource implements TimeSource {

    static Logger log = LoggerFactory.getLogger(RemoteTimeSource.class);

    // Even if multiple threads are calling getTime() this should
    // be safe to leave unvolatile because the limit is ok to be
    // thread specific in cases where the lastTime value is out of sync.
    private long lastTime = 0; // time should never go backwards

    // Adjusted by updateDrift() and used by getTime()... needs to be
    // volatile to make sure everyone is up to date.
    private volatile long drift = 0; 
    private volatile boolean uninitialized = true;

    // Should be set once during init or early on.  No need for
    // volatile and anyway all of the other volatiles will force a 
    // memory barrier. 
    private long offset = 0;
 
    // Only used from the updateDrift() call which should be single-threaded
    // so volatile is not needed.   
    private long lastServerTime = 0;
    private long windowMax = 100;
    private long windowSize = 0;
    
    // For debug tracking
    private Sequence syncTime;
 
    public RemoteTimeSource() {
        this(0);
    }
    
    public RemoteTimeSource( long offset ) {
        this.offset = offset;
        
        // Need to have a flag for this somehow
        this.syncTime = Statistics.getSequence("syncTime", true); 
    }
 
    public void setOffset( long offset ) {
        this.offset = offset;
    }
    
    public long getOffset() {
        return offset;
    }
     
    protected void updateDrift( long serverTime ) {
        
        syncTime.add(serverTime);
    
        this.lastServerTime = serverTime;
        long t = System.nanoTime();
        
        // What do we have to 'add' to our time to get server time.
        long delta = serverTime - t;
 
        // Calculate the running average for drift... we want
        // drift to vary slowly.
        long newDrift = (delta + drift * windowSize) / (windowSize + 1);
        if( log.isDebugEnabled() ) {
            log.debug("======== Time delta:" + DebugUtils.timeString(delta) + "  drift:" + DebugUtils.timeString(newDrift) + "  windowSize:" + windowSize);
            log.debug("=== oldDrift:" + drift + "  drift change:" + DebugUtils.timeString(newDrift - drift)); 
        }
        drift = newDrift;
        if( windowSize < windowMax ) {
            windowSize++;
        }
        
        uninitialized = false;
    }
    
    public void update( ObjectStateMessage msg ) {
        long t = msg.getTime();
        if( t > lastServerTime ) {
            updateDrift(t);
        }
    }

    @Override
    public long getTime() {
        if( uninitialized ) {
            return 0;
        }
        long t = System.nanoTime();
        t = t + drift + offset;
        if( t > lastTime ) {
            lastTime = t;
        } else {
System.out.println("Time rolled backwards:" + (lastTime - t) + " nanos");        
        }
        return lastTime;
    }

    @Override
    public long getDrift() {
        return drift;
    }
}
