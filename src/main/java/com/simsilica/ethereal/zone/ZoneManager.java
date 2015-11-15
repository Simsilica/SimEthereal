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

import com.simsilica.mathd.AaBBox;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 *  Manages all of the zones for all of the players.  This
 *  is used to update the status of active objects.  The zone 
 *  manager then makes sure that information gets to where it needs to go.
 *
 *  @author    Paul Speed
 */
public class ZoneManager {
    static Logger log = LoggerFactory.getLogger(ZoneManager.class);
 
    private ZoneGrid grid;   
    private final Map<Long, ZoneRange> index = new HashMap<>();

    private long updateTime = -1;

    // The active zones
    private final Map<ZoneKey,Zone> zones = new ConcurrentHashMap<>(16, 0.75f, 2);
    private final Lock historyLock = new ReentrantLock();

    private final Set<Long> pendingRemoval = new HashSet<>();

    // Keeps track of the frame times for a certain
    // block of history.
    private boolean collectHistory = false;
    private int historyBacklog;
    private long[] historyIndex;
    private int historySize = 0;
    
    // For perf tracking
    private long nextLog = 0;
    private long updateStartTime = 0;
    private long totalUpdateTime = 0; 

    public ZoneManager( int zoneSize ) {
        this(new ZoneGrid(zoneSize));
    }
    
    public ZoneManager( ZoneGrid grid ) {
        this(grid, 12);
    }
    
    public ZoneManager( ZoneGrid grid, int historyBacklog ) {
        this.grid = grid;
        this.historyBacklog = historyBacklog;
        this.historyIndex = new long[historyBacklog];
    }

    public ZoneGrid getGrid() {
        return grid;
    }

    /**
     *  Set to true if history should be collected or false if object updates
     *  should be ignored.  Generally, the StateCollector will turn history collection    
     *  on when it is ready to start periodically purging history.  Otherwise there
     *  is a risk that the buffers will overflow before the first purge is done. 
     */
    public void setCollectHistory( boolean b ) {
        this.collectHistory = b;
    }
    
    public boolean getCollectHistory() {
        return collectHistory;
    }

    protected ZoneRange getZoneRange( Long id, boolean create ) {
        ZoneRange result = index.get(id);
        if( result == null && create ) {
            result = new ZoneRange();
            index.put(id, result);
        }
        return result;
    }

long frameCounter = 0;
long nextFrameTime = System.nanoTime() + 1000000000L;

//long lastTime = 0;

    public void beginUpdate( long time ) {
        updateStartTime = System.nanoTime();
        updateTime = time;
        
/*if( lastTime > 0 ) {
    long delta = time - lastTime;
    System.out.println("zone time delta frame[" + timeCheckCounter + "]:" + ((delta)/1000000.0) + " ms");
}
lastTime = time;*/
        
frameCounter++;        
if( updateStartTime > nextFrameTime ) {
    if( frameCounter < 60 ) {
        System.out.println("zone update underflow FPS:" + frameCounter);
    } else if( frameCounter > 70 ) {
        System.out.println("zone update overflow FPS:" + frameCounter);
    }
    //System.out.println("zone update FPS:" + frameCounter);
    frameCounter = 0;
    nextFrameTime = System.nanoTime() + 1000000000L;
}
        
        for( Zone z : zones.values() ) {
            z.beginUpdate(time);
        }
        
        // Deactivate any pending entities
        for( Long id : pendingRemoval ) {
            if( log.isDebugEnabled() ) {
                log.debug("ZONE:  --- delayed deactivation:" + id);
            }            
            ZoneRange range = index.remove(id);
            if( log.isDebugEnabled() ) {
                log.debug("range:" + range);
            }            
            if( range != null ) {
                range.leave(id);
            }
        }
        pendingRemoval.clear();                            
    }
 
    public void updateEntity( Long id, boolean active, Vec3d p, Quatd orientation, AaBBox bounds ) {
    
        Vec3i minZone = grid.worldToZone(bounds.getMin()); 
        Vec3i maxZone = grid.worldToZone(bounds.getMax()); 
 
        ZoneRange info = getZoneRange(id, true);
        if( !minZone.equals(info.min) || !maxZone.equals(info.max) ) {
            info.setRange(id, minZone, maxZone);
        }
        
        // Now we blast an update to the zones for any listeners to handle.
        info.sendUpdate(id, p.clone(), orientation.clone());
    }    
   
    public void endUpdate()
    {
        // If we aren't really collecting history then don't do a commit.
        // We know how the zones push their history so doing this avoids
        // history accumulation.
        if( !collectHistory ) {
            return;
        }
    
        // Obtain the general write lock for history
        // For all of the other data structures, we know we
        // are the only reader and so don't need any read locks.
        historyLock.lock();
        try {
            // If we're about to overlow history then be a little
            // more graceful than throwing IndexOutOfBounds
            if( historySize + 1 >= historyIndex.length ) {
                log.warn("Pausing history collect.  Overlow detected, current history size:" + historySize + " max:" + historyBacklog);
                return;            
            }
        
            historyIndex[historySize++] = updateTime;
            
            for( Iterator<Map.Entry<ZoneKey,Zone>> i = zones.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry<ZoneKey,Zone> e = i.next();
                Zone z = e.getValue();                
                if( !z.commitUpdate() && z.isEmpty() ) {
                    if( log.isDebugEnabled() ) {
                        log.debug("Zone no longer active:" + e.getKey() + "  active zones:" + zones.keySet() );
                    }                    
                    // Now we can remove the zone
                    i.remove();                    
                }                 
            }
        } finally {
            historyLock.unlock();
        }
        
        updateTime = -1;
        long end = System.nanoTime();
        totalUpdateTime += (end - updateStartTime);
        if( end > nextLog ) {
            nextLog = end + 1000000000L;
            totalUpdateTime = 0;
        } 
    }

    public StateFrame[] purgeState() {
    
        // Obtain the general write lock for history since
        // we will be purging it        
        historyLock.lock();
        try {
int high = 5;        
if( historySize > high ) {            
    System.out.println( "Purging >" + high + " history frames:" + historySize );
}
            StateFrame[] state = new StateFrame[historySize];
 
            // Go through each zone and merge its history into an array
            // of StateFrames.                                               
            for( Iterator<Map.Entry<ZoneKey,Zone>> i = zones.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry<ZoneKey,Zone> e = i.next();
                Zone z = e.getValue();
                StateBlock[] history = z.purgeHistory(); 
 
                // Merge this zone's state blocks into the coinciding
                // StateFrames.               
                int h = 0;
                for( StateBlock b : history ) {
                    if( b.getTime() < historyIndex[h] ) {
                        throw new RuntimeException( "StateBlock precedes history index. Time:" + b.getTime() 
                                                    + "  history index:" + h + "  history time:" + historyIndex[h] );
                    }
 
                    // A given zone may have gaps in its history as compared to
                    // global state.                    
                    while( b.getTime() > historyIndex[h] ) {
                        h++;
                    }
                    
                    if( state[h] == null ) {
                        state[h] = new StateFrame(historyIndex[h], zones.size());
                    }
                    state[h].add(b);    
                }               
            }
 
            historySize = 0;
            return state;               
        } finally {
            historyLock.unlock();
        }
    }

    /**
     *  Let's the zone manager know about a particular entity.  This is
     *  only required if remove() if used for objects that may come back
     *  later... like for objects that enter/leave the zone manager if they
     *  are awake/asleep.  Essentially it just makes sure that the object
     *  isn't pending removal on the next update.
     */
    public void add( Long id ) {
        if( log.isDebugEnabled() ) {
            log.debug("ZONE:  +++ activated:" + id);
        }
        pendingRemoval.remove(id);
    }
    
    /**
     *  Removes the entity from the zone manager.  This can also be
     *  used to temporarily deactivate an entity but then add() must be
     *  called if it is active again.
     */
    public void remove( Long id ) {
        if( log.isDebugEnabled() ) {
            log.debug("ZONE:  --- deactivated:" + id);
        }
        ZoneRange range = index.get(id);
        if( log.isDebugEnabled() ) {
            log.debug("range:" + range);
        }        
        if( range == null ) {
            return;
        }
 
        // See if we are in the middle of a frame update or not           
        if( updateTime < 0 ) {
        
            // Outside of a frame update we need to hold onto
            // the removal until we have proper history setup.
            
            // Save it for later
            if( log.isDebugEnabled() ) {
                log.debug("ZONE:  --- pending:" + id);
            }
            pendingRemoval.add(id);
        } else {
            if( log.isDebugEnabled() ) {
                log.debug("ZONE:  --- leaving zone:" + id);
            }
            // Should really check the thread also
            index.remove(id);
            range.leave(id);
        }
    }    

    protected Zone getZone( ZoneKey key, boolean create ) {
        Zone result = zones.get(key);
        if( result == null && create ) {
            result = new Zone(key, historyBacklog);
            if( updateTime >= 0 ) {
                result.beginUpdate(updateTime);
            } 
            zones.put(key, result);
        }
        return result;        
    }

    protected void updateZoneObject( Long id, Vec3d p, Quatd orientation, ZoneKey key )
    {
        Zone zone = getZone(key, false);
        if( zone == null ) {
            log.warn( "Body is updating a zone that does not exist, id:" + id + ", zone:" + key );
            return;
        }
 
        zone.update(id, p, orientation);  
    }

    protected void enterZone( Long id, ZoneKey key ) {
        if( log.isDebugEnabled() ) {
            log.debug("ZONE: enter zone:" + id + "  " + key);
        }
        
        // Add this entity to the zone's children.
        // If there is no zone created yet then create one
        Zone zone = getZone(key, true);
        zone.addChild(id);
    }
     
    protected void leaveZone( Long id, ZoneKey key ) {
        if( log.isDebugEnabled() ) {
            log.debug("ZONE: leave zone:" + id + "  " + key);
        }
        
        // Remove this body from the zone's children...
        // If the zone has no more children then remove it
        Zone zone = getZone(key, false);
        if( zone == null ) {
            log.warn( "Body is leaving zone that does not exist, id:" + id + ", zone:" + key );
            return;
        }
        zone.removeChild(id);
        if( zone.isEmpty() ) {
            // We can't remove the zone until it is both empty
            // and devoid of state.  We do that when we commit
            // the current block of state.
            
            // But we can call any activation listeners to let them
            // know it has been deactivated, I suppose.           
        }
    }

    public static void main( String... args ) {
        ZoneGrid grid = new ZoneGrid(32);
        ZoneManager zones = new ZoneManager(grid);
 
        zones.beginUpdate(12345);
    
        zones.updateEntity(1L, true, new Vec3d(0, 0, 0), new Quatd(), new AaBBox(10));
        zones.updateEntity(1L, true, new Vec3d(16, 16, 0), new Quatd(), new AaBBox(10));
        zones.updateEntity(1L, true, new Vec3d(32, 16, 0), new Quatd(), new AaBBox(10));
        zones.updateEntity(1L, true, new Vec3d(48, 16, 0), new Quatd(), new AaBBox(10));
        
    }    

    private ZoneKey createKey( int x, int y, int z ) {
        return new ZoneKey(grid, x, y, z);
    }

    protected class ZoneRange {
        Vec3i min;
        Vec3i max;
        
        // Keys go like:
        //
        // min.y
        //       min.x  max.x
        // min.z   0      1      
        // max.z   3      2
        //
        // max.y
        //       min.x  max.x
        // min.z   4      5      
        // max.z   7      6
        //
        ZoneKey[] keys = new ZoneKey[8];       

        public ZoneRange() {
        }

        private boolean contains( int x, int y, int z ) {
            if( x < min.x || y < min.y || z < min.z )
                return false;
            if( x > max.x || y > max.y || z > max.z )            
                return false;
            return true;  
        }

        public void sendUpdate( Long id, Vec3d p, Quatd orientation ) {
            if( keys[0] != null ) {
                updateZoneObject(id, p, orientation, keys[0]);
            }
            if( keys[1] != null ) {
                updateZoneObject(id, p, orientation, keys[1]);
            }
            if( keys[2] != null ) {
                updateZoneObject(id, p, orientation, keys[2]);
            }
            if( keys[3] != null ) {
                updateZoneObject(id, p, orientation, keys[3]);
            }
        }
        
        public void setRange( Long id, Vec3i newMin, Vec3i newMax ) {
            ZoneKey[] oldKeys = keys.clone();
            
            // We could avoid recreating keys if they are already
            // set but the logic is tricky to get right and it's 
            // not called very often anyway.
            
            // Create all of the min.y keys first and we will
            // project them if max.y != min.y
            keys[0] = createKey(newMin.x, newMin.y, newMin.z);
            if( newMin.x != newMax.x )
                keys[1] = createKey(newMax.x, newMin.y, newMin.z);
            else
                keys[1] = null;
            
            if( newMin.z != newMax.z )
                keys[3] = createKey(newMin.x, newMin.y, newMax.z);
            else
                keys[3] = null;
            
            if( keys[1] != null && keys[3] != null )
                keys[2] = createKey(newMax.x, newMin.y, newMax.z);
            else
                keys[2] = null;
 
            if( newMin.y != newMax.y ) {
                // Need to project them up
                keys[4] = keys[0] == null ? null : createKey(keys[0].x, newMax.y, keys[0].z);
                keys[5] = keys[1] == null ? null : createKey(keys[1].x, newMax.y, keys[1].z);
                keys[6] = keys[2] == null ? null : createKey(keys[2].x, newMax.y, keys[2].z);
                keys[7] = keys[3] == null ? null : createKey(keys[3].x, newMax.y, keys[3].z);
            } else {
                // Null them all out
                keys[4] = null;
                keys[5] = null;
                keys[6] = null;
                keys[7] = null;
            }
 
            if( this.min == null ) {
                // Then this is the first time and we can be optimized to
                // just enter all of them.
                this.min = newMin;            
                this.max = newMax;
                enter( id );
                return;            
            }
 
            enterMissing( id, newMin, newMax, keys );
 
            Vec3i oldMin = this.min;
            Vec3i oldMax = this.max;               
            this.min = newMin;            
            this.max = newMax;            
 
            leaveMissing( id, oldMin, oldMax, oldKeys );
        } 

        private void enter( Long id ) {
 
            for( ZoneKey key : keys ) {
                if( key != null ) {
                    enterZone(id, key);
                }
            }       
        }

        public void leave( Long id ) {
            if( log.isDebugEnabled() ) {
                log.debug("ZoneRange.leave(" + id + ")  keys:" + Arrays.asList(keys));
            }        
            for( ZoneKey key : keys ) {
                if( key != null ) {
                    leaveZone(id, key);
                }
            }
        }                
 
        private void enterMissing( Long id, Vec3i minZone, Vec3i maxZone, ZoneKey[] zoneKeys ) {
 
            // See which new zone keys are not in the current range
            for( ZoneKey key : zoneKeys ) {
                if( key != null && !contains(key.x, key.y, key.z) ) {
                    enterZone(id, key);   
                }
            }       
        }

        public void leaveMissing( Long id, Vec3i minZone, Vec3i maxZone, ZoneKey[] zoneKeys ) {
            // See which old zone keys are not in the current range
            for( ZoneKey key : zoneKeys ) {
                if( key != null && !contains(key.x, key.y, key.z) ) {
                    leaveZone(id, key);   
                }
            }
        }
        
    }    
}

 

