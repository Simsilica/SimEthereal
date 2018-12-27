/*
 * $Id$
 * 
 * Copyright (c) 2018, Simsilica, LLC
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

package com.simsilica.util;

import java.util.*;

//import com.google.common.collect.Iterators;

/**
 *  A HashSet implementation that buffers modifications until a commit
 *  is performeed.  The buffering can be done from one thread and the
 *  reads from another.  So reads are safe from any thread but writes
 *  must be done from only one thread or properly synchronized externally.
 *  This is useful for efficiently buffering the output of one process
 *  for another.
 *
 *  Note: this doubles the storage requirements over a typical HashSet
 *  is it internally keeps two sets, one for the writer and one for all
 *  readers.
 *
 *  Also note: the semantics of the way reads and writes are decoupled
 *  mean that it is possible for add(element) to return false (indicating
 *  that the set already has the value) while contains(element) will also
 *  return false (because the element hasn't been committed yet).  The
 *  writing thread should use getTransaction() if it wants to check 
 *  transactional data integrity for some reason.
 *
 *  @author    Paul Speed
 */
public class BufferedHashSet<E> implements Set<E> {

    private HashSet<E> buffer = new HashSet<>();
    private volatile HashSet<E> delegate = new HashSet<>();
    private Thread writer = null;

    public BufferedHashSet() {
    }
    
    private boolean checkThread() {
        if( writer == null ) {
            writer = Thread.currentThread();
            return true;
        }
        return false;
    }

    private String badThreadMessage() {
        return "Non-write thread:" + Thread.currentThread() + " accessing as writer:" + writer;
    }
 
    /**
     *  Returns the transactional buffer.  Note: access to this
     *  is only thread safe from the writing thread.
     */
    public Set<E> getTransaction() {
        assert checkThread() : badThreadMessage();
        return buffer;
    }

    /**
     *  Called from the writing thread to apply the buffer changes
     *  to the readable view.
     */
    public void commit() {
        assert checkThread() : badThreadMessage();
        delegate = buffer;
        buffer = (HashSet<E>)delegate.clone(); 
    }

    /**
     *  Returns a snapshot of the readable view of this HashSet at the
     *  time this method is called.  Subsequent commits will not affect
     *  this view.  
     */
    public Set<E> getSnapshot() {
        return Collections.unmodifiableSet(delegate);
    } 
    
    @Override
    public int size() {
        return delegate.size(); 
    }
    
    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }
    
    @Override
    public boolean contains( Object o ) {
        return delegate.contains(o);
    }
 
    @Override
    public Iterator<E> iterator() {
        //return Iterators.unmodifiableIterator(delegate.iterator());
        return delegate.iterator();
    }
    
    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }
    
    @Override
    public <T> T[] toArray( T[] a ) {
        return delegate.toArray(a);
    }
 
    /**
     *  Updates the write buffer to reflect the addition of the specified element.
     *  This won't show up to the read methods until a commit() is performed.
     *  Callers wishing to see data in the buffer must use the Set returned
     *  from getTransaction().
     */   
    @Override
    public boolean add( E e ) {
        assert checkThread() : badThreadMessage();
        return buffer.add(e);
    }
    
    /**
     *  Updates the write buffer to reflect the removal of the specified element.
     *  This won't show up to the read methods until a commit() is performed.
     *  Callers wishing to see data in the buffer must use the Set returned
     *  from getTransaction().
     */   
    @Override
    public boolean remove( Object o ) {
        assert checkThread() : badThreadMessage();
        return buffer.remove(o);
    }
    
    @Override
    public boolean containsAll( Collection<?> c ) {
        return delegate.containsAll(c);
    }
    
    /**
     *  Updates the write buffer to reflect the removal of the specified elements.
     *  This won't show up to the read methods until a commit() is performed.
     *  Callers wishing to see data in the buffer must use the Set returned
     *  from getTransaction().
     */   
    @Override
    public boolean addAll( Collection<? extends E> c ) {
        return buffer.addAll(c);
    }
    
    /**
     *  Updates the write buffer to reflect the retention of the specified elements.
     *  This won't show up to the read methods until a commit() is performed.
     *  Callers wishing to see data in the buffer must use the Set returned
     *  from getTransaction().
     */   
    @Override
    public boolean retainAll( Collection<?> c ) {
        assert checkThread() : badThreadMessage();
        return buffer.retainAll(c);
    }
    
    /**
     *  Updates the write buffer to reflect the removal of the specified elements.
     *  This won't show up to the read methods until a commit() is performed.
     *  Callers wishing to see data in the buffer must use the Set returned
     *  from getTransaction().
     */   
    @Override
    public boolean removeAll( Collection<?> c ) {
        assert checkThread() : badThreadMessage();
        return buffer.removeAll(c);
    }
    
    /**
     *  Clears the write buffer.
     *  This won't show up to the read methods until a commit() is performed.
     *  Callers wishing to see data in the buffer must use the Set returned
     *  from getTransaction().
     */   
    @Override
    public void clear() {
        assert checkThread() : badThreadMessage();
        buffer.clear();
    }
    
    @Override
    public boolean equals( Object o ) {
        if( o == this ) {
            return true;
        }
        if( o == null || o.getClass() != getClass() ) {
            return false;
        }
        BufferedHashSet other = (BufferedHashSet)o;
        return Objects.equals(delegate, other.delegate);
    }
    
    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
    
    @Override
    public String toString() {
        return delegate.toString();
    }    
}
