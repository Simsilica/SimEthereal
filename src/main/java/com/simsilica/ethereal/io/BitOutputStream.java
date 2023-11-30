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
 *  Writes bit strings of any length to an
 *  underlying stream.
 *
 *  @version   $Revision: 4022 $
 *  @author    Paul Speed
 */
public class BitOutputStream implements AutoCloseable {
    private final OutputStream out;
    private int currentByte = 0;
    private int bits = 8;
    
    public BitOutputStream( OutputStream out ) {
        this.out = out;
    }
    
    public int getPendingBits() {
        return bits;
    }
 
    public void writeBits( int value, int count ) throws IOException {
        if( count == 0 )
            throw new IllegalArgumentException( "Cannot write 0 bits." );

        // Make sure the value is clean of extra high bits
        value = value & (0xffffffff >>> (32 - count));
    
        int remaining = count;
        while( remaining > 0 ) {
            int bitsToCopy = bits < remaining ? bits : remaining;
 
            int sourceShift = remaining - bitsToCopy;
            int targetShift = bits - bitsToCopy;
            
            currentByte |= (value >>> sourceShift) << targetShift;
            
            remaining -= bitsToCopy;
            bits -= bitsToCopy;                      
            
            value = value & (0xffffffff >>> (32 - remaining));
                        
            // If there are no more bits left to write to in our
            // working byte then write it out and clear it.
            if( bits == 0 ) {
                flush();                
            }
        }   
    }

    public void writeLongBits( long value, int count ) throws IOException {
        if( count == 0 )
            throw new IllegalArgumentException( "Cannot write 0 bits." );

        // Make sure the value is clean of extra high bits
        value = value & (0xffffffffffffffffL >>> (64 - count));
    
        int remaining = count;
        while( remaining > 0 ) {
            int bitsToCopy = bits < remaining ? bits : remaining;
 
            int sourceShift = remaining - bitsToCopy;
            int targetShift = bits - bitsToCopy;
            
            currentByte |= (value >>> sourceShift) << targetShift;
            
            remaining -= bitsToCopy;
            bits -= bitsToCopy;                      
 
            value = value & (0xffffffffffffffffL >>> (64 - remaining));
                        
            // If there are no more bits left to write to in our
            // working byte then write it out and clear it.
            if( bits == 0 ) {
                flush();                
            }
        }   
    }
 
    protected void flush() throws IOException {
        out.write(currentByte);
        bits = 8;
        currentByte = 0;
    }
 
    @Override   
    public void close() throws IOException {
        flush();
        out.close();
    }
    
    public static void main( String... args ) throws Exception {
        test2();
    }
 
    public static void test2() throws Exception {
        byte[] bytes = new byte[] { (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78, (byte)0x9a, (byte)0xbc, (byte)0xde, (byte)0xff };

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        BitOutputStream out = new BitOutputStream(bOut);
        for( int i = 0; i < bytes.length; i++ ) {
            out.writeBits( 1, 1 );
            out.writeBits( bytes[i], 8 );
            out.writeLongBits( 0x123456789abcdef0L, 64 );
            out.writeLongBits( -1, 64 );
            out.writeLongBits( 0x80123456789abcdeL, 64 );
        }
        out.close();
        
        byte[] toRead = bOut.toByteArray();
        System.out.println( "Written length:" + toRead.length );
        
        ByteArrayInputStream bIn = new ByteArrayInputStream(toRead);
        BitInputStream in = new BitInputStream(bIn);
        for( int i = 0; i < bytes.length; i++ ) {
            int test = in.readBits(1);
            int val = in.readBits(8);
            long l1 = in.readLongBits(64);
            long l2 = in.readLongBits(64);
            long l3 = in.readLongBits(64);
            System.out.print( "[" + Integer.toHexString( val ) + "]" );
            System.out.println( "(" + Long.toHexString(l1) + ", " + Long.toHexString(l2) + ", " + Long.toHexString(l3) + ")" );
        }
        System.out.println();
    }
    
    public static void test1() throws Exception {
        
        for( int count = 1; count <= 32; count++ ) {
            System.out.println( "Count:" + count );              
            byte[] bytes = new byte[] { (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78, (byte)0x9a, (byte)0xbc, (byte)0xde, (byte)0xff };
            int total = 8 * 8;
               
            ByteArrayInputStream bIn = new ByteArrayInputStream(bytes);
            BitInputStream in = new BitInputStream(bIn);
 
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            BitOutputStream out = new BitOutputStream(bOut);
            
            int bitsRead = 0;
            while( bitsRead <= (total - count) ) {
                int val = in.readBits(count);
                out.writeBits( val, count );
                //System.out.println( Integer.toHexString( in.readBits(count) ) );
                bitsRead += count;
            }
                
            byte[] result = bOut.toByteArray();
            for( int i = 0; i < result.length; i++ )
                System.out.print( "[" + Integer.toHexString(result[i] & 0xff) + "]" );
            System.out.println();
        }
    }
}
