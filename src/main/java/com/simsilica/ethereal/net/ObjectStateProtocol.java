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

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.bits.QuatBits;
import com.simsilica.mathd.bits.Vec3Bits;
import com.simsilica.ethereal.io.BitInputStream;
import com.simsilica.ethereal.io.BitOutputStream;
import java.io.IOException;


/**
 *  Holds information about the bit sizes of various
 *  ObjectState fields.
 *
 *  @author    Paul Speed
 */
public class ObjectStateProtocol {

    public int zoneIdBitSize;
    public int idBitSize;
    public Vec3Bits positionBits;
    public QuatBits rotationBits;
    
    public ObjectStateProtocol( int zoneIdBitSize, int idBitSize, 
                                Vec3Bits posBits, QuatBits rotBits ) {
        this.zoneIdBitSize = zoneIdBitSize;
        this.idBitSize = idBitSize;
        this.positionBits = posBits;
        this.rotationBits = rotBits;
    }
 
    public void setPosition( ObjectState state, Vec3d pos ) {
        state.positionBits = positionBits.toBits(pos);
    }
 
    public Vec3d getPosition( ObjectState state ) {
        return positionBits.fromBits(state.positionBits);
    }

    public void setRotation( ObjectState state, Quatd rot ) {
        state.rotationBits = rotationBits.toBits(rot);
    }
    
    public Quatd getRotation( ObjectState state ) {
        return rotationBits.fromBits(state.rotationBits);
    }
    
    public int getEstimatedBitSize( ObjectState state ) {
        // Basic state layout
        //  networkdId: 16 bits
        //  ?hasRealId: 1 bit
        //      -realId: idBitSize bits
        //  ?hasZone: 1 bit
        //      -parent: zoneIdBitSize bits
        //  ?hasParent: 1 bit
        //      -parent: idBitSize bits
        //  ?hasPosition: 1 bit
        //      -position: posBits bits
        //  ?hasRotation: 1 bit
        //      -rotation: rotBits bits
        int size = 16;
        
        size++;
        if( state.zoneId != -1 ) {
            size += zoneIdBitSize;
        }
 
        size++;
        if( state.realId != null ) {
            size += idBitSize;
        }
 
        size++;
        if( state.parentId != null ) {
            size += idBitSize;
        }
        
        size++;
        if( state.positionBits != -1 ) {
            size += positionBits.getBitSize();
        }
            
        size++;
        if( state.rotationBits != -1 ) {
            size += rotationBits.getBitSize();
        }
                   
        return size;
    }  
 
    public void writeBits( ObjectState state, BitOutputStream out ) throws IOException {
 
        if( state == null ) {
            out.writeBits(0, 16);
            return;
        }
    
        out.writeBits(state.networkId, 16);
        if( state.networkId == 0 ) {
            // Nothing else to write... and it might be a bug
            throw new IllegalArgumentException("Object state networkId is 0");
        }

        if( state.zoneId == -1 ) {
            out.writeBits(0, 1);
        } else {
            out.writeBits(1, 1);
            out.writeBits(state.zoneId, zoneIdBitSize);
        }
    
        if( state.realId == null ) {
            out.writeBits(0, 1);
        } else {
            out.writeBits(1, 1);
            out.writeLongBits(state.realId, idBitSize);
        }
 
        if( state.parentId == null ) {
            out.writeBits(0, 1);
        } else {
            out.writeBits(1, 1);
            out.writeLongBits(state.parentId, idBitSize);
        }
            
        if( state.positionBits == -1 ) {
            out.writeBits(0, 1);
        } else {
            out.writeBits(1, 1);
            out.writeLongBits(state.positionBits, positionBits.getBitSize());
        }
            
        if( state.rotationBits == -1 ) {
            out.writeBits(0, 1);
        } else {
            out.writeBits(1, 1);
            out.writeLongBits(state.rotationBits, rotationBits.getBitSize());
        }
    }
       
    public ObjectState readBits( BitInputStream in ) throws IOException {
            
        int networkId = in.readBits(16);
        if( networkId == 0 ) {
            return null;
        }
        ObjectState state = new ObjectState();
        state.networkId = networkId;
        
        int bit;
        bit = in.readBits(1);
        if( bit != 0 ) {
            state.zoneId = in.readBits(zoneIdBitSize);
        }

        bit = in.readBits(1);
        if( bit != 0 ) {
            state.realId = in.readLongBits(idBitSize);
        }

        bit = in.readBits(1);
        if( bit != 0 ) {
            state.parentId = in.readLongBits(idBitSize);
        }
        
        bit = in.readBits(1);
        if( bit != 0 ) {
            state.positionBits = in.readLongBits(positionBits.getBitSize());
        }
        
        bit = in.readBits(1);
        if( bit != 0 ) {
            state.rotationBits = in.readLongBits(rotationBits.getBitSize());
        }
        
        return state;
    } 

}

