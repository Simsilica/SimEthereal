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
 *  <p>Object updates need to be done in the context of a 'frame' so
 *  that the updates can be properly grouped together.  To do this, 
 *  the game code that is reporting updates needs to call the ZoneManager
 *  methods using a specific life cycle:</p>
 *
 *  <ul>
 *  <li>beginUpdate(frameTime): starts a new frame.  The frame time will
 *  be used to stamp any state updates.</li>
 *  <li>updateEntity(...): record an update event for the specific entity
 *  within the current frame.</li>
 *  <li>remove(entityId): removes an entity from the zone manager.  This can
 *  be called between beginUpdate() and endUpdate() but it can also be called
 *  outside of those calls and the removal will be enqueued for the next update.
 *  If this feature is used then make sure to also call add() if the object
 *  needs to come back.</li>
 *  <li>endUpdate(): closes updates to the current frame.</li>
 *  <li>add(entityId): used outside of beginUpdate()/endUpdate() if remove(id)
 *  is also used outside of beingUpdate()/endUpdate().  Calling remove(id)
 *  outside of a begin/end block will enqueue the removal for the next begin/end
 *  block.  add() will remove it from this queue in case that removal hasn't been
 *  sent yet.  Normally add() is not required as updateEntity() will add the
 *  entity if it hasn't already seen it.</lI>    
 *  </ul>
 *
 *  @author    Paul Speed
 */
public class ZoneManager {
    static Logger log = LoggerFactory.getLogger(ZoneManager.class);
 
    private ZoneGrid grid;   
    private final Map<Long, ZoneRange> index = new HashMap<>();

    // Per frame, keep track of the keys we did not get updates for.
    // Added for no-change support.
    private Set<Long> noUpdates;

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

    // Control which ZoneRange implementation is used... currently
    // there are only two and we'll default to the older better-tested
    // one for a while.
    private boolean dynamicZoneRange = false;

    // For frame rate limiting
    private int frameUnderflowLimit = 60;
    private int frameOverflowLimit = 70;

    /**
     *  Creates a new ZoneManager with a ZoneGrid sized using zoneSize and
     *  with a history backlog of 12 frames.
     */
    public ZoneManager( int zoneSize ) {
        this(new ZoneGrid(zoneSize));
    }
 
    /**
     *  Creates a new ZoneManager with the specified grid representation and
     *  with a history backlog of 12.
     */   
    public ZoneManager( ZoneGrid grid ) {
        this(grid, 12);
    }
 
    /**
     *  Creates a new zone manager with the specified grid representation and
     *  history backlog.
     *
     *  @param grid The grid settings used for zone partitioning.
     *  @param historyBacklog Designates how many frames of history to keep
     *  in each zone. 
     */   
    public ZoneManager( ZoneGrid grid, int historyBacklog ) {
        this.grid = grid;
        this.historyBacklog = historyBacklog;
        this.historyIndex = new long[historyBacklog];
    }

    /**
     *  Returns the current zone specification used for partioning space into
     *  zones.
     */
    public ZoneGrid getGrid() {
        return grid;
    }

    /**
     *  Set to true to support objects that are larger than the grid spacing.
     *  This is a new experimental feature that may become the default behavior
     *  someday.  In the mean time, applications have to specifically turn it
     *  on because the new code is less tested.
     *  The older internal zone range implementation would not support objects
     *  that spanned more than two zones in any direction (and will log an error
     *  when it encounters this).  The thinking was that it could use a fixed size
     *  array and avoid constantly recreating it.  The thing is it was always 
     *  cloning it internally anyway whenever the range changed.
     *  The new code builds its array dynamically and can essentially support
     *  any zone range.  It's probably just as good and just as memory efficient.
     */
    public void setSupportLargeObjects( boolean b ) {
        this.dynamicZoneRange = b;
    }
    
    public boolean getSupportLargeObjects() {
        return dynamicZoneRange;
    }

    /**
     *  Set to true if history should be collected or false if object updates
     *  should be ignored.  This method is used internal to the framework for
     *  managing the lifecycle of dependent components.  When a ZoneManager is 
     *  not part of an active state collection process then it's important that 
     *  it not collect any history because it might overflow its buffers since purge() 
     *  is never called.  Generally, the StateCollector will turn history collection on 
     *  when it is ready to start periodically purging history.
     *  Defaults to false until a state collection process turns it on.
     */
    public void setCollectHistory( boolean b ) {
        this.collectHistory = b;
    }
    
    /**
     *  Returns true if the ZoneManager is currently collecting history, false otherwise.
     *  When a ZoneManager is not part of an active StateCollector then it's important
     *  that it not track history. 
     */
    public boolean getCollectHistory() {
        return collectHistory;
    }

    /**
     *  Sets frame update limits which are used to print warnings when ZoneManager update rate
     *  goes under or over the specified value.
     *  By default underflow limit is 60 and overflow limit is 70.
     *
     *  @throws IllegalArgumentException if values are negative or underflow limit is larger than the overflow limit.
     */
    public void setFrameRateLimits( int frameUnderflowLimit, int frameOverflowLimit ) {
        if( frameUnderflowLimit < 0 || frameOverflowLimit < 0 ) {
            throw new IllegalArgumentException("Frame limits must be positive.");
        }
        if( frameUnderflowLimit > frameOverflowLimit ) {
            throw new IllegalArgumentException("Frame underflow limit must be less than the overflow limit.");
        }

        this.frameUnderflowLimit = frameUnderflowLimit;
        this.frameOverflowLimit = frameOverflowLimit;
    }

    /**
     *  Sets frame underflow limit. ZoneManager will print warnings if frame update
     *  rate goes below this limit. By default underflow limit is 60.
     *
     *  @throws IllegalArgumentException if value is negative or larger than the overflow limit.
     */
    public void setFrameUnderflowLimit( int frameUnderflowLimit ) {
        if( frameUnderflowLimit < 0 ) {
            throw new IllegalArgumentException("Frame underflow limit must be positive.");
        }
        if( frameUnderflowLimit > frameOverflowLimit ) {
            throw new IllegalArgumentException("Frame underflow limit must be less than the overflow limit.");
        }

        this.frameUnderflowLimit = frameUnderflowLimit;
    }

    public int getFrameUnderflowLimit() {
        return frameUnderflowLimit;
    }

    /**
     *  Sets frame overflow limit. ZoneManager will print warnings if frame update
     *  rate goes above this limit. By default overflow limit is 70.
     *
     *  @throws IllegalArgumentException if value is negative or less than the underflow limit.
     */
    public void setFrameOverflowLimit( int frameOverflowLimit ) {
        if( frameOverflowLimit < 0 ) {
            throw new IllegalArgumentException("Frame overflow limit must be positive.");
        }
        if( frameOverflowLimit < frameUnderflowLimit ) {
            throw new IllegalArgumentException("Frame overflow limit must be larger than the underflow limit.");
        }

        this.frameOverflowLimit = frameOverflowLimit;
    }

    public int getFrameOverflowLimit() {
        return frameOverflowLimit;
    }

    protected ZoneRange getZoneRange( Long id, boolean create ) {
        ZoneRange result = index.get(id);
        if( result == null && create ) {
            if( dynamicZoneRange ) {
                result = new DynamicZoneRange(id);
            } else {
                result = new OctZoneRange(id);
            }
            index.put(id, result);
        }
        return result;
    }

long frameCounter = 0;
long nextFrameTime = System.nanoTime() + 1000000000L;

//long lastTime = 0;

    /**
     *  Starts history collection for a frame at the specified time.  All
     *  updateEntity() and remove() calls after this beginUpdate() will be
     *  collected together and associated with the specified frame time
     *  until endUpdate() is called.
     */
    public void beginUpdate( long time ) {
        if( log.isTraceEnabled() ) {
            log.trace("beginUpdate(" + time + ")");
        }    
        updateStartTime = System.nanoTime();
        updateTime = time;
        
        /*if( lastTime > 0 ) {
            long delta = time - lastTime;
            System.out.println("zone time delta frame[" + timeCheckCounter + "]:" + ((delta)/1000000.0) + " ms");
        }
        lastTime = time;*/

        frameCounter++;
        if( updateStartTime > nextFrameTime ) {
            if( frameCounter < frameUnderflowLimit ) {
                log.warn("zone update underflow FPS:" + frameCounter);
            } else if( frameCounter > frameOverflowLimit ) {
                log.warn("zone update overflow FPS:" + frameCounter);
            }
            //System.out.println("zone update FPS:" + frameCounter);
            frameCounter = 0;
            nextFrameTime = System.nanoTime() + 1000000000L;
        }
        // Keep track of the IDs for objects that receive no updates.
        // (by subtraction)  Added for no-update support.        
        // Seed it with all known object IDs.
        noUpdates = new HashSet<>(index.keySet());
        
        // Remove any of the pending deletes
        noUpdates.removeAll(pendingRemoval);
        
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
 
    /**
     *  Updates an entity's position in the zone space.
     *
     *  @param id The ID of the object that has been moved.
     *  @param active Currently unused.  Pass 'true' or the non-sleeping state of your object
     *          if you care to be accurate for future changes.
     *  @param p The position of the object in world space. 
     *  @param orientation The orientation of the object in world space.
     *  @param bounds The 3D bounds of the object in WORLD SPACE.  This is why it's passed
     *          every update and it allows it to change to be accurate as the object rotates and
     *          so on.  But more importantly, it pushes the updating of the bounds to the thing
     *          actually controlling the position which might be able to more efficiently update
     *          it than we could internally.  
     */
    public void updateEntity( Long id, boolean active, Vec3d p, Quatd orientation, AaBBox bounds ) {

        if( log.isTraceEnabled() ) {
            log.trace("updateEntity(" + id + ", " + active + ", " + p + ")");
        }
         
        // If one day you are looking in here and wondering why 'id' is a Long instead of
        // just 'long'... it's because we internally use it as a key for a bunch of things.
        // By exposing the object version to the caller they can avoid the excessive autoboxing
        // that would occure if we did it internally.  By projecting outward, we can even
        // encourage the caller to also keep Long IDs and then the autoboxing never happens
        // but at the very least, we only do it once in here.
    
        Vec3i minZone = grid.worldToZone(bounds.getMin()); 
        Vec3i maxZone = grid.worldToZone(bounds.getMax()); 
 
        ZoneRange range = getZoneRange(id, true);
        if( !minZone.equals(range.getMin()) || !maxZone.equals(range.getMax()) ) {
        
            if( !range.setRange(minZone, maxZone) ) {
                log.error("Error setting range for object:" + id 
                          + " from bounds:" + bounds
                          + " grid size:" + grid.getZoneSize() 
                          + " likely too small for object with extents:" + bounds.getExtents());
            }
        }
        
        // Now we blast an update to the zones for any listeners to handle.
        range.sendUpdate(p.clone(), orientation.clone());
        
        // Mark that we've received an update for this object.  Added for no-change support.
        noUpdates.remove(id);
    }    
 
    /**
     *  Called to end update collection for the current 'frame'.  See: beginUpdate()
     */  
    public void endUpdate() {
    
        log.trace("endUpdate()");
        
        // If we aren't really collecting history then don't do a commit.
        // We know how the zones push their history so doing this avoids
        // history accumulation.
        if( !collectHistory ) {
            return;
        }

        if( log.isTraceEnabled() ) {
            log.trace("No-updates for keys:" + noUpdates);
        }
        
        // Go through any of the objects that didn't get updates and send
        // no-update events.  Added for no-change support.     
        if( noUpdates != null && !noUpdates.isEmpty() ) {
            for( Long id : noUpdates ) {
                ZoneRange range = getZoneRange(id, false);
                if( range == null ) {
                    log.warn("No zone range found for no-change key:" + id);
                    continue;
                }
                range.sendNoChange();
            }
        } 
    
        // Obtain the general write lock for history
        // For all of the other data structures, we know we
        // are the only reader and so don't need any read locks.
        log.trace("writing history");        
        historyLock.lock();
        try {
            // If we're about to overflow history then be a little
            // more graceful than throwing IndexOutOfBounds
            if( historySize + 1 >= historyIndex.length ) {
                log.warn("Pausing history collect.  Overflow detected, current history size:" + historySize + " max:" + historyBacklog);
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
            log.trace("done writing history");        
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

    /**
     *  Called by a state collection process to return all of the history that
     *  has been collected since the last call to purgeState().  Generally, this
     *  method is called internal to the framework, usually by the StateCollector.
     *  The return array will contain one StateFrame[] for every beginUpdate()/endUpdate()
     *  block since the last purgeState().  Each StateFrame will contain all of the 
     *  state updates for that frame divided into separate StateBlocks, one per active
     *  zone.
     */
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
     *  only required if remove() is used for objects that may come back
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

    protected void updateZoneObject( Long id, Vec3d p, Quatd orientation, ZoneKey key ) {
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


    protected interface ZoneRange {
        public Vec3i getMin();
        public Vec3i getMax();
        public boolean setRange( Vec3i newMin, Vec3i newMax );
        public void sendUpdate( Vec3d p, Quatd orientation );
        public void sendNoChange();
        public void leave( Long id );
    }

    /**
     *  The original ZoneRange implementation that maxed out
     *  at 8 zones, ie: 2x2x2.  It keeps a fixed array of zone keys
     *  and nulls out the ones that aren't being used.
     *  I think the original thinking was that we avoid array creation
     *  churn... but it also limits the size of objects to never be any
     *  bigger than a zone cell.  Besides which we are recreating
     *  keys at least as often as we might the array.
     */
    protected final class OctZoneRange implements ZoneRange {
        Long id;
    
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

        // For purposes of handling 'no-change' updates, we will keep
        // the last state we received.  Added for no-updated support.
        Vec3d lastPosition;
        Quatd lastOrientation;        

        public OctZoneRange( Long id ) {
            this.id = id;
        }

        @Override    
        public Vec3i getMin() {
            return min;
        }
        
        @Override    
        public Vec3i getMax() {
            return max;
        }

        private boolean contains( int x, int y, int z ) {
            if( x < min.x || y < min.y || z < min.z )
                return false;
            if( x > max.x || y > max.y || z > max.z )            
                return false;
            return true;  
        }

        @Override    
        public void sendUpdate( Vec3d p, Quatd orientation ) {
            // They were cloned before giving them to us, safe
            // to keep them directly.  Added for no-updated support.
            lastPosition = p;
            lastOrientation = orientation;
            
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
        
        @Override    
        public void sendNoChange() {
            // Nothing special here... just resend the last data.
            // Someday maybe there is something optimized?  Don't
            // know what and doesn't matter today.  Added for no-change support.
            sendUpdate(lastPosition, lastOrientation);
        }
        
        @Override    
        public boolean setRange( Vec3i newMin, Vec3i newMax ) {

            boolean result = true; 
            // Check the range to see if it's too large... this indicates that
            // the bounds is too big for the grid spacing.
            if( newMax.x - newMin.x > 1
                || newMax.y - newMin.y > 1  
                || newMax.z - newMin.z > 1 ) {
                log.error("OctZoneRange: Range too big:" + newMin + " -> " + newMax);
                result = false;
            }
        
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
                enter(id);
                return result;            
            }
 
            enterMissing( id, newMin, newMax, keys );
 
            Vec3i oldMin = this.min;
            Vec3i oldMax = this.max;               
            this.min = newMin;            
            this.max = newMax;            
 
            leaveMissing( id, oldMin, oldMax, oldKeys );
            
            return result;
        } 

        private void enter( Long id ) {
 
            for( ZoneKey key : keys ) {
                if( key != null ) {
                    enterZone(id, key);
                }
            }       
        }

        @Override    
        public void leave( Long id ) {
            if( log.isDebugEnabled() ) {
                log.debug("OctZoneRange.leave(" + id + ")  keys:" + Arrays.asList(keys));
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

        private void leaveMissing( Long id, Vec3i minZone, Vec3i maxZone, ZoneKey[] zoneKeys ) {
            // See which old zone keys are not in the current range
            for( ZoneKey key : zoneKeys ) {
                if( key != null && !contains(key.x, key.y, key.z) ) {
                    leaveZone(id, key);   
                }
            }
        }
        
    }
    
    protected final class DynamicZoneRange implements ZoneRange {
        Long id;
    
        Vec3i min;
        Vec3i max;
        
        ZoneKey[] keys = new ZoneKey[0];
        int keyCount; 

        // For purposes of handling 'no-change' updates, we will keep
        // the last state we received.  Added for no-updated support.
        Vec3d lastPosition;
        Quatd lastOrientation;        

        public DynamicZoneRange( Long id ) {
            this.id = id;
        }

        @Override    
        public Vec3i getMin() {
            return min;
        }
        
        @Override    
        public Vec3i getMax() {
            return max;
        }

        private boolean contains( int x, int y, int z ) {
            if( x < min.x || y < min.y || z < min.z )
                return false;
            if( x > max.x || y > max.y || z > max.z )            
                return false;
            return true;  
        }

        @Override    
        public void sendUpdate( Vec3d p, Quatd orientation ) {
            // They were cloned before giving them to us, safe
            // to keep them directly.  Added for no-updated support.
            lastPosition = p;
            lastOrientation = orientation;
 
            for( int i = 0; i < keyCount; i++ ) {
                updateZoneObject(id, p, orientation, keys[i]);
            }
        }
        
        @Override    
        public void sendNoChange() {
            // Nothing special here... just resend the last data.
            // Someday maybe there is something optimized?  Don't
            // know what and doesn't matter today.  Added for no-change support.
            sendUpdate(lastPosition, lastOrientation);
        }
        
        @Override    
        public boolean setRange( Vec3i newMin, Vec3i newMax ) {

            ZoneKey[] oldKeys = keys.clone();
            int oldKeyCount = keyCount;

            int xSize = newMax.x - newMin.x + 1;
            int ySize = newMax.y - newMin.y + 1;
            int zSize = newMax.z - newMin.z + 1;
            
            int size = xSize * ySize * zSize;
            if( oldKeys.length < size || oldKeys.length > size * 2 ) {
                // Grow or shrink it.  We shrink if our keys are less than
                // half the old size just to avoid taking up too much RAM.
                // ...and to avoid potential memory explosions for large objects
                // that never shrink again.
                keys = new ZoneKey[size];
            }
            keyCount = size;
            
            int index = 0;
            for( int i = 0; i < xSize; i++ ) {
                for( int j = 0; j < ySize; j++ ) {
                    for( int k = 0; k < zSize; k++ ) {
                        keys[index] = createKey(newMin.x + i, newMin.y + j, newMin.z + k);
                        index++;
                    }
                }
            }
            
            if( this.min == null ) {
                // Then this is the first time and we can be optimized to
                // just enter all of them.
                this.min = newMin;            
                this.max = newMax;
                enter(id);
                return true;            
            }
 
            enterMissing(id, newMin, newMax, keys, keyCount);
 
            Vec3i oldMin = this.min;
            Vec3i oldMax = this.max;               
            this.min = newMin;            
            this.max = newMax;            
 
            leaveMissing(id, oldMin, oldMax, oldKeys, oldKeyCount);
            
            return true;
        } 

        private void enter( Long id ) { 
            for( int i = 0; i < keyCount; i++ ) {
                enterZone(id, keys[i]);
            }       
        }

        @Override    
        public void leave( Long id ) {
            if( log.isDebugEnabled() ) {
                log.debug("DynamicZoneRange.leave(" + id + ")  keys:" + Arrays.asList(keys));
            }        
            for( int i = 0; i < keyCount; i++ ) {
                leaveZone(id, keys[i]);
            }
        }                
 
        private void enterMissing( Long id, Vec3i minZone, Vec3i maxZone, ZoneKey[] zoneKeys, int count ) {
 
            // See which new zone keys are not in the current range
            for( int i = 0; i < count; i++ ) {
                ZoneKey key = zoneKeys[i];
                if( !contains(key.x, key.y, key.z) ) {
                    enterZone(id, key);   
                }
            }       
        }

        private void leaveMissing( Long id, Vec3i minZone, Vec3i maxZone, ZoneKey[] zoneKeys, int count ) {
            // See which old zone keys are not in the current range
            for( int i = 0; i < count; i++ ) {
                ZoneKey key = zoneKeys[i];
                if( !contains(key.x, key.y, key.z) ) {
                    leaveZone(id, key);   
                }
            }
        }
        
    }    

}

 

