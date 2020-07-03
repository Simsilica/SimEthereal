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

import java.util.Objects;


/**
 *
 *
 *  @author    Paul Speed
 */
public class ObjectState implements Cloneable { 
    public int networkId;
    public int zoneId;
    public Long realId;
    public Long parentId;
    public long positionBits;
    public long rotationBits;
    
    public ObjectState() {
        this(0, null);
    }
    
    public ObjectState( int networkId ) {
        this(networkId, null);
    }
    
    public ObjectState( int networkId, Long realId ) {
        this.networkId = networkId;
        this.realId = realId;
        this.zoneId = -1;
        this.positionBits = -1;
        this.rotationBits = -1;
    }
    
    @Override
    public ObjectState clone() {
        try {
            return (ObjectState)super.clone();
        } catch( CloneNotSupportedException e ) {
            throw new RuntimeException("Should never happen", e);
        }
    }
 
    public void set( ObjectState state ) {
        this.networkId = state.networkId;
        this.zoneId = state.zoneId;
        this.realId = state.realId;
        this.parentId = state.parentId;
        this.positionBits = state.positionBits;
        this.rotationBits = state.rotationBits;
    }
 
    /**
     *  Returns true if this object is marked for removal at this 
     *  state.
     */
    public boolean isMarkedRemoved() {
        //return parentId == null && zoneId == 0;
        // I think now that we are implementing parentId 'for real' that
        // it's not right to clear it for removal.  -pspeed:2020-06-15
        return zoneId == 0;
    }
 
    public void markRemoved() {
        // I think this clearing of the parent ID is wrong in today's light.
        // But it is many years later and parentId was only used 'for real' 
        // now.  -pspeed:2020-06-15
        //this.parentId = null;
        this.zoneId = 0;
    }
 
    public ObjectState getDelta( ObjectState baseline ) {
        if( baseline == null ) {
            return clone();
        }

        ObjectState result = new ObjectState(networkId);
        
        if( zoneId != baseline.zoneId ) {
            result.zoneId = zoneId;
        }
        
        if( !Objects.equals(realId, baseline.realId) ) {
            result.realId = realId;   
        }
 
        if( !Objects.equals(parentId, baseline.parentId) ) {
            result.parentId = parentId;   
        }
 
        if( positionBits != baseline.positionBits ) {
            result.positionBits = positionBits;
        }

        if( rotationBits != baseline.rotationBits ) {
            result.rotationBits = rotationBits;
        }
                       
        return result;        
    }
    
    public void applyDelta( ObjectState delta ) {
 
        if( delta.zoneId != -1 ) {
            zoneId = delta.zoneId;
        }                 
        if( delta.parentId != null ) {
            parentId = delta.parentId;
        }
        if( delta.positionBits != -1 ) {
            positionBits = delta.positionBits;
        }                 
        if( delta.rotationBits != -1 ) {
            rotationBits = delta.rotationBits;
        }                 
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName());
        sb.append("[id=").append(networkId);
        if( realId != null )
            sb.append(", realId=").append(realId);
        if( parentId != null )
            sb.append(", parentId=").append(parentId);
        if( zoneId == 0 && parentId == null )
            sb.append(", REMOVED");
        else if( zoneId != -1 )
            sb.append(", zoneId=").append(zoneId);
        if( positionBits != -1 )
            sb.append(", positionBits=").append(Long.toHexString(positionBits));
        if( rotationBits != -1 )
            sb.append(", rotationBits=").append(Long.toHexString(rotationBits));
        sb.append("]");
        return sb.toString();
    }
}



