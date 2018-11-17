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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.*;

import com.jme3.network.HostedConnection;

import com.simsilica.ethereal.ConnectionStats;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.ethereal.zone.ZoneKey;


/**
 *  Buffers the state information, flushing it to real messages
 *  when it exceeds a certain message threshold.
 *
 *  @author    Paul Speed
 */
public class StateWriter {

    static Logger log = LoggerFactory.getLogger(StateWriter.class);

    /**
     *  The connection to send state through.
     */
    private final HostedConnection conn;
    private final ObjectStateProtocol objectProtocol;     

    // Track the state packets that we've sent to the client.
    // We will look these up again when we receive an ACK (and purge the
    // out of order ones)
    private final LinkedList<SentState> sentStates = new LinkedList<>();
   
    // Track the ACKs we've received but that we've haven't received
    // a double-ack for yet.  We include these in every message header
    // until we see an ACK for a message that already included them. 
    private final Set<Integer> receivedAcks = new TreeSet<>();
    private int[] receivedAcksArray = null;

    // Frame header information.
    private long frameTime;
    private long legacySequence;
    private ZoneKey centerZone;    
    private long centerZoneId;
 
    // The accumulate state that we will flush when required   
    private FrameState currentFrame;   
 
 
    private static final int UDP_HEADER = 50;
    private static final int SM_HEADER = 5;    
    private int mtu = 1500;
    private int bufferSize = mtu - UDP_HEADER - SM_HEADER; // 8 bytes of slop for internal protocol
 
    private SentState outbound;
    private int nextMessageId = 0;
    private int headerBits;
    private int estimatedSize;
 
    // Time source we will use for timestamping outbound messages.
    // This time should be compatible with the times provided by the 
    // frame updates or things will get weird.
    private TimeSource timeSource;
    
    private ConnectionStats stats;
    
    public StateWriter( HostedConnection conn, ObjectStateProtocol objectProtocol, TimeSource timeSource, ConnectionStats stats ) {
        this.conn = conn;
        this.objectProtocol = objectProtocol;
        this.timeSource = timeSource;
        this.stats = stats;   
    }
    
    /**
     *  Sets the desired maximum message size.  Typically the ideal situation
     *  would be to keep this under the MTU of the connection.  By default,
     *  StateWriter estimates this as 1500 bytes.  It can vary and there is no
     *  direct way to know.
     *  Set this to a really large value to avoid ever splitting messages.
     */
    public void setMaxMessageSize( int max ) {
        this.mtu = max;
        this.bufferSize = mtu - UDP_HEADER - SM_HEADER; // 8 bytes of slop for internal protocol
    }
    
    public int getMaxMessageSize() {
        return mtu;
    }     

    public SentState ackSentState( int messageId ) {
    
        if( sentStates.isEmpty() ) {
            return null;
        }
 
        // Go through the kept messages until we get the one
        // we are looking for.  We purge any that are older than
        // we are looking for because they are 'out of order' and
        // we ignore them.  The latest state always supersedes it
        // anyway.
        for( Iterator<SentState> it = sentStates.iterator(); it.hasNext(); ) {
            SentState s = it.next();
            
            if( s.messageId == messageId ) {
                // This is the one we wanted to see
                
                // So, we have received an ACK for a message that we
                // sent previously.  This means that the client really does 
                // have that message... and thus all of the double-ACKs we
                // sent as part of that message.  We don't need to send them
                // anymore and can remove them from our ACK header
                if( s.acked != null ) {
                    for( int ack : s.acked ) {
                        receivedAcks.remove(ack);
                        receivedAcksArray = null;
                    }
                }
                
                // Now we need to start ACKing this message
                receivedAcks.add(messageId);
                receivedAcksArray = null;
                
                // This message has been fully handled now, we can
                // remove it from tracking
                it.remove();
                return s;
            }
            
            // If our passed messageId is before this message's ID then
            // it is in the future and we will not find what we are looking
            // for.  The sent states list is in send order.
            if( messageId < s.messageId ) { //isBefore(messageId, s.messageId) ) {
                // Probably we received this messageId after we purged
                // it from getting a later messageId. ie: out of order messages.
                
                // It occurs to me that if messageId is newer than anything we've
                // seen so far then this is a bug.  ie: the server says 'use this
                // latest state' and for some reason we don't have it.  However,
                // there is no reason I can think of that we shouldn't have it.
                // (Note: by 'anything we've seen so far' I don't mean in this loop,
                //  I mean 'ever'... a high water mark.)
                //
                // It does point out an interesting possibility to potentially
                // ignore acks older than a certain level and search back from
                // newest first (the caller that is) but one message might not
                // have the complete state that another has part of.
                
                return null;
            }
            
            // Finally, remove this element as it's older than what we've
            // been searching for and we only want the latest stuff
            it.remove();             
        }
 
        // We didn't find it.
        return null; 
    }

    public void startFrame( long time, ZoneKey centerZone ) throws IOException {
    
        // Watchdog to check for mismatched time sources.
        long delta = Math.abs(time - timeSource.getTime()); 
        if( delta > 1000000000 ) {
            // more then a second difference means they are waaaaaay off.
            // Even a ms difference would be large.
            log.warn("Mismatched time sources.  Delta:" + (delta/1000000000.0) + " seconds");
        }
    
        // End any previous frame that we might be in the middle of    
        endFrame();
        
        // Make sure we have a current message started
        startMessage();
            
        this.frameTime = time;
        this.centerZone = centerZone;
        this.centerZoneId = centerZone != null ? centerZone.toLongId() : -1;
        this.legacySequence = time & 0xffffffffffff00L;
    }
    
    public void addState( ObjectState state ) throws IOException {
        if( currentFrame == null ) {
            if( frameTime == 0 ) {
                throw new IllegalStateException("Frame time is 0 as if not initialized.");
            }
            // Then create the frame
            currentFrame = new FrameState(frameTime, legacySequence++, centerZoneId);
        }        
            
        currentFrame.addState(state, objectProtocol);
    }

    protected void startMessage() {

        if( outbound != null ) { 
            return;
        }
        
        // Just in case we'll put a watchdog in here.  The reason this
        // is unlikely to trigger is because the receivedAcks set only
        // grows when we've received a message from the client.  And at
        // that point we get to remove every receivedAck the client 
        // confirms.  So one new ID should always nearly empty the set.
        int size = receivedAcks.size(); 
        if( size >= 128 ) {
            throw new RuntimeException( "Very bad things have happened in the receivedAcks set." );
        }       
 
        // Build the ACKs array
        if( receivedAcksArray == null ) {
            receivedAcksArray = new int[size];
            int index = 0;
            for( Integer s : receivedAcks ) {
                receivedAcksArray[index++] = s;
            }
        }         
 
        this.outbound = new SentState(-1, receivedAcksArray, new ArrayList<FrameState>());
        this.headerBits = outbound.getEstimatedHeaderSize();
        this.estimatedSize = headerBits;       
    }

    /**
     *  Adds the frame to an existing outbound message or closes/sends
     *  the previous outbound message and starts a new one.
     */
    protected void endFrame() throws IOException {
    
        if( currentFrame == null ) {
            // Nothing to do
            return;
        }    

        // One of the following cases is true:
        // 1) we can fit the frame in the current message.
        // 2) we cannot fit the frame in the current message
        //    but we have other frames in this message already.
        // 3) we cannot fit the frame in the current message
        //    and we have no other frames.

        long frameSize = currentFrame.getEstimatedBitSize() + 1; // extra 1 for the null bit
        long bitsRemaining = (bufferSize * 8) - estimatedSize;
        if( frameSize < bitsRemaining ) {
            // This frame will fit in the current message
            outbound.frames.add(currentFrame);
            estimatedSize += frameSize;
            currentFrame = null;
            return;
        }
                   
        // Split the frame or send it completely in the next
        // message... either way, end the current message.
        // 
        // Basic logic:
        //  -while still stuff in frame
        //      -endMessage
        //      -write as much of frame as we can
        FrameState frame = currentFrame;
        while( frame != null ) {
            // If there were already frames queued to go then we need
            // to close them out and send them.  This frame won't fit in 
            // the message as is and we'd like to avoid splitting it if we can.
            // So we close this message out and start a new one... and hope it fits.
            if( !outbound.frames.isEmpty() ) {
                // Then we need to flush the current message first
                endMessage();
            }
            
            // Make sure there is a message started if needed
            startMessage();

            // I'm adding this recalculation of the bitsRemaining because
            // it seems right... but I'm fixing this probably a year or more
            // after writing it so I feel the need to leave a comment.
            // The next line that splits the frame would seem to need to know
            // the accurate size.
            // Justification: there are two case we are handling when entering
            // here:
            // 1) There were other frames in an existing message and we flushed
            //      them with endMessage() and started a new message with startMessage().
            //      In that case, bitsRemaining is way wrong because it was calculated
            //      from the old unfinished message.
            // 2) There were no other frames and we still can't fit it in one message.
            //      In which case, bitsRemaining is accurate because it just happens
            //      to be just the bufferSize - header.  Either way, it doesn't do
            //      any harm to calculate again.
            bitsRemaining = (bufferSize * 8) - estimatedSize;
            FrameState split = frame.split(bitsRemaining, objectProtocol);
            
            // Add the unsplit part to the current message
            outbound.frames.add(frame);
            
            // Need to update the estimated size because we may leave the message
            // open when dropping out of this loop.  ie: maybe we only wrote a tiny last
            // part of a frame and there is still room for more frames later.
            //estimatedSize += frameSize;
            // However, the old line was as above and not using the actual added
            // frame size if we were making a second pass through this loop.
            // This can happen when the frame was too big for even one message.
            frameSize = frame.getEstimatedBitSize() + 1; 
            estimatedSize += frameSize; 
 
            // At this point, we've either fully added the frame to the next
            // outbound message or we had to split it.  If we full added it then
            // the message stays open to see if the next frame will fit... we fall
            // out of this loop.
            // If the frame did NOT fully fit then we got again using the split
            // off part as the new outbound frame... thus all of the bitsRemaining
            // and estimatedSize fixes I made above (2016/06/14).
            // My own testing prior to this probably never had a frame that spanned
            // more than one message and so never hit this.  At last calculation,
            // it takes something like 80 object updates to exceed a message size.
            // And to trigger this bug, we'd have to not only exceed that but
            // also have existing smaller frames already ready to go.
            // (Queued up outbound frames would have made bitsRemaining a small
            // number versus no frames queued in which the bitsRemaining would
            // have been accurate... because empty queue = max bitsRemaining.)
            // Though I guess the partial end of frame we wrote could set us
            // up on the next endFrame() with items already in the queue and an
            // inaccurate bitsRemaining again... so this error case is probably
            // pretty common but for some reason didn't show up in my own meager 
            // stress testing.  In the best case, bitsRemaining is wrong but still
            // large enough to fit more data into.  I guess the problem comes when
            // bitsRemaining from whatever was waiting in the outbound queue would
            // be so small that no amount of splitting would fit.  The frame split logic
            // assumes that at least one object update will fit so if we gave it a
            // limit of say, 5 then it would never resolve properly. (Actually, looking
            // at the code it will throw an exception.)  In the case where the message
            // can't be split with the bits remaining we may want to just send the
            // whole frame.  I worry that if we have too many acks built up and a really
            // small MTU that maybe we hit the same issue of non-split... but at least
            // we'll get an exception.
            //
            // Note: The first pass through the loop, calculating bitsRemaining is
            // redundant but costs little. 
 
            // TODO: add a split counter to a statistics object
            
            // It occurs to me that we can potentially _greatly_ reduce the size
            // of our frame states if we remove the redundant information.
            //
            // For example, here is one string dump of an outbound SentState object:
            // 
            // SentState[messageId=-1, created=2066993764023919, frames=[
            //   FrameState[sequence=2066993714059008, columnId=-1, states=[
            //      espace.ethereal.net.ObjectState[id=10, realId=39, zoneId=2, positionBits=cec400004e3d, rotationBits=f04bd5800800]]], 
            //   FrameState[sequence=2066993730678528, columnId=-1, states=[
            //      espace.ethereal.net.ObjectState[id=10, realId=39, zoneId=2, positionBits=cec400004e3d, rotationBits=f04bd5800800]]], 
            //   FrameState[sequence=2066993746304256, columnId=-1, states=[
            //      espace.ethereal.net.ObjectState[id=10, realId=39, zoneId=2, positionBits=cec400004e3d, rotationBits=f04bd5800800]]], 
            //   FrameState[sequence=2066993762899968, columnId=-1, states=[
            //      espace.ethereal.net.ObjectState[id=10, realId=39, zoneId=2, positionBits=cec400004e3d, rotationBits=f04bd5800800]]]]]            
            //
            // Notice all of the redundant information.  Compressing it could get kind
            // of tricky but it's worth thinking about.  It would require a lot more 
            // tracking than we are doing to do it on the fly and we can't count on the
            // caller to do it because they wouldn't know where we'll break the messages.
            // Still, it's very tantalzing.  That SentState is 169 bytes and could be reduced
            // to at least 1/3rd of that if not a bit less.
            //
            // Random thoughts on that subject:
            // -because we don't know when we'll break the message we'll have to
            //    do redundant calculations to estimate whether a frame will fit in a
            //    message or not to know when to split it.
            // -alternately, we could provide some way of removing a frame once added
            //    if it turns out to bump us past our max size.  This at least prevents
            //    the double-calc in most cases.
            // -splitting a single frame becomes a little more 'interesting' in that case.
            //
            // Note: I'm leaving the above for posterity but the reality is that
            // messages will rarely if ever be so uniform.  The above was caused by a bug
            // where object positions weren't being updated properly.  In a real message,
            // the realId, zoneId, etc. will already be very stable and thus reduce to 1-bit.
            // position and rotation will continuously update and when they don't it is likely
            // that the object won't even get any state updates.
            // It's kind of a relief, really.    
 
            if( split != null ) {
                // The frame was big enough that it wouldn't fit in a new
                // message so we'll try the rest in another pass.
                //System.out.println( "Splitting frame.  remaining:" + split.states.size() );
            }
            frame = split;                
        }
        
        currentFrame = null;
    }

    protected void endMessage() throws IOException {
    
        //long timestamp = System.nanoTime();
        long timestamp = timeSource.getTime();
        int id = nextMessageId++;    

        ObjectStateMessage msg = new ObjectStateMessage(id, timestamp, null);        
        msg.setState(outbound, objectProtocol);
        sentStates.add(outbound);
  
        conn.send(msg);
  
        stats.addMessageSize(ObjectStateMessage.HEADER_SIZE + msg.getBuffer().length);
        
        outbound = null;
    }
    
    public void flush() throws IOException {
        endFrame();
 
        if( outbound == null ) {    
            return; // already flushed
        }
        
        endMessage();
    }
}

