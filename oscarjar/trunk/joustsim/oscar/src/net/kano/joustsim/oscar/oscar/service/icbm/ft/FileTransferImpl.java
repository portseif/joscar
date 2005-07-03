/*
 *  Copyright (c) 2005, The Joust Project
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
 */

package net.kano.joustsim.oscar.oscar.service.icbm.ft;

import net.kano.joscar.CopyOnWriteArrayList;
import net.kano.joscar.rv.RecvRvEvent;
import net.kano.joscar.rv.RvSession;
import net.kano.joscar.rv.RvSnacResponseEvent;
import net.kano.joscar.rvcmd.InvitationMessage;
import net.kano.joscar.rvcmd.sendfile.FileSendAcceptRvCmd;
import net.kano.joscar.rvcmd.sendfile.FileSendBlock;
import net.kano.joscar.rvcmd.sendfile.FileSendRejectRvCmd;
import net.kano.joscar.rvcmd.sendfile.FileSendReqRvCmd;
import net.kano.joscar.snaccmd.icbm.RvCommand;
import net.kano.joustsim.oscar.oscar.service.icbm.RendezvousSessionHandler;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.StateController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.ControllerListener;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.FileTransferEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.EventPost;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.ConnectedEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.ConnectingEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.ConnectingToProxyEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.ResolvingProxyEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.WaitingForConnectionEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.TransferringFileEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.FileCompleteEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.state.StateInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

public abstract class FileTransferImpl
        implements FileTransfer, TransferPropertyHolder,
        RvSessionBasedTransfer, StateBasedTransfer, CachedTimerHolder {

    private FileSendBlock fileInfo;
    private InvitationMessage message;
    private Timer timer = new Timer("File transfer timer");
    private RendezvousSessionHandler rvSessionHandler;
    private RvSession session;
    private StateController controller = null;
    private Map<Key<?>,Object> transferProperties
            = new HashMap<Key<?>, Object>();
    private FileTransferManager fileTransferManager;

    private CopyOnWriteArrayList<FileTransferListener> listeners
            = new CopyOnWriteArrayList<FileTransferListener>();
    private FileTransferState state = FileTransferState.WAITING;
    private boolean cancelled = false;
    private EventPost eventPost = new EventPost() {
        public void fireEvent(FileTransferEvent event) {
            boolean fireState;
            FileTransferState newState = null;
            synchronized (FileTransferImpl.this) {
                if (event instanceof ConnectingEvent
                        || event instanceof ConnectingToProxyEvent
                        || event instanceof ResolvingProxyEvent
                        || event instanceof WaitingForConnectionEvent) {
                    newState = FileTransferState.CONNECTING;

                } else if (event instanceof ConnectedEvent) {
                    newState = FileTransferState.CONNECTED;

                } else if (event instanceof TransferringFileEvent
                        || event instanceof FileCompleteEvent) {
                    newState = FileTransferState.TRANSFERRING;
                }
                if (newState != null && newState != state) {
                    fireState = true;
                    state = newState;
                } else {
                    fireState = false;
                }
            }
            if (fireState) {
                for (FileTransferListener listener : listeners) {
                    listener.handleEventWithStateChange(FileTransferImpl.this,
                            newState, event);
                }
            } else {
                for (FileTransferListener listener : listeners) {
                    listener.handleEvent(FileTransferImpl.this, event);
                }
            }
        }
    };

    protected FileTransferImpl(FileTransferManager fileTransferManager,
            RvSession session) {
        this.fileTransferManager = fileTransferManager;
        this.session = session;
        rvSessionHandler = createSessionHandler();
    }

    public synchronized FileTransferState getState() {
        return state;
    }

    protected abstract FtRvSessionHandler createSessionHandler();

    protected synchronized void setInvitationMessage(InvitationMessage message) {
        this.message = message;
    }

    protected synchronized void setFileInfo(FileSendBlock fileInfo) {
        this.fileInfo = fileInfo;
    }

    public Timer getTimer() { return timer; }

    public synchronized FileSendBlock getFileInfo() { return fileInfo; }

    public synchronized InvitationMessage getInvitationMessage() { return message; }

    protected void startStateController(final StateController controller) {
//        StateController oldController = this.controller;
//        if (oldController != null) {
//            throw new IllegalStateException("Cannot start state controller: "
//                    + "controller is already set to " + oldController);
//        }
        changeStateController(controller);
    }

    protected void changeStateController(final StateController controller) {
        StateController last;
        synchronized (this) {
            if (cancelled) return;
            last = this.controller;
            this.controller = controller;
            controller.addControllerListener(new ControllerListener() {
                public void handleControllerSucceeded(StateController c,
                        StateInfo info) {
                    goNext();
                }

                public void handleControllerFailed(StateController c,
                        StateInfo info) {
                    goNext();
                }

                private void goNext() {
                    controller.removeControllerListener(this);
                    changeStateController(getNextStateController());
                }
            });
        }
        if (last != null) last.stop();
        controller.start(this, last);
    }

    public synchronized StateController getStateController() { return controller; }

    protected RendezvousSessionHandler getRvSessionHandler() {
        return rvSessionHandler;
    }

    public RvSession getRvSession() {
        return session;
    }

    public synchronized <V> void putTransferProperty(Key<V> key, V value) {
        transferProperties.put(key, value);
    }

    public synchronized <V> V getTransferProperty(Key<V> key) {
        return (V) transferProperties.get(key);
    }

    public FileTransferManager getFileTransferManager() {
        return fileTransferManager;
    }

    public void cancel() {
        StateController controller;
        synchronized(this) {
            if (cancelled) return;
            cancelled = true;
            controller = this.controller;
        }
        controller.stop();
    }

    public void addTransferListener(FileTransferListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeTransferListener(FileTransferListener listener) {
        listeners.remove(listener);
    }

    public EventPost getEventPost() {
        return eventPost;
    }


    protected abstract class FtRvSessionHandler
            implements RendezvousSessionHandler {
        public final void handleRv(RecvRvEvent event) {
            RvCommand cmd = event.getRvCommand();
            if (cmd instanceof FileSendReqRvCmd) {
                FileSendReqRvCmd reqCmd = (FileSendReqRvCmd) cmd;
                handleIncomingRequest(event, reqCmd);

            } else if (cmd instanceof FileSendAcceptRvCmd) {
                FileSendAcceptRvCmd acceptCmd = (FileSendAcceptRvCmd) cmd;
                handleIncomingAccept(event, acceptCmd);

            } else if (cmd instanceof FileSendRejectRvCmd) {
                FileSendRejectRvCmd rejectCmd = (FileSendRejectRvCmd) cmd;
                handleIncomingReject(event, rejectCmd);
            }
        }

        protected abstract void handleIncomingReject(RecvRvEvent event,
                FileSendRejectRvCmd rejectCmd);

        protected abstract void handleIncomingAccept(RecvRvEvent event,
                FileSendAcceptRvCmd acceptCmd);

        protected abstract void handleIncomingRequest(RecvRvEvent event,
                FileSendReqRvCmd reqCmd);

        public void handleSnacResponse(RvSnacResponseEvent event) {
        }

        protected FileTransfer getFileTransfer() {
            return FileTransferImpl.this;
        }
    }

}