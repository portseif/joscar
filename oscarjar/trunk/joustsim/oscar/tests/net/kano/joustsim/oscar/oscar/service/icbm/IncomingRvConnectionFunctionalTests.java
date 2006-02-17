/*
 * Copyright (c) 2006, The Joust Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in
 *   the documentation and/or other materials provided with the
 *   distribution.
 * - Neither the name of the Joust Project nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * File created by keithkml
 */

package net.kano.joustsim.oscar.oscar.service.icbm;

import net.kano.joscar.rvcmd.RvConnectionInfo;
import net.kano.joscar.rvcmd.SegmentedFilename;
import net.kano.joscar.rvproto.ft.FileTransferHeader;
import net.kano.joustsim.TestHelper;
import static net.kano.joustsim.TestHelper.findInstances;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.Checksummer;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.ConnectionType;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.DefaultRvConnectionEventListener;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.RvConnection;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.RvConnectionEventListener;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.RvConnectionState;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.AbstractConnectionController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.ConnectToProxyForIncomingController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.IncomingFileTransferPlumber;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.OutgoingConnectionController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.RedirectConnectionController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.RedirectToProxyController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.StateController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.TransferredFile;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.Transferrer;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.RvConnectionEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.StartingControllerEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.state.StateInfo;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.state.StreamInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class IncomingRvConnectionFunctionalTests extends RvConnectionTestCase {
  private MockIncomingRvConnection conn;

  protected void setUp() throws Exception {
    conn = new MockIncomingRvConnection();
  }

  protected void tearDown() throws Exception {
    conn = null;
  }

  protected MockRvConnection getConnection() {
    return conn;
  }

  protected int getBaseOutgoingRequestId() {
    return 2;
  }

  public void testIncomingLanConnection() {
    addNopConnector(conn);
    generateRequestAndWaitForStream();
    assertTrue(TestHelper.findOnlyInstance(conn.getHitControllers(),
        OutgoingConnectionController.class).getTimeoutType()
        == ConnectionType.LAN);
    assertSentRvs(0, 1, 0);
  }

  public void testIncomingInternetConnection() {
    conn.addEventListener(new DefaultRvConnectionEventListener() {
      public void handleEvent(RvConnection transfer, RvConnectionEvent event) {
        if (event instanceof StartingControllerEvent) {
          StateController controller = ((StartingControllerEvent) event)
              .getController();
          if (controller instanceof OutgoingConnectionController) {
            OutgoingConnectionController ogc
                = (OutgoingConnectionController) controller;
            ConnectionType type = ogc.getTimeoutType();
            if (type == ConnectionType.LAN) {
              ogc.setConnector(new FailConnector());

            } else if (type == ConnectionType.INTERNET) {
              ogc.setConnector(new NopConnector());

            } else {
              throw new IllegalStateException();
            }
          }
        }
      }
    });
    generateRequestAndWaitForStream();
    List<OutgoingConnectionController> controllers
        = findInstances(conn.getHitControllers(),
        OutgoingConnectionController.class);
    assertEquals(2, controllers.size());
    assertTrue(controllers.get(0).getTimeoutType() == ConnectionType.LAN);
    assertTrue(controllers.get(1).getTimeoutType() == ConnectionType.INTERNET);
    assertSentRvs(0, 1, 0);
  }

  public void testIncomingProxyConnection() {
    conn.addEventListener(new RvConnectionEventListener() {
      public void handleEventWithStateChange(RvConnection transfer,
          RvConnectionState state, RvConnectionEvent event) {
      }

      public void handleEvent(RvConnection transfer, RvConnectionEvent event) {
        if (event instanceof StartingControllerEvent) {
          StateController controller = ((StartingControllerEvent) event)
              .getController();
          if (controller instanceof OutgoingConnectionController) {
            OutgoingConnectionController ogc
                = (OutgoingConnectionController) controller;
            ogc.setConnector(new NeverConnector(ogc.getConnector()));

          } else if (controller instanceof ConnectToProxyForIncomingController) {
            ConnectToProxyForIncomingController proxyconn
                = (ConnectToProxyForIncomingController) controller;
            proxyconn.setConnector(getDirectedToProxyConnector());
          }
        }
      }
    });
    generateRequestAndWaitForStream();
    assertNotNull(TestHelper.findOnlyInstance(conn.getHitControllers(),
        ConnectToProxyForIncomingController.class));
    assertSentRvs(0, 1, 0);
  }

  public void testIncomingConnectionWeRedirect() {
    conn.addEventListener(new DefaultRvConnectionEventListener() {
      public void handleEvent(RvConnection transfer, RvConnectionEvent event) {
        if (event instanceof StartingControllerEvent) {
          StateController controller = ((StartingControllerEvent) event)
              .getController();
          if (controller instanceof OutgoingConnectionController) {
            OutgoingConnectionController ogc
                = (OutgoingConnectionController) controller;
            ogc.setConnector(new FailConnector());

          } else if (controller instanceof RedirectConnectionController) {
            RedirectConnectionController redir
                = (RedirectConnectionController) controller;
            redir.setConnector(new MockPassiveConnector());
          }
        }
      }
    });
    StateInfo end = generateRequestAndRun(conn);

    assertTrue("End was " + end, end instanceof StreamInfo);
    List<StateController> hit = conn.getHitControllers();
    List<OutgoingConnectionController> controllers = findInstances(hit,
        OutgoingConnectionController.class);
    assertEquals(2, controllers.size());
    assertTrue(controllers.get(0).getTimeoutType() == ConnectionType.LAN);
    assertTrue(controllers.get(1).getTimeoutType() == ConnectionType.INTERNET);
    assertEquals(1, findInstances(hit,
        RedirectConnectionController.class).size());
    assertSentRvs(1, 1, 0);
  }

  public void testIncomingConnectionWeRedirectToProxy() {
    conn.addEventListener(new DefaultRvConnectionEventListener() {
      public void handleEvent(RvConnection transfer, RvConnectionEvent event) {
        if (event instanceof StartingControllerEvent) {
          StateController controller = ((StartingControllerEvent) event)
              .getController();
          if (controller instanceof OutgoingConnectionController
              || controller instanceof RedirectConnectionController) {
            AbstractConnectionController ogc
                = (AbstractConnectionController) controller;
            ogc.setConnector(new FailConnector());

          } else if (controller instanceof RedirectToProxyController) {
            RedirectToProxyController redir
                = (RedirectToProxyController) controller;
            redir.setConnector(getInitiateProxyConnector());
          }
        }
      }
    });
    StateInfo end = generateRequestAndRun(conn);

    assertTrue("End was " + end, end instanceof StreamInfo);
    List<StateController> hit = conn.getHitControllers();
    List<OutgoingConnectionController> controllers = findInstances(hit,
        OutgoingConnectionController.class);
    assertEquals(2, controllers.size());
    assertTrue(controllers.get(0).getTimeoutType() == ConnectionType.LAN);
    assertTrue(controllers.get(1).getTimeoutType() == ConnectionType.INTERNET);
    assertEquals(1, findInstances(hit, RedirectConnectionController.class).size());
    assertEquals(1, findInstances(hit, RedirectToProxyController.class).size());
    assertSentRvs(2, 1, 0);
  }

  public void testIncomingConnectionBuddyRedirects() throws UnknownHostException {
    final HangConnector hangConnector = new HangConnector();
    conn.addEventListener(new DefaultRvConnectionEventListener() {
      public void handleEvent(RvConnection transfer, RvConnectionEvent event) {
        if (event instanceof StartingControllerEvent) {
          StateController controller = ((StartingControllerEvent) event)
              .getController();
          if (controller instanceof OutgoingConnectionController
              || controller instanceof RedirectConnectionController) {
            AbstractConnectionController ogc
                = (AbstractConnectionController) controller;
            try {
              if (conn.getRvSessionInfo().getConnectionInfo().getInternalIP()
                  .equals(InetAddress.getByName("40.40.40.40"))) {
                ogc.setConnector(new NopConnector());
              } else {
                ogc.setConnector(hangConnector);
              }
            } catch (UnknownHostException e) {
              throw new IllegalArgumentException(e);
            }
          }
        }
      }
    });
    List<StateController> hit = conn.getHitControllers();
    RvConnectionInfo conninfo = new RvConnectionInfo(
        InetAddress.getByName("40.40.40.40"),
        InetAddress.getByName("41.41.41.41"), null, 10, false, false);
    StateInfo end = simulateBuddyRedirectionAndWait(conn, hangConnector, conninfo);

    assertTrue("End was " + end, end instanceof StreamInfo);
    List<OutgoingConnectionController> controllers = findInstances(hit,
        OutgoingConnectionController.class);
    assertEquals(2, controllers.size());
    assertTrue(controllers.get(0).getTimeoutType() == ConnectionType.LAN);
    assertTrue(controllers.get(1).getTimeoutType() == ConnectionType.LAN);
    assertSentRvs(0, 2, 0);
  }

  public void testIncomingConnectionBuddyRedirectsToProxy() throws UnknownHostException {
    final HangConnector hangConnector = new HangConnector();
    conn.addEventListener(new DefaultRvConnectionEventListener() {
      public void handleEvent(RvConnection transfer, RvConnectionEvent event) {
        if (event instanceof StartingControllerEvent) {
          StateController controller = ((StartingControllerEvent) event)
              .getController();
          if (controller instanceof AbstractConnectionController) {
            AbstractConnectionController ogc
                = (AbstractConnectionController) controller;
            try {
              if (InetAddress.getByName("60.60.60.60").equals(
                  conn.getRvSessionInfo().getConnectionInfo().getProxyIP())) {
                ogc.setConnector(getDirectedToProxyConnector());
              } else {
                ogc.setConnector(hangConnector);
              }
            } catch (UnknownHostException e) {
              throw new IllegalArgumentException(e);
            }
          }
        }
      }
    });
    List<StateController> hit = conn.getHitControllers();
    RvConnectionInfo conninfo = RvConnectionInfo.createForOutgoingProxiedRequest(
        InetAddress.getByName("60.60.60.60"), 10);
    StateInfo end = simulateBuddyRedirectionAndWait(conn, hangConnector, conninfo);

    assertTrue("End was " + end, end instanceof StreamInfo);
    List<OutgoingConnectionController> controllers
        = findInstances(hit, OutgoingConnectionController.class);
    assertEquals(1, controllers.size());
    assertTrue(controllers.get(0).getTimeoutType() == ConnectionType.LAN);
    assertNotNull(TestHelper.findOnlyInstance(hit,
        ConnectToProxyForIncomingController.class));
    assertSentRvs(0, 2, 0);
  }

  public void testIncomingRequestWeImmediatelyReject() {
    conn.setAutoMode(AutoMode.REJECT);
    StateInfo end = generateRequestAndRun(conn);

    assertNull(end);
    assertTrue(conn.getHitControllers().isEmpty());
    assertSentRvs(0, 0, 1);
  }

  private static class MyIncomingFileTransferPlumber
      implements IncomingFileTransferPlumber {
    private List<FileTransferHeader> sent = new ArrayList<FileTransferHeader>();
    private BlockingQueue<FileTransferHeader> toread
        = new LinkedBlockingQueue<FileTransferHeader>();

    @SuppressWarnings({"ReturnOfCollectionOrArrayField"})
    public List<FileTransferHeader> getSentHeaders() {
      return sent;
    }

    public void queueForReceipt(FileTransferHeader header) {
      toread.add(header);
    }

    public TransferredFile getNativeFile(SegmentedFilename segName)
        throws IOException {
      return new MockTransferredFile();
    }

    public boolean shouldAttemptResume(TransferredFile file) {
      return false;
    }

    public Transferrer createTransferrer(TransferredFile file, long startedAt,
        long toDownload) {
      return new MockTransferrer(toDownload);
    }

    public Checksummer getChecksummer(TransferredFile file, long len) {
      return new MockChecksummer(len);
    }

    public void sendHeader(FileTransferHeader outHeader) throws IOException {
      sent.add(outHeader);
    }

    public FileTransferHeader readHeader() throws IOException {
      try {
        return toread.take();
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }
  }

}