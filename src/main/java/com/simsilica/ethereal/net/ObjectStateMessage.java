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

import com.jme3.network.AbstractMessage;
import com.jme3.network.serializing.Serializable;
import java.io.IOException;


/**
 *
 *
 *  @author    Paul Speed
 */
@Serializable
public class ObjectStateMessage extends AbstractMessage {

    public static final int HEADER_SIZE = 2 + 8 + 1 + 4; // id, time, null byte for buffer, size of buffer

    private int id; // the message sequence number
    private long time; // for ping tracking
    private byte[] buffer;

    public ObjectStateMessage() {
    }
    
    public ObjectStateMessage( int id, long nanoTime, byte[] buffer ) {
        this.id = id;
        this.time = nanoTime;
        this.buffer = buffer;
    }
 
    public int getId() {
        return id;
    }
    
    public long getTime() {
        return time;
    }

    public byte[] getBuffer() {
        return buffer;
    }
 
    public SentState getState( ObjectStateProtocol protocol ) {
        if( buffer == null ) {
            return null;
        } 
        try {
            return SentState.fromByteArray(id, buffer, protocol);                    
        } catch( IOException e ) {
            throw new RuntimeException("Error reading frame states", e);
        }
    }
 
    public void setState( SentState state, ObjectStateProtocol protocol ) {
        if( state == null ) {
            buffer = null;
            return;
        } 
        try {
            state.messageId = id;
            this.buffer = SentState.toByteArray(state, protocol);
        } catch( IOException e ) {
            throw new RuntimeException("Error writing frame states", e);
        }
    }  
 
    @Override
    public String toString() {
        if( buffer == null ) {
            return "ObjectStateMessage[]";
        }
            
        return "ObjectStateMessage[id=" + id + ", time=" + time + ", size=" + buffer.length + "]";
    }
}


