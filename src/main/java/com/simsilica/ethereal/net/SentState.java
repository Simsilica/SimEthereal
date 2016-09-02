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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 *
 *
 *  @author    Paul Speed
 */
public class SentState {

    public long created = System.nanoTime();
    public int messageId;
    public int[] acked;
    public List<FrameState> frames;

    public SentState( int messageId, int[] acked, List<FrameState> frames ) {
        this.messageId = messageId;
        this.acked = acked;
        this.frames = frames;               
    }

    public boolean isBefore( SentState state ) {
        return isBefore(state.messageId);
    }

    /**
     *  Returns true if this state is before the specified messageId.  Message IDs
     *  wrap so a simple &lt; check is not enough.
     */
    public boolean isBefore( int compare ) {
        if( Math.abs(messageId - compare) > 32000 ) {
            // Then we'll assume that the IDs have wrapped
            // so our comparison is backwards.
            return true;
        }
        return messageId < compare;
    }
   

    /** 
     *  Returns the number of bits that will be written for the header. 
     */
    public int getEstimatedHeaderSize() {
        int result = 0;
        result += 8; // array size
        result += acked == null ? 0 : acked.length * 16; // array values
        return result;  
    }

    public static SentState fromByteArray( int sequenceId, byte[] buffer, ObjectStateProtocol protocol ) throws IOException {
    
        ByteArrayInputStream bIn = new ByteArrayInputStream(buffer);
        BitInputStream in = new BitInputStream(bIn);
        try {
            List<FrameState> frames = new ArrayList<>();
            
            // First read the acks array
            int size = in.readBits(8);        
            int[] acks = new int[size];
            for( int i = 0; i < size; i++ )
                acks[i] = in.readBits(32);

            // Then read each frame... this protocol presumes that a bit
            // per frame will ultimately be smaller than a fixed size count, on average.
            while( in.readBits(1) == 1 ) {
                FrameState frame = new FrameState();
                frame.readBits(in, protocol);
                frames.add(frame);
            }
 
            return new SentState(sequenceId, acks, frames);           
        } finally {
            in.close();
        }
    }

    public static byte[] toByteArray( SentState state, ObjectStateProtocol protocol ) throws IOException {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        BitOutputStream out = new BitOutputStream(bOut);
 
        // Write the acks array first
        out.writeBits(state.acked.length, 8);
        for( int s : state.acked ) {
            out.writeBits(s, 32);
        }
        
        // Now write the frames with their marker bit
        for( FrameState frame : state.frames ) {
            out.writeBits(1, 1);
            frame.writeBits(out, protocol);
        }
        
        // And the empty bit
        out.writeBits(0, 1);

        out.close();
        
        return bOut.toByteArray();        
    } 
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for( int i : acked ) {
            if( sb.length() > 0 ) {
                sb.append(", ");
            }
            sb.append("(" + i + ")");
        }
        return "SentState[messageId=" + messageId + ", created=" + created + ", acked=[" + sb + "], frames=" + frames + "]";
    }        
}
