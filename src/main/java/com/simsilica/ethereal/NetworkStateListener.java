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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.HostedConnection;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.Vec3d;

import com.simsilica.ethereal.net.ClientStateMessage;
import com.simsilica.ethereal.net.SentState;
import com.simsilica.ethereal.net.StateWriter;
import com.simsilica.ethereal.zone.StateBlock;
import com.simsilica.ethereal.zone.StateBlock.StateEntry;
import com.simsilica.ethereal.zone.StateListener;
import com.simsilica.ethereal.zone.ZoneGrid;
import com.simsilica.ethereal.zone.ZoneKey;
import com.simsilica.util.BufferedHashSet;


/**
 *  A server-side per-player state listener that streams shared space changes
 *  to the client based on the current local zones.
 *
 *  @author    Paul Speed
 */
public class NetworkStateListener implements StateListener {

    static Logger log = LoggerFactory.getLogger(NetworkStateListener.class);

    public static final String ATTRIBUTE_KEY = "networkStateListener";

    private EtherealHost host;
    private HostedConnection conn; 
    private LocalZoneIndex zoneIndex;
    private IdIndex idIndex;
    private SharedObjectSpace space;

    private BufferedHashSet<Long> activeIds = new BufferedHashSet<>();

    /**
     *  Keeps track of who 'we' are for proper central zone setting
     */
    private Long self;
    private final Vec3d selfPosition = new Vec3d();
   
    private boolean zonesChanged = false;    
    private final List<ZoneKey> entered = new ArrayList<>();
    private final List<ZoneKey> exited = new ArrayList<>();    
 
    /**
     *  Buffers and splits message to utilize MTU efficiently.
     */ 
    private StateWriter stateWriter;
 
    /**
     *  Keeps track of the ACKs we've seen from the client but haven't yet
     *  confirmed it's received our double-ACK back.  We queue them as we
     *  receive them and only process them inline with sending state.
     */   
    private ConcurrentLinkedQueue<ClientStateMessage> acked = new ConcurrentLinkedQueue<>();
 
 
    // Track average ping time
    private long pingTime = 0;
    private long windowMax = 100;
    private long windowSize = 0;

    private ConnectionStats stats = new ConnectionStats();
    
    public NetworkStateListener( EtherealHost host, HostedConnection conn, ZoneGrid grid, int zoneRadius ) {
        this(host, conn, new LocalZoneIndex(grid, zoneRadius), new IdIndex(10));
    }

    public NetworkStateListener( EtherealHost host, HostedConnection conn, ZoneGrid grid, Vec3i zoneExtents ) {
        this(host, conn, new LocalZoneIndex(grid, zoneExtents), new IdIndex(10));
    }
    
    public NetworkStateListener( EtherealHost host, HostedConnection conn, LocalZoneIndex zoneIndex, IdIndex idIndex ) {
        this.host = host;
        this.conn = conn;                                 
        this.zoneIndex = zoneIndex;
        this.idIndex = idIndex;
        this.space = new SharedObjectSpace(host.getObjectProtocol());
        this.stateWriter = new StateWriter(conn, host.getObjectProtocol(), host.getTimeSource(), stats);        
    }

    public void setSelf( Long self, Vec3d startingPosition ) {
        this.self = self;
        this.selfPosition.set(startingPosition);
    }
    
    public Long getSelf() {
        return self;
    }

    /**
     *  Returns the set of all active IDs at this point in time.
     */
    public Set<Long> getActiveIds() {
        return activeIds.getSnapshot();
    }

    public ConnectionStats getConnectionStats() {
        return stats;
    }

    /**
     *  Sets the desired maximum message size.  Typically the ideal situation
     *  would be to keep this under the MTU of the connection.  By default,
     *  StateWriter estimates this as 1500 bytes.  It can vary and there is no
     *  direct way to know.  This setting is provided for applications that want
     *  to try changing the max message size for connections that seem to be dropping
     *  a lot of packets.  Making this size smaller may help fall under that particular
     *  connections MTU and thus make it more likely that a single message makes it
     *  through since it won't need to be split by the network connection.
     *
     *  <p>Set this to a really large value to avoid ever splitting a frame into multiple
     *  messages.  This may make the actual transport of the message more likely to fail
     *  over disadvantaged comms and it may make the message take a little longer to
     *  reach the endpoint.</p>
     *
     *  <p>Notes on MTU and why this setting exists: for a particular connection there
     *  is a maximum message transmit unit size over which a UDP message will be split and
     *  reassembled.  If any of the parts of that UDP message fail to arrive then the
     *  message is discarded completely.  SimEthereal attempts to keep its own messages
     *  under a theoretical MTU size (by default 1500) in order to avoid this splitting.
     *  We trade calculation time and complexity for (hopefully) a higher likelihood that
     *  our messages make it there and in a timely manner (split UDP packets are only
     *  as fast as their slowest part, after all).  Even if one of our self-split messages
     *  makes it then at least you get a partial frame update.  If part of the UDP fails
     *  to make it then you get nothing.</p>   
     */
    public void setMaxMessageSize( int max ) {
        stateWriter.setMaxMessageSize(max);
    }
    
    public int getMaxMessageSize() {
        return stateWriter.getMaxMessageSize();
    }
    
    @Override
    public boolean hasChangedZones() {
        return zonesChanged;
    }

    @Override
    public List<ZoneKey> getEnteredZones() {
        return entered;
    }

    @Override
    public List<ZoneKey> getExitedZones() {
        return exited;
    }
 
    /**
     *  Called when client state is received.  Autowired through the 
     *  SessionDataDelegator setup in EtherealHost.
     */   
    protected void postResponse( ClientStateMessage m ) {
 
        // ClientStateMessage's getTime() will be returning the ObjectStateMessage's
        // timestamp which is in 'time source' time.  So we need to know received time
        // in 'time source' time also.
        long timestamp = host.getTimeSource().getTime();   
        m.resetReceivedTime(timestamp);
        
        long ping = m.getReceivedTime() - m.getTime();
        stats.addPingTime(ping);

        long newPing = (ping + pingTime * windowSize) / (windowSize + 1);
        if( windowSize < windowMax ) {
            windowSize++;
        }
        long delta = Math.abs(newPing - pingTime);
        pingTime = newPing;
        if( delta > 10000000 ) { // 10 ms change            
            if( log.isDebugEnabled() ) {
                log.debug("********** " + conn + "  avg ping:" + pingTime + "  " + (pingTime / 1000000.0) + " ms");
            }
        }
        
        if( log.isTraceEnabled() ) {
            log.trace("received message:" + m.getId());
        }        
        acked.add(m);
    }     

    @Override
    public void beginFrameBlock() {
    }

    @Override
    public void endFrameBlock() {
        try {
            // Flush any lingering data in the state writer... We always
            // try to keep blocks as contiguous as possible. 
            stateWriter.flush();
        } catch( IOException e ) {
            throw new RuntimeException("Error flushing", e);
        }
    }

    @Override
    public void beginFrame( long time ) {
    
        if( log.isTraceEnabled() ) {
            log.trace(self + ":beginFrame(" + time + ") selfPosition:" + selfPosition);
        }
    
        // If the zones changed last frame then clear the buffers
        if( zonesChanged ) {    
            entered.clear();
            exited.clear();
            zonesChanged = false;
        }        
    } 

    @Override
    public void endFrame( long time ) {
    
        if( log.isTraceEnabled() ) {
            log.trace(self + ":endFrame(" + time + ") selfPosition:" + selfPosition);
            log.trace("endFrame() acked queue size:" + acked.size());
        }
        
        // See if we've gotten any ACKs to add to our ACK header
        ClientStateMessage ackedMsg;
        while( (ackedMsg = acked.poll()) != null ) {
 
            stats.incrementAcks();
                   
            // Acknowledge any state acks we've recieved up until now.
            SentState sentState = stateWriter.ackSentState(ackedMsg.getId());
            if( sentState == null ) {
                stats.incrementAckMisses();
                continue;
            }
            
            // This ACK has now become part of our base state we send to clients
            // so it's now OK to update our baseline.  If the client receives a message
            // from us then it will also receive the double-ack and know to update its
            // own baseline as well.
            //
            // Think of it this way, sometime in the past we sent 'version 1.4' of some
            // object.  We send diffs against 1.0 because we haven't heard back from
            // the client yet.  Once we hear from the client that 'yes, I've seen 1.4'
            // we update our baseline to 1.4 and now send diffs against that... but as
            // part of that state, we also include the 'yes, I've seen 1.4' that we got 
            // from the client.  So the state is completely self contained and the client
            // that we already know saw the 1.4 update (because it told us) now knows that
            // we know it knows an can update its baseline accordingly.
            //
            // (Later when we get an ACK for one of these new messages, we'll know it's also
            //  seen the old 1.4 double-ACK and we can remove it from our ACK header.)
            
            // So, update our baseline
            space.updateBaseline(sentState.frames);
        }
        
        // Buffer the state out through the state writer.
        try {
            ZoneKey center = zoneIndex.getCenter();
            stateWriter.startFrame(time, center);
            
            for( Iterator<SharedObject> it = space.objects().iterator(); it.hasNext(); ) {
                SharedObject so = it.next();
 
                // If this object wasn't updated this frame then that
                // means it probably fell out of any active zones.
                // Meaning, we moved and that zone's objects are no longer
                // in our view... and this object doesn't cross a border.
                //
                // 2018-12-15: I belive this approach was taken because an
                // object can be in multiple zones at once... but will generally
                // only have been updated one time.  So if the object was updated
                // at all then it must be in at least one of the zones in our view.
                // It also avoids any sort of chick-and-egg style problems of 
                // having checked zone membership at the wrong time or whatever.
                // It has a major downside in that we can't support "no-update" objects.
                // For example, a physics engine might put an object to sleep and
                // stop sending us updates for a while... in the current code we'd
                // consider that a removal when it's really not.
                // 
                // The real underlying question is if we want to support objects
                // that receive no external updates.  I kind of feel like we do.
                // Besides, I think in the current situation we might leak some stuff
                // in that it looks outwardly like we've removed the object while internally
                // we are still tracking it in the global structures.  
                //
                // Here we only care about the version and not whether it actually
                // moved or not.  So as long as the zones update the version of objects
                // that aren't moving we are clear here.  (And we avoid redoing processing
                // per-client that's perhaps best done globally.) 
                if( !so.isMarkedRemoved() && so.getVersion() < time ) {
                    if( log.isDebugEnabled() ) {
                        log.debug("Object no longer in active zones, marking removed:" + so.getEntityId() );
                    }
                    so.markRemoved(time);
                }
                    
                stateWriter.addState(so.getDelta());
                
                if( so.isFullyMarkedRemoved() ) {
                    if( log.isDebugEnabled() ) {
                        log.debug("State entry is removed for:" + so.getEntityId() ); //+ "  zone:" + e.zone + "  parent:" + e.parentId );
                    }
                    it.remove();
                    idIndex.retireId(so.getNetworkId());
                    
                    // Dear future-Paul, if you are here because you are trying
                    // to figure out why a removed object didn't actually get
                    // removed from the activeIds (and thus not removed on the client)
                    // note that if there are no other moving objects in the space
                    // the frame updates stop happening.  There are no frame times
                    // and so StateCollector stops calling us at all.
                    // I've left it this way because it is extremely unreastic
                    // that NO objects would be moving anywhere in the space...
                    // especially since typically the player itself is an object
                    // moving in the space.  Fixing it is non-trivial and incurs
                    // a small overhead all the time just to support this unlikely
                    // use-case.  But hopefully this comment will save you a couple
                    // hours of debug logging if you forget why it's like that.
                    activeIds.remove(so.getEntityId());
                } else {
                    activeIds.add(so.getEntityId());
                } 
                
            } 
        } catch( IOException e ) {
            throw new RuntimeException("Error writing state", e);
        }
 
        if( self != null && selfPosition != null ) {
        
            // Now that all of the frame events have been 
            // handled, lets see if our zone is changed.
            // We can't do this as part of normal state processing because
            // we use the zoneIds need to stay consistent during processing.
            if( zoneIndex.setCenter(selfPosition, entered, exited) ) {
                zonesChanged = true;
            }
        }
 
        activeIds.commit();                                
    }


    @Override
    public void stateChanged( StateBlock b ) {
 
        if( log.isTraceEnabled() ) {
            log.trace(self + ":stateChanged(" + b + ")");
        }

        // StateBlocks are per zone but contain all of the 
        // objects for a particular time step in that zone.
    
        long time = b.getTime();
        ZoneKey zone = b.getZone();
        
        int zoneId = zoneIndex.getZoneId(zone);
        if( zoneId <= 0 ) {
            System.err.println("No zone ID for changed zone:" + zone + "  received:" + zoneId);
        }
        
        if( log.isTraceEnabled() ) {
            log.trace("stateChanged() zone:" + zone 
                        + " updates:" + b.getUpdates() 
                        + " removals:" + b.getRemovals());
        }        
        
        if( b.getUpdates() != null )  {        
            for( StateEntry e : b.getUpdates() ) {
            
                Vec3d pos = e.getPosition();
                Quatd rot = e.getRotation();
 
                int networkId = idIndex.getId(e.getEntity(), true);
                Long entityId = e.getEntity(); 
                Long parentId = e.getParent();
 
                // Get or create the shared object for the IDs.
                SharedObject so = space.getObject(networkId, entityId);
                if( so.updateState(time, zone, zoneId, parentId, pos, rot) ) {
                    // If this was 'us' then keep the position for later
                    if( Objects.equals(self, entityId) ) {
                        this.selfPosition.set(pos);
                    }
                }                 
            }                
        } else {
            if( log.isTraceEnabled() ) {
                log.trace(self + ":No updates");
            }
        }
        
        if( b.getRemovals() != null ) {
            for( Long e : b.getRemovals() ) {
                int networkId = idIndex.getId(e, false);
                if( networkId == -1 )
                    continue;
 
                SharedObject so = space.getObject(networkId);
                if( so == null ) {
                    continue;
                }

                if( log.isDebugEnabled() ) {
                    log.debug("StateBlock - Marking removed:" + e);
                }                
                so.markRemoved(time);
            }
        }             
    } 
}
