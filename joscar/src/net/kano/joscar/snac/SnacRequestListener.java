/*
 *  Copyright (c) 2002-2003, The Joust Project
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions 
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer. 
 *  - Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution. 
 *  - Neither the name of the Joust Project nor the names of its
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 *  File created by keith @ Feb 17, 2003
 *
 */

package net.kano.joscar.snac;

/**
 * Provides an interface for listening for events related to a specific
 * <code>SnacRequest</code>.
 *
 * @see SnacRequest#addListener
 */
public interface SnacRequestListener {
    /**
     * Called when an outgoing SNAC request was sent over a SNAC connection.
     * This method will be called only once for each request this listener has
     * been added to.
     *
     * @param e an object describing the event
     */
    void handleSent(SnacRequestSentEvent e);

    /**
     * Called when a SNAC packet is received in response to a request. This may
     * be called multiple times, as a request can have more than one response.
     *
     * @param e an object describing the event
     */
    void handleResponse(SnacResponseEvent e);

    /**
     * Called when a SNAC packet "times out" and is removed from the request
     * list of its parent <code>SnacProcessor</code>. This will be called only
     * once for each request to which this listener has been added.
     *
     * @param event an object describing the event
     */
    void handleTimeout(SnacRequestTimeoutEvent event);
}