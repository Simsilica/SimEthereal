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

import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.ethereal.zone.ZoneGrid;
import com.simsilica.ethereal.zone.ZoneKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 *  Keeps track of the local zone space around the player to
 *  provide smaller zoneId values for particular ZoneKeys.  This
 *  also serves as a functional limit to what a particular client
 *  can see.
 *
 *  @author    Paul Speed
 */
public class LocalZoneIndex {
 
    private ZoneGrid grid;
    private int xExtent;
    private int xSize; 
    private int yExtent;
    private int ySize; 
    private int zExtent;
    private int zSize;
    private final int minZoneId = 1;
     
    private ZoneKey center;
    private ZoneKey[] keyIndex;
    private final Set<ZoneKey> keySet = new HashSet<>();
    
    public LocalZoneIndex( ZoneGrid grid, int gridRadius ) {    
        this(grid, gridRadius, gridRadius, gridRadius);
    }

    public LocalZoneIndex( ZoneGrid grid, Vec3i zoneExtents ) {    
        this(grid, zoneExtents.x, zoneExtents.y, zoneExtents.z );
    }
    
    public LocalZoneIndex( ZoneGrid grid, int xRadius, int yRadius, int zRadius ) {
        this.grid = grid;
        
        // Check to see if any of the grid dimensions are flat and do
        // similar to our index radius
        if( grid.getZoneSize().x == 0 ) {
            xRadius = 0;
        }
        if( grid.getZoneSize().y == 0 ) {
            yRadius = 0;
        }
        if( grid.getZoneSize().z == 0 ) {
            zRadius = 0;
        }
        
        this.xExtent = xRadius;
        this.yExtent = yRadius;
        this.zExtent = zRadius;
        xSize = xRadius * 2 + 1;
        ySize = yRadius * 2 + 1;
        zSize = zRadius * 2 + 1;
        keyIndex = new ZoneKey[xSize * ySize * zSize];
    }
 
    public int getIndexSize() {
        return keyIndex.length; 
    }

    public int getMinimumZoneId() {
        return minZoneId;
    }
    
    public ZoneGrid getGrid() {
        return grid;
    }
 
    public ZoneKey getZone( int zoneId ) {
        return getZone(zoneId, null);
    }

    public ZoneKey getZone( int zoneId, ZoneKey defaultValue ) {
        if( zoneId <= 0 ) {
            return defaultValue;
        }
        if( center == null ) {
            return defaultValue;
        }
        int index = zoneId - minZoneId;
        if( index >= keyIndex.length ) {
            throw new IllegalArgumentException("ZoneID out of bounds:" + zoneId);
        }
        return keyIndex[index];
    }
    
    public int getZoneId( ZoneKey zone ) {
        if( center == null ) {
            return -1;
        }
        
        int xBase = center.x - xExtent;
        int yBase = center.y - yExtent;
        int zBase = center.z - zExtent;
        int x = zone.x - xBase;
        int y = zone.y - yBase;
        int z = zone.z - zBase;
        
        return minZoneId + z * (xSize * ySize) + y * ySize + x;   
    }
        
    public ZoneKey getCenter() {
        return center;
    }
        
    public boolean setCenter( Vec3d pos, List<ZoneKey> entered, List<ZoneKey> exited ) {
        ZoneKey key = grid.worldToKey(pos.x, pos.y, pos.z);
        return setCenter(key, entered, exited);        
    }         
        
    public boolean setCenter( ZoneKey center, List<ZoneKey> entered, List<ZoneKey> exited ) {
        if( Objects.equals(this.center, center) ) {
            return false;
        }
 
        exited.clear();
        entered.clear();
        if( this.center != null ) {
            // Seed it with all of them... we'll remove the
            // ones 
            exited.addAll(keySet);
        }
 
        this.center = center;
        int index = 0;
        for( int z = center.z - zExtent; z <= center.z + zExtent; z++ ) {
            for( int y = center.y - yExtent; y <= center.y + yExtent; y++ ) {
                for( int x = center.x - xExtent; x <= center.x + xExtent; x++ ) {
                
                    ZoneKey k = new ZoneKey(grid, x, y, z);
                    keyIndex[index] = k;
                    index++;
                    
                    if( keySet.add(k) ) {
                        // It's a new zone
                        entered.add(k);
                    }
                }
            }
        }
        keySet.clear();
        keySet.addAll(Arrays.asList(keyIndex));

        // Clean out the exited array of stuff that wasn't actually
        // exited
        for( Iterator<ZoneKey> it = exited.iterator(); it.hasNext(); ) {
            if( keySet.contains(it.next()) ) {
                it.remove();
            }
        }
        
        return true;
    }  
 
    public static void main( String... args ) {
        ZoneGrid grid = new ZoneGrid(32, 32, 32);    
        LocalZoneIndex zones = new LocalZoneIndex(grid, 1);
        List<ZoneKey> entered = new ArrayList<>();
        List<ZoneKey> exited = new ArrayList<>();
        
        ZoneKey center = new ZoneKey(grid, 2, 2, 2);
        zones.setCenter(center, entered, exited);
          
        System.out.println("Entered:" + entered);
        System.out.println("Exited:" + exited);
       
        for( int i = 0; i < zones.keyIndex.length; i++ ) {
            ZoneKey k = zones.keyIndex[i]; 
            System.out.println("  key[" + i + "] = " + k);
            int zoneId = zones.getZoneId(k); 
            System.out.println("      id:" + zoneId + "   relookup:" + zones.getZone(zoneId)); 
        }
        
        center = new ZoneKey(grid, 3, 2, 2); 
        zones.setCenter(center, entered, exited);
        System.out.println("Entered:" + entered);
        System.out.println("Exited:" + exited);
    }
         
}


