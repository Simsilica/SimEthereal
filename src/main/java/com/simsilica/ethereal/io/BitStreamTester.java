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

package com.simsilica.ethereal.io;

import java.io.*;
import java.util.Random;

/**
 *
 *
 *  @author    Paul Speed
 */
public class BitStreamTester {

    public static void main( String... args ) throws Exception {
 
        Random rand = new Random(1);
    
        // Create an array of test values and test bit sizes
        int count = 1000;
        int[] bits = new int[count];
        long[] values = new long[count];
        int totalBits = 0;
        
        for( int i = 0; i < count; i++ ) {
            int size = rand.nextInt(63) + 1;
            bits[i] = size;
            totalBits += size;
            long mask = 0xffffffffffffffffL >>> (64 - size);
            long value = rand.nextLong();
            value = value & mask;
            
            // No reason to let negatives get in the way... testing the
            // high bit will happen anyway.
            value = value & 0x7fffffffffffffffL;
 
            //System.out.println("[" + i + "] bits:" + size + "  value:" + Long.toHexString(value) + "  mask:" + Long.toHexString(mask));              
            values[i] = value;
        }
        
        System.out.println("Writing " + count + " values as " + totalBits + " bits... ~" + (totalBits / 8) + " bytes.");
        
        // Write the data
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        BitOutputStream out = new BitOutputStream(bOut);
        try {
            for( int i = 0; i < count; i++ ) {
                out.writeLongBits(values[i], bits[i]);
            } 
        } finally {
            out.close();
        }
        
        byte[] raw = bOut.toByteArray();
        System.out.println("Array size:" + raw.length);
        
        // Read it back
        long[] readValues = new long[count];
        ByteArrayInputStream bIn = new ByteArrayInputStream(raw);
        BitInputStream in = new BitInputStream(bIn);
        for( int i = 0; i < count; i++ ) {
            readValues[i] = in.readLongBits(bits[i]);
        }
        
        // Now compare the values
        boolean good = true;
        for( int i = 0; i < count; i++ ) {
            if( readValues[i] != values[i] ) {
                System.out.println("Index " + i + " differs, read:" + readValues[i] + " wrote:" + values[i] + " size:" + bits[i]);
                good = false;
                break;
            }
        }
        if( good ) {
            System.out.println("Input matches written data.");
        }
    }
}
