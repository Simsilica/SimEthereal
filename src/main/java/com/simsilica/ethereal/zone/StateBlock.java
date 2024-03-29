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

package com.simsilica.ethereal.zone;

import java.util.ArrayList;
import java.util.List;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;


/**
 *
 *
 *  @author    Paul Speed
 */
public class StateBlock {
    private final long time;
    private final ZoneKey zone;
    private List<StateEntry> updates;
    private List<Long> removes;
    private List<Long> warps;
 
    public StateBlock( long time, ZoneKey zone ) {
        this.time = time;
        this.zone = zone;
    }

    public ZoneKey getZone() {
        return zone;
    }

    public boolean isEmpty() {
        return updates == null && removes == null && warps == null;
    }
    
    public void addUpdate( Long parent, Long entity, Vec3d pos, Quatd rot ) {
        if( updates == null ) {
            updates = new ArrayList<>();
        }
        updates.add(new StateEntry(parent, entity, pos, rot));
    }
    
    public void removeEntity( Long entity ) {
        if( removes == null ) {
            removes = new ArrayList<>();
        }
        removes.add(entity);
    }
    
    public void addWarp( Long parent, Long entity ) {
        if( warps == null ) {
            warps = new ArrayList<>();
        }
        // Technically we probably only care about entities with
        // no parent... but what if a 'self' was attached as a child to
        // some ridable object?  So we'll track them all.
        warps.add(entity);
    } 
   
    public long getTime() {
        return time;        
    }
    
    public List<StateEntry> getUpdates() {
        return updates;
    }
 
    public List<Long> getRemovals() {
        return removes;
    }

    public List<Long> getWarps() {
        return warps;
    }

    @Override   
    public String toString() {
        StringBuilder sb = new StringBuilder("StateBlock[time=" + time + ", zone=" + zone);
        if( updates != null ) {
            sb.append( ", updates=" + updates );
        }
        if( removes != null ) {
            sb.append( ", removes=" + removes );
        }
        sb.append( "]" );
        return sb.toString();
    }
      
    public static class StateEntry {
        private final Long parent;
        private final Long entity;
        private final Vec3d pos;
        private final Quatd rot;
        
        public StateEntry( Long parent, Long entity, Vec3d pos, Quatd rot ) {
            this.parent = parent;
            this.entity = entity;
            this.pos = pos;
            this.rot = rot;
        }
        
        public Long getParent() {
            return parent;
        }
        
        public Long getEntity() {
            return entity;
        }
        
        public Vec3d getPosition() {
            return pos;
        }
        
        public Quatd getRotation() {
            return rot;
        }
 
        @Override       
        public String toString() {
            return "StateEntry[" + (parent != null ? (parent + ", ") : "") + entity + ", " + pos + ", " + rot + "]";
        }
    }
}
