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

import com.simsilica.ethereal.io.BitInputStream;
import com.simsilica.ethereal.io.BitOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 *
 *
 *  @author    Paul Speed
 */
public class FrameState {

    public long legacySequence;
    public long time;
    public long columnId;
    public List<ObjectState> states = new ArrayList<>();
    public long estimatedBitSize;
    
    public FrameState() {
        this( -1, -1, -1 );
    }
 
    public FrameState( long time, long legacySequence, long columnId ) {
        this.time = time;
        
        // Not sure sequence is really the way to go as client will
        // need some kind of time and sequence is at worse superfluous
        // and at best too many bits for what it does. 
        this.legacySequence = legacySequence;
        this.columnId = columnId;
        
        // Header + list size
        this.estimatedBitSize = getHeaderBitSize();
    }
        
    public static int getHeaderBitSize() {
        // extra 16 bits for the list size
        return 64 + 64 + 16;
    }

    public long getEstimatedBitSize() {
        return estimatedBitSize;
    }
    
    public void addState( ObjectState state, ObjectStateProtocol protocol ) {
        if( state.networkId == -1 ) {
            throw new IllegalArgumentException("Incomplete state added to frame:" + state);
        }
            
        states.add(state);
        estimatedBitSize += protocol.getEstimatedBitSize(state);   
    }
    
    public FrameState split( long limit, ObjectStateProtocol protocol ) {
        if( estimatedBitSize <= limit ) {
            return null;
        }
        
        // Else split us at the right place... which we need to find
        long size = getHeaderBitSize();
        int split = 0;
        while( split < states.size() ) {
            ObjectState s = states.get(split);
            int bits = protocol.getEstimatedBitSize(s); 
            if( size + bits > limit )
                break;
            size += bits;
            split++; 
        }
        if( split == 0 || split == states.size() ) {
            throw new RuntimeException( "Error splitting message. split:" + split + " limit:" + limit);
        }
        long leftOverBits = estimatedBitSize - size;
        
        // Create a new frame with the left-overs
        FrameState result = new FrameState(time, legacySequence+1, columnId);
        result.states = states.subList(split, states.size());
        result.estimatedBitSize += leftOverBits;  // add them to the header size
        
        // Now reset this frame's fields accordingly
        estimatedBitSize = size;
        states = states.subList(0, split);
        
        return result;        
    }
 
    
    public void writeBits( BitOutputStream out, ObjectStateProtocol protocol ) throws IOException {
        out.writeLongBits(time, 64);
        out.writeLongBits(legacySequence, 64);
        out.writeLongBits(columnId, 64);
        
        // Write the list size as 16 bits... we could probably get
        // away with much less but it's hard to be sure
        out.writeBits(states.size(), 16); 
        
        for( ObjectState s : states ) {
            protocol.writeBits(s, out);
        }
    }
    
    public void readBits( BitInputStream in, ObjectStateProtocol protocol ) throws IOException {
 
        this.time = in.readLongBits(64);    
        this.legacySequence = in.readLongBits(64);
        this.columnId = in.readLongBits(64);
        
        int size = in.readBits(16);
        states.clear();
        for( int i = 0; i < size; i++ ) {
            ObjectState s = protocol.readBits(in);
            states.add(s);
        }
    } 
    
    @Override
    public String toString() {
        return "FrameState[time=" + time + ", legacySequence=" + legacySequence + ", columnId=" + columnId + ", states=" + states + "]";
    }
}

