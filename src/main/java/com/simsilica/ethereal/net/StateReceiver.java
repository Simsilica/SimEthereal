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

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.Client;

import com.simsilica.mathd.util.*;

import com.simsilica.ethereal.LocalZoneIndex;
import com.simsilica.ethereal.SharedObject;
import com.simsilica.ethereal.SharedObjectSpace;
import com.simsilica.ethereal.Statistics;
import com.simsilica.ethereal.Statistics.Sequence;
import com.simsilica.ethereal.Statistics.Tracker;
import com.simsilica.ethereal.zone.ZoneGrid;
import com.simsilica.ethereal.zone.ZoneKey;



/**
 *  Handles the incoming ObjectStateMessage and uses it to update
 *  the local SharedObjectSpace.
 *
 *  @author    Paul Speed
 */
public class StateReceiver {
    
    static Logger log = LoggerFactory.getLogger(StateReceiver.class);
    
    private final Client client;
    private final ObjectStateProtocol objectProtocol;
    
    private final SharedObjectSpace space;
    private final LocalZoneIndex zoneIndex;
    private final ZoneGrid grid;

    private final RemoteTimeSource timeSource;

    /**
     *  Track the states we've received by message ID so that we
     *  can update our baseline with the double-acks from the server.
     */
    private final Map<Integer,SentState> receivedStates = new TreeMap<>();
 
    private long lastFrameTime;
 
    private final Sequence frameTime;
    private final Tracker messageSize;
 
    public StateReceiver( Client client, LocalZoneIndex zoneIndex, SharedObjectSpace space ) {
        this.client = client;
        this.space = space;
        this.zoneIndex = zoneIndex;
        this.grid = zoneIndex.getGrid();
        this.objectProtocol = space.getObjectProtocol();
        this.timeSource = new RemoteTimeSource(-100 * 1000000L); // -100 ms in the past to start with
        
        this.frameTime = Statistics.getSequence("stateTime", true);
        this.messageSize = Statistics.getTracker("messageSize", 5, true);
    }
 
    public RemoteTimeSource getTimeSource() {
        return timeSource;
    }
       
    public void handleMessage( ObjectStateMessage msg ) {
        
        timeSource.update(msg);
 
        if( log.isDebugEnabled() ) {
            log.debug("Update state:" + msg);
        }        

        // Very first thing we do is acknowledge the message
        // 2022-11-05 - I believe that sending these reliably is why we can
        // just ack this single message and not keep a running set of all
        // acknowledged messages to send back.  We know the server will eventually
        // see this and we don't particularly care how long it takes.
        client.send(new ClientStateMessage(msg, 0));
 
        // Collect the statistics
        messageSize.update(ObjectStateMessage.HEADER_SIZE + msg.getBuffer().length);
        
        // Grab the state and track it for later.  We will
        // apply this state to our 'current' versions but we will
        // also keep it around to update the baseline version when
        // we are sure the server knows that we have received it.            
        SentState state = msg.getState(objectProtocol);
        
        if( log.isDebugEnabled() ) {
            log.debug("State:" + state);
        }
        receivedStates.put(state.messageId, state);

        processAcks(state.acked);
        
        
        // Now the baselines are properly setup for interpretation
        // of the incoming delta messages.  All messages are interpretted
        // as deltas from the object baselines so it is important to make
        // sure they are synched with what the server thinks they are...
        // and that's what the ACK message list is for.  The server
        // is telling us:
        // "These are the messages that you said you already received that
        // I'm basing my baselines on"
        //
        // ...which in the end is all about reducing state update sizes by
        // leaving out information that is relatively stable.  And reducing
        // state update sizes means we can fit more object updates in a single
        // message.  

        // For through all of the frames
        for( FrameState frame : state.frames ) {
        
            if( frame.time < lastFrameTime ) {
                continue;
            }
            lastFrameTime = frame.time;
 
            if( log.isDebugEnabled() ) {
                log.debug("** frame begin:" + frame.time);
            }
            
            frameTime.add(frame.time);
            
            space.beginFrame(lastFrameTime);
        
            ZoneKey center = grid.fromLongId(frame.columnId);
            
            // Make sure the local zone grid is updated so that 
            // zoneIds can be reinterpretted
            if( zoneIndex.setCenter(center, new ArrayList<ZoneKey>(), new ArrayList<ZoneKey>()) ) {
                // The zone index has changed centers and so any zoneIds
                // now mean something different.  However, we can only update
                // the ones we get state changes for because the ZoneKeys for
                // an object may not have actually changed with the grid change.
            }
             
            for( ObjectState objectState : frame.states ) {
 
                SharedObject so;
            
                if( objectState.realId != null ) {
                    so = space.getObject(objectState.networkId, objectState.realId);
                } else {
                    so = space.getObject(objectState.networkId);
                    if( so == null ) {
                        // This should be the sign of a problem, I think.
                        // Either we are getting updates for an object we've never
                        // seen a realId for (how could it happen if we never ACKed
                        // a baseline with the realID.) or we are receiving updates
                        // for an object we have deleted... apparently too soon.
                        // I'm going to print an error to see if it shows up... could
                        // be an exception, too, but I want to see if it's relatively
                        // normal or not first.
                        log.warn("********* Network ID lookup returned null.  State:" + objectState
                                 + "  messageId:" + state.messageId );
                        // I'm seeing these in an application with lots of objects.  The objectState
                        // seems to be empty, though.
                        continue;
                    }                
                }
 
                // Resolve the zoneId
                
                // Apply the changes
                if( so.applyNetworkState(frame.time, objectState, zoneIndex) ) {
                    // The object changed for real... we can notify any listeners
                    // to update state, etc.  SharedObjectSpace will do that for us.
                    
                    // If the object was removed for real then we need to remove
                    // it from the space, also.
                    if( so.isFullyMarkedRemoved() ) {
                        // The object is marked removed in both current and baseline
                        // so no reason to track it anymore.
                        space.removeObject(so);
                    } 
                }
                 
            }
            
            space.endFrame();        
        }
        
    }

    protected void processAcks( IntRange[] acked ) {    
        // The server has acknowledged a certain number of our ACKs and
        // those represent the new baseline.  So we need to apply them to our
        // local state.  We are guaranteed to have _ALL_ of these because we
        // were the one who told the server that we had them... if we don't
        // have them it's only because the server has sent us redundant ACKs.
        // This should always be the case.  Even if we missed messages it
        // doesn't matter because the server keeps sending the same values
        // until we have processed them for real.  In other words,
        // the server only stops sending a particular ID if we have
        // received that message, ACKed it, the server got the ACK, the server
        // included it in ANOTHER message and we ACKed that one too.
        for( IntRange range : acked ) {
            int min = range.getMinValue();
            int max = range.getMaxValue();
            for( int ackedId = min; ackedId <= max; ackedId++ ) {        
                SentState sentState = ackReceivedState(ackedId);
            
                // It is normal that we might not have a sent state anymore
                // because we might have previously ack'ed our ack.  The server
                // keeps sending it until we claim we have it so we may get
                // it multiple times while the server waits for our first
                // ACK. 
                if( sentState == null ) {
                    // Totally normal.  See above.  We may see the same acked
                    // ID hundreds of times if there is a message lag for our
                    // responses to the server.
                    continue;
                }
                    
                List<FrameState> old = sentState.frames;  
                if( old != null ) {
                    if( log.isDebugEnabled() ) {
                        log.debug("Updating baseline for message:" + ackedId );
                    }                
                    space.updateBaseline(old);
                }
            }
        }
    }        

    /**
     *  Called when we receive our own ACK back so that we can
     *  update the baseline with our "known good" state.
     */
    protected SentState ackReceivedState( int messageId ) {
    
        if( receivedStates.isEmpty() )
            return null;

        // Note: the only reason a messageId shows up here is
        // because we've told the server we already have it.  So
        // it's 100% guaranteed to be here unless we've already
        // processed it.  No exceptions.
        // We are safe to purge the old ones because the server
        // always sends them in order.                               

        // Scan forward through the list, removing the stale
        // SentStates.
        for( Iterator<SentState> it = receivedStates.values().iterator(); it.hasNext(); ) {
            SentState state = it.next();
            if( state.messageId < messageId ) { //state.isBefore(messageId) ) {
                // Remove it and skip it... 
                it.remove();
log.warn("Skipping state:" + state + " for messageId:" + messageId);                
                continue;
            }
            
            if( state.messageId == messageId ) {
                // We found it
                it.remove();
                return state;
            }
            
            // Else it is a state that we've already processed at some
            // earlier time as any next message will be after us
            return null;            
        }
        
        return null;
    }
         
}


