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


import com.jme3.network.*;
import com.jme3.network.serializing.Serializable;

/**
 *
 *
 *  @author    Paul Speed
 */
@Serializable
public class ClientStateMessage extends AbstractMessage {

    private int ackId; // ID ofo the message we are ack'ing  
    private long time; // for ping tracking... nano time 
                       // of the message we received from the server
    private long controlBits;
    private transient long receivedTime;
    
    public ClientStateMessage() {
    }
    
    public ClientStateMessage( ObjectStateMessage ack, long controlBits ) {
        this.ackId = ack.getId();
        this.time = ack.getTime();
        this.controlBits = controlBits;
    }
    
    public void resetReceivedTime() {
        receivedTime = System.nanoTime();    
    }
    
    public long getReceivedTime() {
        return receivedTime;
    }
    
    public int getId() { 
        return ackId;
    }
    
    public long getTime() {
        return time;
    }
    
    public long getControlBits() {
        return controlBits;
    }
    
    @Override
    public String toString() {
        return "ClientStateMessage[id=" + ackId + ", time=" + time + ", controlBits=" + Long.toBinaryString(controlBits) + "]";
    }    
}

