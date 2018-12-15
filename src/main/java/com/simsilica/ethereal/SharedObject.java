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

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.ethereal.net.ObjectState;
import com.simsilica.ethereal.zone.ZoneKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 *
 *  @author    Paul Speed
 */
public class SharedObject {
    
    static Logger log = LoggerFactory.getLogger(SharedObject.class);
    
    private final SharedObjectSpace space;
    
    // On the server, version is time.  On the client, version
    // is message sequence number.  We could probably use a seq# for
    // both I suppose.    
    private long version;
    private final ObjectState current;
    
    // Baseline version is always a sequence number as it's always
    // updated from network messages.
    private long baselineVersion;
    private ObjectState baseline;
    private ZoneKey zone;
    
    // This is a bit of a kludge but only kind of.  On the client,
    // we may continue receiving updates for removed objects but we
    // only want to notify listeners about the removal once (unless the
    // state has changed).  So we keep a flag here that the caller
    // can set if it has already sent remove notifications out.
    // We will clear it in applyNetworkState() if the remove status
    // changes to unremoved.  It's cheaper to keep it here than to have
    // StateReceiver keep a whole other data structure just for this...
    // also we can more easily clear the flag here.
    private boolean notifiedRemoved;
    
    public SharedObject( SharedObjectSpace space, int networkId, Long realId ) {
        this.space = space;
        this.current = new ObjectState(networkId, realId);
    }
 
    public int getNetworkId() {
        return current.networkId;
    }
    
    public Long getEntityId() {
        return current.realId;
    }
 
    public long getVersion() {
        return version;
    }
    
    public ObjectState getDelta() {
        return current.getDelta(baseline);
    }
    
    public Vec3d getWorldPosition() {
        Vec3d result = space.getObjectProtocol().getPosition(current); 
        return zone.toWorld(result, result);
    }
    
    public Quatd getWorldRotation() {
        return space.getObjectProtocol().getRotation(current);
    }
    
    /**
     *  Returns true if the current version is marked for removal
     *  even if the baseline is not.
     */
    public boolean isMarkedRemoved() {
        return current.isMarkedRemoved();
    }
 
    /**
     *  Returns true if the object is fully marked for removal
     *  in both the current version and in the baseline (if it exists).
     *  If the baseline does not exist then this method returns false
     *  since we can never fully remove an object until we at least know
     *  the client and server share a common baseline.
     */
    public boolean isFullyMarkedRemoved() {
        if( baseline == null ) {
            return false;
        }
        
        return baseline.isMarkedRemoved() && current.isMarkedRemoved();   
    }

    /**
     *  Marks this version of the object as removed if the specified
     *  time is after the current object's last update time.
     */
    public void markRemoved( long time ) {
        // Only update the time if it is newer than our last update
        // We may have received valid updates for this object from
        // one zone while we may later see a removal notice for a
        // completely different zone... but we should ignore it.
        if( time > this.version ) {
            current.markRemoved();
        }
    } 
 
    /**
     *  The client side state receiver sets this flag if it has already
     *  notified listeners about a removal.  SharedObject will unset it
     *  if the object becomes unremoved.
     */
    public void markNotifiedRemoved( boolean b ) {
        this.notifiedRemoved = b;
    }
 
    /**
     *  Returns true if the client side state receiver has already notified
     *  listeners above the markRemoved.
     */   
    public boolean isNotifiedRemoved() {
        return notifiedRemoved;
    }

    /**
     *  Updates the current object state with the supplied world state.  This
     *  is called on the server as real object history/state is streamed to the
     *  shared space. 
     */
    public boolean updateState( long time, ZoneKey zone, int zoneId, Long parentId, 
                                Vec3d pos, Quatd rot ) {
                                
        if( time <= this.version ) {
            // We are already more current than the state provided
            // This can happen when we receive state for the same object multiple
            // times... like when the object overlaps two zones that we are watching.
            return false;
        }
                
        if( current.isMarkedRemoved() ) {
            // Just a bit of a message just in case
            // Note: we may have been removed from another zone but now we
            // can become active again in a new zone
            if( log.isDebugEnabled() ) {
                log.debug("Unremoving:" + current.realId);
            }
        }
        
        this.version = time;
 
        this.zone = zone;
        current.zoneId = zoneId;
        current.parentId = parentId;
        
        Vec3d localPos = zone.toLocal(pos);
        space.getObjectProtocol().setPosition(current, localPos);
        space.getObjectProtocol().setRotation(current, rot);
        
        return true;
    }
 
    public boolean updateBaseline( long sequence, ObjectState state ) {
    
        if( baseline == null ) {
            baseline = state.clone();
            return true;
        }
        
        if( baselineVersion > sequence ) { 
            // We already have newwer state than this... perhaps
            // an ACK came out of order.
            return false;
        }
        
        baselineVersion = sequence;
        baseline.applyDelta(state);
        return true;
    }
    
    public boolean applyNetworkState( long sequence, ObjectState state, LocalZoneIndex zoneIndex ) {
        // This is kind of a three way merge of sorts.
        // Whatever values are unset in the supplied state need to go 
        // back to being baseline.  Whatever are set in the supplied 
        // state override baseline.
        //
        // So the easiest way is to reset current to baseline and
        // apply the state as a regular delta.
        if( log.isDebugEnabled() ) {
            log.debug("applyNetworkState(" + sequence + ", " + state + ")");
        }
        if( version > sequence ) {
            if( log.isDebugEnabled() ) {
                log.debug( "********** Already have newer state for:" + current.realId );
            }
            return false;
        }
        
        version = sequence;
 
        // Reset our current version to baseline since that's what
        // the delta is based on
        if( baseline != null ) {
            current.set(baseline);
        }
        
        // Now apply the delta
        current.applyDelta(state);          
 
        if( current.zoneId == -1 ) {
            throw new RuntimeException("No zoneId set for object with ID:" + current.realId);
        }
        // Make sure our zone key is up to date
        this.zone = zoneIndex.getZone(current.zoneId, this.zone);
 
        if( !isMarkedRemoved() ) {
            // Things have changed and we might have told listeners it was
            // removed before but now it's not.
            notifiedRemoved = false;
            
            // Notify the listeners about the object change
            space.objectUpdated(this);
            
        } else if( !notifiedRemoved ) {
            notifiedRemoved = true;
            // Then notify listeners that the object has been removed
            // and mark it notified
            space.objectRemoved(this);
        } 
        
        return true;
    }
}


