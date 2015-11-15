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

import com.simsilica.ethereal.net.FrameState;
import com.simsilica.ethereal.net.ObjectState;
import com.simsilica.ethereal.net.ObjectStateProtocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 *  The synched shared object space that is local to the player.
 *  A copy of this is kept per-player on the server and in the client.
 *  Each SharedObject contains a baseline and a set of current values.
 *  These are used to form the actual network messages that are sent
 *  around.  The baseline is updated when the client and server agree
 *  about the level of messages they have both seen.
 *
 *  @author    Paul Speed
 */
public class SharedObjectSpace {

    private final ObjectStateProtocol objectProtocol;
    private final Map<Integer,SharedObject> objects = new HashMap<>();
    
    private final ConcurrentLinkedQueue<SharedObjectListener> toAdd = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SharedObjectListener> toRemove = new ConcurrentLinkedQueue<>();
    private final List<SharedObjectListener> listeners = new ArrayList<>();
    private SharedObjectListener[] listenerArray;
    
    public SharedObjectSpace( ObjectStateProtocol objectProtocol ) {
        this.objectProtocol = objectProtocol;
    }
 
    public final ObjectStateProtocol getObjectProtocol() {
        return objectProtocol;
    }
    
    public SharedObject getObject( int networkId, Long entityId ) {
        SharedObject result = objects.get(networkId);
        if( result == null ) {
            result = new SharedObject(this, networkId, entityId);
            objects.put(networkId, result);
        }
        return result;
    }
    
    public SharedObject getObject( int networkId ) {
        return objects.get(networkId);
    }   
    
    public void removeObject( SharedObject so ) {
        objects.remove(so.getNetworkId());
    }
    
    public Collection<SharedObject> objects() {
        return objects.values();
    }

    public void addObjectListener( SharedObjectListener l ) {
        toAdd.add(l);
        toRemove.remove(l);
    }
    
    public void removeObjectListener( SharedObjectListener l ) {
        toRemove.add(l);
        toAdd.remove(l);
    } 

    private SharedObjectListener[] getListeners() {
        if( listenerArray == null ) {
            listenerArray = new SharedObjectListener[listeners.size()];
            listenerArray = listeners.toArray(listenerArray);
        }
        return listenerArray;
    }    
 
    /**
     *  Used by the state receiver to notify client-side SharedObjectListeners
     *  about a new frame.
     */   
    public final void beginFrame( long time ) {
    
        // This makes sure that we don't notify some listener about
        // an object before they got a beginFrame()
        while( !toAdd.isEmpty() ) {
            SharedObjectListener l = toAdd.poll();
            listeners.add(l);
            listenerArray = null;              
        }
        while( !toRemove.isEmpty() ) {
            SharedObjectListener l = toRemove.poll();
            listeners.remove(l);
            listenerArray = null;              
        }
    
        for( SharedObjectListener l : getListeners() ) {
            l.beginFrame(time);
        }
    }
    
    /**
     *  Used by the shared object to notify client-side SharedObjectListeners
     *  about an object change.
     */   
    protected final void objectUpdated( SharedObject obj ) {
        for( SharedObjectListener l : getListeners() ) {
            l.objectUpdated(obj);
        }
    }
    
    /**
     *  Used by the shared object to notify client-side SharedObjectListeners
     *  about an object removal.
     */   
    protected final void objectRemoved( SharedObject obj ) {
        for( SharedObjectListener l : getListeners() ) {
            l.objectRemoved(obj);
        }
    }
    
    /**
     *  Used by the state receiver to notify client-side SharedObjectListeners
     *  about an ended frame.
     */   
    public final void endFrame() {
        for( SharedObjectListener l : getListeners() ) {
            l.endFrame();
        }
    }
    
    /**
     *  Update the shared object baselines with the specified frame
     *  data.
     */
    public void updateBaseline( List<FrameState> frames ) {
        for( FrameState frame : frames ) {
            for( ObjectState state : frame.states ) {
            
                SharedObject so = getObject(state.networkId);
                if( so == null ) {
                    // This can happen if we receive duplicate state..
                    // which we will... often.
                    continue;
                }            
 
                so.updateBaseline(frame.time, state);
            }
        }
    }
}

