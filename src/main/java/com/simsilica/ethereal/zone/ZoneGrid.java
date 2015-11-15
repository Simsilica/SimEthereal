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
 *  Can translate to/from integer-based grid coordinates and world
 *  real coordinates.  Note: Zones are essentially 2 dimensional
 *  in the 'ground' plane, by JME convention: x, z
 *
 *  @author    Paul Speed
 */
public class ZoneGrid {

    private Vec3i zoneSize;
    
    public ZoneGrid( int zoneSize ) {
        this(new Vec3i(zoneSize, zoneSize, zoneSize));
    }
    
    public ZoneGrid( int xZoneSize, int yZoneSize, int zZoneSize ) {
        this(new Vec3i(xZoneSize, yZoneSize, zZoneSize));
    }
    
    public ZoneGrid( Vec3i sizes ) {
        this.zoneSize = sizes;
    }
    
    public Vec3i getZoneSize() {
        return zoneSize;
    }
    
    private int worldToZone( int i, int size ) {
        if( size == 0 ) {
            return 0;  // special case where the dimension is flattened
        }
        if( i < 0 ) {
            // Need to adjust so that, for example:
            // -32 to -1 is -1 instead of part -1 and part 0
            i = (i + 1) / size;
            return i - 1;
        } else {
            return i / size;
        }
    }

    private int worldToZone( double d, int size ) {
        return worldToZone((int)Math.floor(d), size);
    }

    public Vec3i worldToZone( double x, double y, double z ) {
        int i = worldToZone(x, zoneSize.x);
        int j = worldToZone(y, zoneSize.y);
        int k = worldToZone(z, zoneSize.z);
        return new Vec3i(i, j, k);
    }

    public Vec3i worldToZone( Vec3d world ) {
        int i = worldToZone(world.x, zoneSize.x);
        int j = worldToZone(world.y, zoneSize.y);
        int k = worldToZone(world.z, zoneSize.z);
        return new Vec3i(i, j, k);
    }
    
    private int zoneToWorld( int i, int size ) {
        return i * size;
    } 

    public Vec3i zoneToWorld( int x, int y, int z ) {
        int i = zoneToWorld(x, zoneSize.x);   
        int j = zoneToWorld(y, zoneSize.y);
        int k = zoneToWorld(z, zoneSize.z);
        return new Vec3i(i, j, k);
    }   

    public ZoneKey worldToKey( Vec3d pos ) {
        return worldToKey(pos.x, pos.y, pos.z);
    }        

    public ZoneKey worldToKey( double x, double y, double z ) {
        int i = worldToZone(x, zoneSize.x);
        int j = worldToZone(y, zoneSize.y);
        int k = worldToZone(z, zoneSize.z);
        return new ZoneKey(this, i, j, k);   
    }        
 
    public Vec3i zoneToWorld( ZoneKey key ) {
        return key.origin.clone();
    }
 
    /**
     *  Converts the x, y, z to a single long by masking 
     *  each value and bit shifting.  This effectively means
     *  the zone x,y,z values are limited to 2^21 (+/- 2^20) but that's probably
     *  ok for all reasonable implementations.  It's over a million zones in
     *  every direction.
     */
    public long toLongId( ZoneKey key ) {
        long x = key.x & 0x1fffffL;
        long y = key.y & 0x1fffffL;
        long z = key.z & 0x1fffffL;
        return (x << 42) | (y << 21) | z;
    }
 
    public ZoneKey fromLongId( long id ) {
        int z = (int)(id & 0x1fffffL);
        if( (z & 0x100000) != 0 ) {
            // Sign extend
            z = z | 0xfff00000;
        }
        int y = (int)((id >>> 21) & 0x1fffffL);
        if( (y & 0x100000) != 0 ) {
            // Sign extend
            y = y | 0xfff00000;
        }
        int x = (int)((id >>> 42) & 0x1fffffL);
        if( (x & 0x100000) != 0 ) {
            // Sign extend
            x = x | 0xfff00000;
        }
        
        return new ZoneKey(this, x, y, z);       
    }
 
    public static void main( String... args ) {
        ZoneGrid grid = new ZoneGrid(32);
 
        int maxValue = 0xfffff;
        int minValue = -0xfffff;
    
        ZoneKey[] test = new ZoneKey[] {
                new ZoneKey(grid, 0, 0, 0),
                new ZoneKey(grid, 1, 1, 1),
                new ZoneKey(grid, 1, -1, 0),
                new ZoneKey(grid, 100, 100, 100),
                new ZoneKey(grid, -1, -1, -1),
                new ZoneKey(grid, -100, -100, -100),
                new ZoneKey(grid, maxValue, maxValue, maxValue),
                new ZoneKey(grid, minValue, minValue, minValue)
            };
            
        for( ZoneKey k : test ) {
            System.out.println("Key:" + k);
            long id = grid.toLongId(k);
            System.out.println("    id:" + id + "  " + Long.toHexString(id));
            System.out.println("    reverse:" + grid.fromLongId(id));
        }            
    }
 
    
    @Override
    public String toString() {
        return "Grid[" + zoneSize + "]";
    }       
}


