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

import java.util.HashMap;
import java.util.Map;


/**
 *  Provides a unique limited range set of integer IDs for a set 
 *  of Long object IDs.  Generated IDs can be reused later if the 
 *  caller returns them to the pool with retireId().
 *
 *  @author    Paul Speed
 */
public class IdIndex {
 
    private final Map<Integer,Long> entityIdMap = new HashMap<>();
    private final Map<Long,Integer> idMap = new HashMap<>();
    private int minId;
    private int maxId;
    private int nextId;
      
    public IdIndex( int minId ) {
        this(minId, 65536);
    }

    public IdIndex( int minId, int maxId ) {
        this.minId = minId;
        this.maxId = maxId;
        this.nextId = minId;
    }
     
    protected void incrementNextId() {
        nextId++;
        if( nextId > maxId ) {
            nextId = minId;
        }          
    }
 
    protected int nextId( Long entity ) {
    
        // Skip IDs that are already in use.  If this
        // happens often then we should potentially do
        // something smarter.  A way to skip large ranges
        // of in-use values.
        while( entityIdMap.containsKey(nextId) ) {
System.out.println( "******** ID already in use:" + nextId );            
            incrementNextId();
        } 
        
        int result = nextId;
        entityIdMap.put(result, entity);
        idMap.put(entity,result);
        incrementNextId();
             
        return result;
    }
 
    public int getId( Long entity, boolean create ) {    
        Integer result = idMap.get(entity);
        if( result == null && create ) {
            return nextId(entity); 
        }
        return result == null ? -1 : result;
    }
 
    public Long getEntityId( int id ) {
        return entityIdMap.get(id);
    }
 
    public void retireId( int id ) {
        Long removed = entityIdMap.remove(id);
        if( removed == null ) {
            System.out.println( "**** Retired id:" + id + " with no mapped entity." );
        }
        idMap.remove(removed);            
    }

}
