/*
 * $Id$
 * 
 * Copyright (c) 2016, Simsilica, LLC
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

import java.util.concurrent.atomic.AtomicLong;

/**
 *  Keeps track of server-side statistics for a particular 
 *  HostedConnection. 
 *
 *  @author    Paul Speed
 */
public class ConnectionStats {

    private RollingAverage ping = new RollingAverage(5);
    private MissCounter acks = new MissCounter();
    
    public ConnectionStats() {
    }
    
    public final void addPingTime( long time ) {
        ping.add(time);
    }
 
    public long getAveragePingTime() {
        return ping.average.get();
    }
    
    public final void incrementAcks() {
        acks.incrementTotal();
    }
    
    public final void incrementAckMisses() {
        acks.incrementMisses();
    }
 
    public final double getAckMissPercent() {
        return acks.percentTimes10.get() / 10.0;
    } 
    
    private class RollingAverage {
        private int windowSize;
        private int count;
        private long accumulator;
        private AtomicLong average = new AtomicLong();
        
        public RollingAverage( int windowSize ) {
            this.windowSize = windowSize;
        }
        
        public void add( long value ) {
            long size = Math.min(count, windowSize);
            count++;
            long roll = (accumulator * size + value) / (size + 1);
            average.set(roll);            
        }
    }
    
    private class MissCounter {
        private long total;
        private long misses;
        private AtomicLong percentTimes10 = new AtomicLong();
        
        public final void incrementTotal() {
            total++;
            percentTimes10.set(misses * 1000 / total);
        }
        
        public final void incrementMisses() {
            misses++;
            percentTimes10.set(misses * 1000 / total);
        }
    }   
}
