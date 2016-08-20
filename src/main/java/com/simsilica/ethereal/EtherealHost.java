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

import com.jme3.network.HostedConnection;
import com.jme3.network.serializing.Serializer;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.util.SessionDataDelegator;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.bits.QuatBits;
import com.simsilica.mathd.bits.Vec3Bits;
import com.simsilica.ethereal.net.ClientStateMessage;
import com.simsilica.ethereal.net.ObjectStateMessage;
import com.simsilica.ethereal.net.ObjectStateProtocol;
import com.simsilica.ethereal.zone.StateCollector;
import com.simsilica.ethereal.zone.StateListener;
import com.simsilica.ethereal.zone.ZoneGrid;
import com.simsilica.ethereal.zone.ZoneManager;
import java.util.Arrays;


/**
 *
 *
 *  @author    Paul Speed
 */
public class EtherealHost extends AbstractHostedConnectionService {

    private ZoneGrid grid;
    private ZoneManager zones;
    private StateCollector stateCollector;
    private ObjectStateProtocol objectProtocol;
    private Vec3i clientZoneExtents;
    private long stateCollectionInterval;

    private SessionDataDelegator delegator;

    public EtherealHost() {
        this(new ObjectStateProtocol(8, 64, new Vec3Bits(-10, 42, 16), new QuatBits(12)),
             new ZoneGrid(32), new Vec3i(1, 1, 1));
    }

    public EtherealHost( ZoneGrid grid ) {
        this(new ObjectStateProtocol(8, 64, new Vec3Bits(-10, 42, 16), new QuatBits(12)),
             grid, new Vec3i(1, 1, 1));
    }

    public EtherealHost( ObjectStateProtocol objectProtocol, ZoneGrid grid, Vec3i clientZoneExtents ) {
        super(false);
        this.objectProtocol = objectProtocol;
        this.grid = grid;
        this.clientZoneExtents = clientZoneExtents;
        
        Serializer.registerClasses(ClientStateMessage.class, ObjectStateMessage.class);        
    }

    public ZoneManager getZones() {
        return zones;
    }

    public void setObjectProtocol( ObjectStateProtocol objectProtocol ) {
        this.objectProtocol = objectProtocol;
    }
    
    public ObjectStateProtocol getObjectProtocol() {
        return objectProtocol;
    }

    public void addListener( StateListener l ) {
        stateCollector.addListener(l);
    }
    
    public void removeListener( StateListener l ) {
        stateCollector.removeListener(l);
    }

    /**
     *  Sets the state collection interval that the StateCollector will use
     *  to pull history from the ZoneManager and deliver it to the clients.
     *  By default this is 1/20th of a second, or 50,000,000 nanoseconds.
     */
    public void setStateCollectionInterval( long nanos ) {
        if( stateCollector != null ) {
            throw new RuntimeException("The state collection interval cannot be set once the service is initialized.");
        }
        this.stateCollectionInterval = nanos;
    }
    
    public long getStateCollectionInterval() {
        return stateCollectionInterval; 
    } 

    @Override
    protected void onInitialize( HostedServiceManager s ) {
        this.zones = new ZoneManager(grid);
        this.stateCollector = new StateCollector(zones, stateCollectionInterval);
        
        // A general listener for forwarding the messages
        // to the client-specific handler
        this.delegator = new SessionDataDelegator(NetworkStateListener.class, 
                                                  NetworkStateListener.ATTRIBUTE_KEY,
                                                  true);
        //System.out.println("network state message types:" + Arrays.asList(delegator.getMessageTypes()));
        getServer().addMessageListener(delegator, delegator.getMessageTypes());
        
    }

    @Override
    public void terminate(HostedServiceManager serviceManager) {
        getServer().removeMessageListener(delegator, delegator.getMessageTypes());
    }
    

    @Override
    public void start() {
        stateCollector.start();
    }
    
    @Override
    public void stop() {
        stateCollector.shutdown();
    }
 
    public NetworkStateListener getStateListener( HostedConnection hc ) {
        return hc.getAttribute(NetworkStateListener.ATTRIBUTE_KEY);
    }
    
    @Override
    public void startHostingOnConnection( HostedConnection hc ) {
        
        // See if we've already got one
        NetworkStateListener nsl = hc.getAttribute(NetworkStateListener.ATTRIBUTE_KEY);
        if( nsl != null ) {
            return;
        }
    
        nsl = new NetworkStateListener(this, hc, grid, 1);
        hc.setAttribute(NetworkStateListener.ATTRIBUTE_KEY, nsl);
        
        // Now add it to the state collector
        stateCollector.addListener(nsl);
        
        // FIXME/TODO: send the object protocol, grid configuration, etc.
        //             to the client to avoid accidental mismatches.    
    }

    /**
     *  Called during connection setup to tell SimEthereal which tracked object
     *  is the player and what their initial position is.  This allows the per-connection
     *  state listener to properly find the center zone for the player. 
     */
    public void setConnectionObject( HostedConnection hc, Long selfId, Vec3d initialPosition ) {
        getStateListener(hc).setSelf(selfId, initialPosition);
    } 

    @Override
    public void stopHostingOnConnection( HostedConnection hc ) {        
        NetworkStateListener nsl = hc.getAttribute(NetworkStateListener.ATTRIBUTE_KEY);
        if( nsl == null ) {
            return;
        }
        
        stateCollector.removeListener(nsl);
        hc.setAttribute(NetworkStateListener.ATTRIBUTE_KEY, null);
    }
}


