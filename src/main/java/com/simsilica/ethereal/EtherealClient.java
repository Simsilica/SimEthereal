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

import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;
import com.jme3.network.util.ObjectMessageDelegator;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.bits.QuatBits;
import com.simsilica.mathd.bits.Vec3Bits;
import com.simsilica.ethereal.net.ObjectStateProtocol;
import com.simsilica.ethereal.net.StateReceiver;
import com.simsilica.ethereal.zone.ZoneGrid;
import com.simsilica.ethereal.zone.ZoneManager;


/**
 *
 *
 *  @author    Paul Speed
 */
public class EtherealClient extends AbstractClientService {

    private ZoneGrid grid;
    private StateReceiver stateReceiver;
    private ObjectMessageDelegator delegator;
    private ObjectStateProtocol objectProtocol;
    private Vec3i clientZoneExtents;
    private SharedObjectSpace space;

    public EtherealClient() {
        this(new ObjectStateProtocol(8, 64, new Vec3Bits(-10, 42, 16), new QuatBits(12)),
             new ZoneGrid(32), new Vec3i(1, 1, 1));
    }

    public EtherealClient( ZoneGrid grid ) {
        this(new ObjectStateProtocol(8, 64, new Vec3Bits(-10, 42, 16), new QuatBits(12)),
             grid, new Vec3i(1, 1, 1));
    }

    public EtherealClient( ObjectStateProtocol objectProtocol, ZoneGrid grid, Vec3i clientZoneExtents ) {
        this.objectProtocol = objectProtocol;
        this.grid = grid;
        this.clientZoneExtents = clientZoneExtents;
    }

    public TimeSource getTimeSource() {
        return stateReceiver.getTimeSource();
    }
 
    public void addObjectListener( SharedObjectListener l ) {
        space.addObjectListener(l);
    }
    
    public void removeObjectListener( SharedObjectListener l ) {
        space.removeObjectListener(l);
    }
    
    @Override
    protected void onInitialize( ClientServiceManager s ) {
 
        this.space = new SharedObjectSpace(objectProtocol);
        this.stateReceiver = new StateReceiver(getClient(), new LocalZoneIndex(grid, clientZoneExtents), 
                                               space);   
        this.delegator = new ObjectMessageDelegator(stateReceiver, true);
        getClient().addMessageListener(delegator, delegator.getMessageTypes());
    }

    @Override
    public void terminate( ClientServiceManager serviceManager ) {
        getClient().removeMessageListener(delegator, delegator.getMessageTypes());                   
    }
    
}

    
