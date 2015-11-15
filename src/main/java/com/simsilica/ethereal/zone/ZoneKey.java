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

import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;


/**
 *  A unique key for a particular zone in space.  This is slightly
 *  beyond a location because it also has a reference to its 'owning'
 *  grid and thus can be used in a hashmap that manages multiple grid
 *  sizes. (Be sure to reuse ZoneGrid instances for == comparison.)
 *
 *  @author    Paul Speed
 */
public class ZoneKey {

    public ZoneGrid grid;
    public int x;
    public int y;
    public int z;    
    public Vec3i origin;
        
    public ZoneKey( ZoneGrid grid, int x, int y, int z ) {
        this.grid = grid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.origin = grid.zoneToWorld(x, y, z);
    }
 
    public Vec3d toWorld( Vec3d relative ) {
        return toWorld(relative, new Vec3d());
    }

    public Vec3d toWorld( Vec3d relative, Vec3d store ) {
        store.x = origin.x + relative.x;   
        store.y = origin.y + relative.y;   
        store.z = origin.z + relative.z;
        return store;   
    }
 
    public Vec3d toLocal( Vec3d world ) {
        return toLocal(world, new Vec3d());
    }
    
    public Vec3d toLocal( Vec3d world, Vec3d store ) {
        store.x = world.x - origin.x;   
        store.y = world.y - origin.y;   
        store.z = world.z - origin.z;
        return store;   
    }

    public long toLongId() {
        return grid.toLongId(this);
    }
 
    @Override
    public boolean equals( Object o ) {
        if( o == this )
            return true;
        if( o == null || o.getClass() != getClass() )
            return false;
        ZoneKey other = (ZoneKey)o;
        return other.x == x && other.y == y && other.z == z && other.grid == grid;
    }
        
    @Override
    public int hashCode() {
        int hash = 37;
        hash += 37 * hash + x;
        hash += 37 * hash + y;
        hash += 37 * hash + z;
        return hash;
    }
        
    @Override
    public String toString() {
        return x + ":" + y + ":" + z;
    }               
}
