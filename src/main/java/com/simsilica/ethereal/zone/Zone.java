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

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 *
 *  @author    Paul Speed
 */
public class Zone {
    static Logger log = LoggerFactory.getLogger(Zone.class);

    private final ZoneKey key;
    private final Set<Long> children = new HashSet<>();

    private StateBlock current;
    private final StateBlock[] history;
    private int historyIndex = 0;

    public Zone( ZoneKey key, int historyBacklog ) {
        this.key = key;
        this.history = new StateBlock[historyBacklog];
    }

    public void beginUpdate( long time ) {
        if( log.isTraceEnabled() ) {
            log.trace(key + ":beginUpdate(" + time + ")");
        }
        current = new StateBlock(time, key);
    }

    /**
     *  Adds the update information to the current StateBlock. If parent is null
     *  then the object is a child of the world and has no parent.
     */
    public void update( Long parent, Long id, Vec3d pos, Quatd rotation ) {
        if( log.isTraceEnabled() ) {
            log.trace(key + ":update(" + id + ", " + pos + ")");
        }
        current.addUpdate(parent, id, pos, rotation);
    }

    /**
     *  Adds the warp information to the current StateBlock.
     */
    public void warp( Long parent, Long id ) {
        if( log.isTraceEnabled() ) {
            log.trace(key + ":warp(" + parent + ", " + id + ")");
        }
        current.addWarp(parent, id);
    }

    public void addChild( Long id ) {
        if( log.isTraceEnabled() ) {
            log.trace(key + ":addChild(" + id + ")");
        }
        if( !children.add(id) ) {
            log.warn( "Zone already had a body child for id:" + id );
        }
    }

    public void removeChild( Long id ) {
        if( log.isTraceEnabled() ) {
            log.trace(key + ":removeChild(" + id + ")");
        }
        if( !children.remove(id) ) {
            log.warn( "Zone did not have child to remove for id:" + id );
        }
        current.removeEntity(id);
    }

    public final boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     *  Returns true if there was a state block to push or false
     *  if the state block was empty and history is also empty.
     *  Note: this assumes the overall history write lock has
     *        already been obtained and that it is safe to write.
     */
    public boolean commitUpdate() {
        if( log.isTraceEnabled() ) {
            log.trace(key + ":commitUpdate() empty:" + current.isEmpty() + "   children:" + children);
        }
        if( current.isEmpty() ) {
            // Return true if history is not empty... false otherwise.
            current = null;
            return historyIndex != 0;
        }
        history[historyIndex++] = current;
        current = null;
        return true;
    }

    /**
     *  Purges the history and returns it.  This assumes the overall
     *  write lock has already been obtained and thus that it is safe
     *  to clear the history.
     */
    public StateBlock[] purgeHistory() {
        StateBlock[] result = new StateBlock[historyIndex];
        System.arraycopy( history, 0, result, 0, historyIndex );
        for( int i = 0; i < historyIndex; i++ ) {
            history[i] = null;
        }
        historyIndex = 0;
        return result;
    }

    @Override
    public String toString() {
        return "Zone[" + key + ", child.size=" + children.size() + "]";
    }
}

