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

/**
 *  Reads bit strings of any length from an
 *  underlying stream.
 *
 *  @version   $Revision: 4022 $
 *  @author    Paul Speed
 */
public class BitInputStream implements AutoCloseable {
    private final InputStream in;
    private int lastByte;
    private int bits = 0;
    
    public BitInputStream( InputStream in ) {
        this.in = in;
    }
 
    public long readLongBits( int count ) throws IOException {
        if( count == 0 )
            throw new IllegalArgumentException( "Cannot read 0 bits." );
    
        if( count > 64 )
            throw new IllegalArgumentException( "Bit count overflow:" + count );
            
        long result = 0;
 
        // While we still have bits remaining...       
        int remainingCount = count;
        while( remainingCount > 0 ) {
            // See if we need to refill the current read byte
            if( bits == 0 ) {
                int b = in.read();
                if( b < 0 )
                    throw new IOException( "End of stream reached." );
                lastByte = b;
                bits = 8;                    
            }
 
            // Copy the smaller of the two: remaining bits
            // or bits left in lastByte.
            int bitsToCopy = bits < remainingCount ? bits : remainingCount;
            
            // How much do we have to shift the read byte to just
            // get the high bits we want?
            int sourceShift = bits - bitsToCopy;
            
            // And how much do we have to shift those bits to graft
            // them onto our result?
            int targetShift = remainingCount - bitsToCopy;
 
            // Copy the bits           
            result |= ((long)lastByte >> sourceShift) << targetShift;
 
            // Keep track of how many bits we have left
            remainingCount -= bitsToCopy;
            bits -= bitsToCopy;
                      
            // Now we need to mask off the bits we just copied from
            // lastByte.  Just keep the bits that are left.
            lastByte = lastByte & (0xff >> (8 - bits));           
        }
            
        return result;
    }
 
    public int readBits( int count ) throws IOException {
        if( count == 0 )
            throw new IllegalArgumentException( "Cannot read 0 bits." );
            
        if( count > 32 )
            throw new IllegalArgumentException( "Bit count overflow:" + count );
        
        int result = 0;
 
        // While we still have bits remaining...       
        int remainingCount = count;
        while( remainingCount > 0 ) {
            // See if we need to refill the current read byte
            if( bits == 0 ) {
                int b = in.read();
                if( b < 0 )
                    throw new IOException( "End of stream reached." );
                lastByte = b;
                bits = 8;                    
            }
 
            // Copy the smaller of the two: remaining bits
            // or bits left in lastByte.
            int bitsToCopy = bits < remainingCount ? bits : remainingCount;
            
            // How much do we have to shift the read byte to just
            // get the high bits we want?
            int sourceShift = bits - bitsToCopy;
            
            // And how much do we have to shift those bits to graft
            // them onto our result?
            int targetShift = remainingCount - bitsToCopy;
 
            // Copy the bits           
            result |= (lastByte >> sourceShift) << targetShift;
 
            // Keep track of how many bits we have left
            remainingCount -= bitsToCopy;
            bits -= bitsToCopy;
                      
            // Now we need to mask off the bits we just copied from
            // lastByte.  Just keep the bits that are left.
            lastByte = lastByte & (0xff >> (8 - bits));           
        }
            
        return result;
    }
 
    @Override   
    public void close() throws IOException {
        in.close();
    }
    
    public static void main( String... args ) throws Exception {
        
        for( int count = 1; count <= 32; count++ ) {
            System.out.println( "Count:" + count );              
            byte[] bytes = new byte[] { (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78, (byte)0x9a, (byte)0xbc, (byte)0xde, (byte)0xff };
            int total = 8 * 8;
               
            ByteArrayInputStream bIn = new ByteArrayInputStream(bytes);
            BitInputStream in = new BitInputStream(bIn);
            
            int bitsRead = 0;
            while( bitsRead <= (total - count) ) {
                System.out.println( Integer.toHexString( in.readBits(count) ) );
                bitsRead += count;
            }
        }
               
    }
}
