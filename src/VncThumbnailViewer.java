//
//  Copyright (C) 2007 David Czechowski.  All Rights Reserved.
//  Copyright (C) 2001-2004 HorizonLive.com, Inc.  All Rights Reserved.
//  Copyright (C) 2002 Constantin Kaplinsky.  All Rights Reserved.
//  Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
//
//  This is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 2 of the License, or
//  (at your option) any later version.
//
//  This software is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this software; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
//  USA.
//

//
// VncThumbnailViewer.java - a unique VNC viewer.  This class creates an empty frame
// into which multiple vncviewers can be added.
//

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
// import java.util.Calendar;

/*** REST Server code *******
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
***  End REST Server code ***/

import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import java.net.URL;

public class VncThumbnailViewer extends Frame
		implements WindowListener, ComponentListener, ContainerListener, MouseListener, ActionListener {
	static Socket client;
	final static int MAX_WINDOWS = 8;
	public final static int STATE_EMPTY = 0;
	public final static int STATE_INVAL = 1;
	public final static int STATE_VALID = 2;

	static vncRec[] vncWindows = new vncRec[MAX_WINDOWS];

	public static void main(String argv[]) throws IOException {
		final int DELAY_SEC = 5;
		String urlParameters = "";

		/***
		 * REST Server code ******* final int LISTPORT = 9889; Executor executor
		 * = Executors.newFixedThreadPool(3); ServerSocket ss = new
		 * ServerSocket(LISTPORT); while (true) { executor.execute( new
		 * nasHttpdConnection (ss.accept(), t)); } End REST Server code
		 ***/

		if (argv.length > 0) {
			urlParameters = "{\"jenkInstance\":\"" + argv[0] + "\"}";
		}

		// Initialize the vncRec array (vncWindows) and valid = false
		for (int i = 0; i < MAX_WINDOWS; i++) {
			vncRec vRec = new vncRec();
			vncWindows[i] = vRec;
		}

		// Start the main vncviewer window.
		VncThumbnailViewer t = new VncThumbnailViewer();

		// Keep looping, looking for new VNC clients we can add
		while (true) {
			try {
				/***
				 * REST Server code ******* HttpPost request = new HttpPost abc
				 * ("http://pisa-auto.spglab.juniper.net:7321/listJobs");
				 * conn.setRequestProperty("Accept", "application/json"); End
				 * REST Server code
				 ***/

				// Clear valid flag for all current windows
				invalidateWindowsArray();

				URL url = new URL("http://pisa-auto.spglab.juniper.net:9321/listJobs");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");

				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				conn.setRequestProperty("charset", "utf-8");
				conn.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
				conn.setUseCaches(false);

				DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
				wr.writeBytes(urlParameters);
				wr.flush();
				wr.close();

				if (conn.getResponseCode() != 200) {
					throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
				} else {

					BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
					JsonParser parser = Json.createParser(br);
					Event event = null;

					String newip = null, newjenk, newsuite = null;

					while (parser.hasNext()) {
						event = parser.next();
						String marker = event.name();
						if (marker == "KEY_NAME" || marker == "VALUE_STRING") {
							String value = parser.getString();

							if (marker == "KEY_NAME" && value.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+")) {
								newip = value;
							} else if (value.matches(".*suite.*")) {
								parser.next();
								newsuite = parser.getString();
							} else if (value.matches(".*jenkins.*")) {
								parser.next();
								newjenk = parser.getString();

								// Check to see if we already have a record of
								// this window.
								// Mark it if we do
								if (!currentVnc(newip)) {
									vncRec vRec = new vncRec();
									if (vRec.set(newip, newjenk, newsuite) == false) {
										System.out.println("Error trying to add vncWindow object");
									} else {
										System.out.println("========= ADD " + vRec.getsuite());
										VncViewer v = viewersList.launchViewer(vRec.getip(), 5900, "dana123", "", "",
												vRec.getsuite());
										t.addViewer(v);
										vRec.set(v);
										addVnc(vRec);
									}
								}
							}
						}
					}
					conn.disconnect();
				}
			}

			catch (MalformedURLException e) {
				e.printStackTrace();
			}

			catch (IOException e) {
				e.printStackTrace();
			}

			// Print list of active vnc windows; delete stale ones
			System.out.println("\n--------------------------------");
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			System.out.println(dateFormat.format(date));
			for (int q = 0; q < MAX_WINDOWS; q++) {
				if (vncWindows[q].isValid()) {
					// System.out.println ("[" + q + "] : " +
					// vncWindows[q].getsuite() + " : " +
					// vncWindows[q].getip());
				} else {
					if (!vncWindows[q].isEmpty()) {
						System.out.println("========= REMOVE " + q + " : " + vncWindows[q].getsuite());
						t.removeViewer(vncWindows[q].getViewerRef());
						vncWindows[q].remove();
					}
				}
			}

			// wait for a few seconds
			try {
				Thread.sleep(DELAY_SEC * 1000);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	final static float VERSION = 1.50f;

	static VncViewersList viewersList;
	AddHostDialog hostDialog;
	MenuItem newhostMenuItem, loadhostsMenuItem, savehostsMenuItem, exitMenuItem;
	Frame soloViewer;
	int widthPerThumbnail, heightPerThumbnail;
	int thumbnailRowCount;

	VncThumbnailViewer() {
		viewersList = new VncViewersList(this);
		thumbnailRowCount = 0;
		widthPerThumbnail = 0;
		heightPerThumbnail = 0;

		setTitle("PS Automation Monitor");
		addWindowListener(this);
		addComponentListener(this);
		addMouseListener(this);

		GridLayout grid = new GridLayout();
		setLayout(grid);
		setSize(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getSize());
		setMenuBar(new MenuBar());
		getMenuBar().add(createFileMenu());
		setVisible(true);

		soloViewer = new Frame();
		soloViewer.setSize(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getSize());
		soloViewer.addWindowListener(this);
		soloViewer.addComponentListener(this);
		soloViewer.validate();
	}

	public void launchViewer(String host, int port, String password, String user, String tsname) {
		launchViewer(host, port, password, user, "", tsname);
	}

	public void launchViewer(String host, int port, String password, String user, String userdomain, String tsname) {
		VncViewer v = viewersList.launchViewer(host, port, password, user, userdomain, tsname);
	}

	public void clearWindowsArray() {
		// Initialize the vncRec array (vncWindows) and valid = false
		for (int i = 0; i < MAX_WINDOWS; i++) {
			vncRec vRec = new vncRec();
			vncWindows[i] = vRec;
		}
	}

	public static void invalidateWindowsArray() {
		// Initialize the vncRec array (vncWindows) and valid = false
		for (int i = 0; i < MAX_WINDOWS; i++) {
			if (vncWindows[i].isValid())
				vncWindows[i].invalidate();
		}
	}

	public static boolean currentVnc(String ip) {
		boolean ret = false;
		for (int i = 0; i < MAX_WINDOWS; i++) {
			String tmpip = vncWindows[i].getip();
			if (ip.matches(tmpip)) {
				vncWindows[i].validate();
				ret = true;
				break;
			}
		}
		return ret;
	}

	public static void listVnc() {
		for (int i = 0; i < MAX_WINDOWS; i++)
			if (!vncWindows[i].isEmpty())
				System.out.println(i + ": " + vncWindows[i].getip() + " : " + vncWindows[i].getvalid());
	}

	public static boolean addVnc(vncRec vr) {
		boolean ret = false;
		for (int i = 0; i < MAX_WINDOWS; i++) {
			if (vncWindows[i].isEmpty()) {
				vncWindows[i] = vr;
				ret = true;
				break;
			}
		}
		return ret;
	}

	void addViewer(VncViewer v) {
		int r = (int) Math.sqrt(viewersList.size() - 1) + 1;// int r =
															// (int)Math.sqrt(this.getComponentCount()
															// - 1) + 1;
		if (r != thumbnailRowCount) {
			thumbnailRowCount = r;
			((GridLayout) this.getLayout()).setRows(thumbnailRowCount);
			// ((GridLayout)this.getLayout()).setColumns(thumbnailRowCount);
			resizeThumbnails();
		}
		add(v);
		validate();
	}

	void removeViewer(VncViewer v) {
		viewersList.remove(v);
		remove(v);
		validate();

		int r = (int) Math.sqrt(viewersList.size() - 1) + 1;// int r =
															// (int)Math.sqrt(this.getComponentCount()
															// - 1) + 1;
		if (r != thumbnailRowCount) {
			thumbnailRowCount = r;
			((GridLayout) this.getLayout()).setRows(thumbnailRowCount);
			// ((GridLayout)this.getLayout()).setColumns(thumbnailRowCount);
			resizeThumbnails();
		}
	}

	void soloHost(VncViewer v) {
		if (v.vc == null)
			return;

		if (soloViewer.getComponentCount() > 0)
			soloHostClose();

		soloViewer.setVisible(true);
		soloViewer.setTitle(v.host);
		this.remove(v);
		soloViewer.add(v);
		v.vc.removeMouseListener(this);
		this.validate();
		soloViewer.validate();

		if (!v.rfb.closed()) {
			v.vc.enableInput(true);
		}
		updateCanvasScaling(v, getWidthNoInsets(soloViewer), getHeightNoInsets(soloViewer));
	}

	void soloHostClose() {
		VncViewer v = (VncViewer) soloViewer.getComponent(0);
		v.enableInput(false);
		updateCanvasScaling(v, widthPerThumbnail, heightPerThumbnail);
		soloViewer.removeAll();
		addViewer(v);
		v.vc.addMouseListener(this);
		soloViewer.setVisible(false);
	}

	private void updateCanvasScaling(VncViewer v, int maxWidth, int maxHeight) {
		maxHeight -= v.buttonPanel.getHeight();
		int fbWidth = v.vc.rfb.framebufferWidth;
		int fbHeight = v.vc.rfb.framebufferHeight;
		int f1 = maxWidth * 100 / fbWidth;
		int f2 = maxHeight * 100 / fbHeight;
		int sf = Math.min(f1, f2);
		if (sf > 100) {
			sf = 100;
		}

		v.vc.maxWidth = maxWidth;
		v.vc.maxHeight = maxHeight;
		v.vc.scalingFactor = sf;
		v.vc.scaledWidth = (fbWidth * sf + 50) / 100;
		v.vc.scaledHeight = (fbHeight * sf + 50) / 100;

		// Fix: invoke a re-paint of canvas?
		// Fix: invoke a re-size of canvas?
		// Fix: invoke a validate of viewer's gridbag?
	}

	void resizeThumbnails() {
		int newWidth = getWidthNoInsets(this) / thumbnailRowCount;
		int newHeight = getHeightNoInsets(this) / thumbnailRowCount;

		if (newWidth != widthPerThumbnail || newHeight != heightPerThumbnail) {
			widthPerThumbnail = newWidth;
			heightPerThumbnail = newHeight;

			ListIterator l = viewersList.listIterator();
			while (l.hasNext()) {
				VncViewer v = (VncViewer) l.next();
				// v.
				if (!soloViewer.isAncestorOf(v)) {
					if (v.vc != null) { // if the connection has been
										// established
						updateCanvasScaling(v, widthPerThumbnail, heightPerThumbnail);
					}
				}
			}

		}

	}

	private void loadsaveHosts(int mode) {
		FileDialog fd = new FileDialog(this, "Load hosts file...", mode);
		if (mode == FileDialog.SAVE) {
			fd.setTitle("Save hosts file...");
		}
		fd.show();

		String file = fd.getFile();
		if (file != null) {
			String dir = fd.getDirectory();

			if (mode == FileDialog.SAVE) {
				// ask about encrypting
				HostsFilePasswordDialog pd = new HostsFilePasswordDialog(this, true);
				if (pd.getResult()) {
					viewersList.saveToEncryptedFile(dir + file, pd.getPassword());
				} else {
					viewersList.saveToFile(dir + file);
				}
			} else {
				if (VncViewersList.isHostsFileEncrypted(dir + file)) {
					HostsFilePasswordDialog pd = new HostsFilePasswordDialog(this, false);
					viewersList.loadHosts(dir + file, pd.getPassword());
				} else {
					viewersList.loadHosts(dir + file, "");
				}
			}
		}
	}

	private void quit() {
		// Called by either File->Exit or Closing of the main window
		System.out.println("Closing window");
		ListIterator l = viewersList.listIterator();
		while (l.hasNext()) {
			((VncViewer) l.next()).disconnect();
		}
		this.dispose();

		System.exit(0);
	}

	static private int getWidthNoInsets(Frame frame) {
		Insets insets = frame.getInsets();
		int width = frame.getWidth() - (insets.left + insets.right);
		return width;
	}

	static private int getHeightNoInsets(Frame frame) {
		Insets insets = frame.getInsets();
		int height = frame.getHeight() - (insets.top + insets.bottom);
		return height;
	}

	private Menu createFileMenu() {
		Menu fileMenu = new Menu("File");
		newhostMenuItem = new MenuItem("Add New Host");
		loadhostsMenuItem = new MenuItem("Load List of Hosts");
		savehostsMenuItem = new MenuItem("Save List of Hosts");
		exitMenuItem = new MenuItem("Exit");

		newhostMenuItem.addActionListener(this);
		loadhostsMenuItem.addActionListener(this);
		savehostsMenuItem.addActionListener(this);
		exitMenuItem.addActionListener(this);

		fileMenu.add(newhostMenuItem);
		fileMenu.addSeparator();
		fileMenu.add(loadhostsMenuItem);
		fileMenu.add(savehostsMenuItem);
		fileMenu.addSeparator();
		fileMenu.add(exitMenuItem);

		loadhostsMenuItem.enable(true);
		savehostsMenuItem.enable(true);

		return fileMenu;
	}

	// Window Listener Events:
	public void windowClosing(WindowEvent evt) {
		if (soloViewer.isShowing()) {
			soloHostClose();
		}

		if (evt.getComponent() == this) {
			quit();
		}
	}

	public void windowActivated(WindowEvent evt) {
	}

	public void windowDeactivated(WindowEvent evt) {
	}

	public void windowOpened(WindowEvent evt) {
	}

	public void windowClosed(WindowEvent evt) {
	}

	public void windowIconified(WindowEvent evt) {
	}

	public void windowDeiconified(WindowEvent evt) {
	}

	// Component Listener Events:
	public void componentResized(ComponentEvent evt) {
		if (evt.getComponent() == this) {
			if (thumbnailRowCount > 0) {
				resizeThumbnails();
			}
		} else { // resize soloViewer
			VncViewer v = (VncViewer) soloViewer.getComponent(0);
			updateCanvasScaling(v, getWidthNoInsets(soloViewer), getHeightNoInsets(soloViewer));
		}

	}

	public void componentHidden(ComponentEvent evt) {
	}

	public void componentMoved(ComponentEvent evt) {
	}

	public void componentShown(ComponentEvent evt) {
	}

	// Mouse Listener Events:
	public void mouseClicked(MouseEvent evt) {
		if (evt.getClickCount() == 2) {
			Component c = evt.getComponent();
			if (c instanceof VncCanvas) {
				soloHost(((VncCanvas) c).viewer);
			}
		}

	}

	public void mouseEntered(MouseEvent evt) {
	}

	public void mouseExited(MouseEvent evt) {
	}

	public void mousePressed(MouseEvent evt) {
	}

	public void mouseReleased(MouseEvent evt) {
	}

	// Container Listener Events:
	public void componentAdded(ContainerEvent evt) {
		// This detects when a vncviewer adds a vnccanvas to it's container
		if (evt.getChild() instanceof VncCanvas) {
			VncViewer v = (VncViewer) evt.getContainer();
			v.vc.addMouseListener(this);
			v.buttonPanel.addContainerListener(this);
			// v.buttonPanel.disconnectButton.addActionListener(this);
			updateCanvasScaling(v, widthPerThumbnail, heightPerThumbnail);
		}

		// This detects when a vncviewer's Disconnect button had been pushed
		else if (evt.getChild() instanceof Button) {
			Button b = (Button) evt.getChild();
			if (b.getLabel() == "Hide desktop") {
				b.addActionListener(this);
			}
		}

	}

	public void componentRemoved(ContainerEvent evt) {
	}

	// Action Listener Event:
	public void actionPerformed(ActionEvent evt) {
		if (evt.getSource() instanceof Button && ((Button) evt.getSource()).getLabel() == "Hide desktop") {
			VncViewer v = (VncViewer) ((Component) ((Component) evt.getSource()).getParent()).getParent();
			this.remove(v);
			viewersList.remove(v);
		}
		if (evt.getSource() == newhostMenuItem) {
			hostDialog = new AddHostDialog(this);
		}
		if (evt.getSource() == savehostsMenuItem) {
			loadsaveHosts(FileDialog.SAVE);
		}
		if (evt.getSource() == loadhostsMenuItem) {
			loadsaveHosts(FileDialog.LOAD);
		}
		if (evt.getSource() == exitMenuItem) {
			quit();
		}
	}

	/***
	 * REST Server code *******
	 * 
	 * public static String getRestReq () { try { BufferedReader in = new
	 * BufferedReader ( new InputStreamReader(client.getInputStream(), "8859_1")
	 * ); OutputStream out = client.getOutputStream(); PrintWriter pout = new
	 * PrintWriter (new OutputStreamWriter(out, "8859_1")); String request =
	 * in.readLine(); System.out.println ("Request: " + request);
	 * 
	 * String[] elements; elements = request.split("/"); String newip =
	 * elements[1]; String newts = elements[2]; String newus = elements[3];
	 * String newpa = elements[4]; String retStr = newts + ":" + newip + ":" +
	 * newus + ":" + newpa; System.out.println ("New VNC: " + retStr); return
	 * retStr; }
	 * 
	 * catch (IOException e) { System.out.println("IO Error" + e); } return
	 * "foobar"; }
	 * 
	 * public void venusServer(String host, int port, String password, String
	 * tsname) { VncViewer v = viewersList.launchViewer(host, port, password,
	 * "", "", tsname); addViewer(v); } End REST Server code
	 ***/

}

// CLASS: vncRec
class vncRec {
	String ip = null;
	String jenk = null;
	String suitename = null;
	VncViewer vRef = null;
	int valid;

	vncRec() {
		ip = "blah";
		jenk = "blah";
		suitename = "blah";
		valid = 0;
	}

	boolean set(String i, String j, String s) {
		ip = i;
		jenk = j;
		suitename = s;
		return true;
	}

	void remove() {
		ip = "blah";
		jenk = "blah";
		suitename = "blah";
		valid = 0;
	}

	void set(VncViewer v) {
		vRef = v;
		valid = 2;
	}

	VncViewer getViewerRef() {
		return vRef;
	}

	String getip() {
		return ip;
	}

	String getsuite() {
		return suitename;
	}

	int getvalid() {
		return valid;
	}

	void validate() {
		valid = 2;
	}

	void invalidate() {
		valid = 1;
	}

	boolean isValid() {
		if (valid == 2)
			return true;
		else
			return false;
	}

	boolean isEmpty() {
		if (valid == 0)
			return true;
		else
			return false;
	}
}

/***
 * REST Server code *******
 * 
 * //CLASS: nasHttpdConnection class nasHttpdConnection implements Runnable {
 * Socket client; private VncThumbnailViewer t; nasHttpdConnection (Socket
 * client, VncThumbnailViewer t) throws SocketException { this.client = client;
 * this.t = t; }
 * 
 * public void run() { try { BufferedReader in = new BufferedReader ( new
 * InputStreamReader(client.getInputStream(), "8859_1") ); OutputStream out =
 * client.getOutputStream(); PrintWriter pout = new PrintWriter (new
 * OutputStreamWriter(out, "8859_1")); String request = in.readLine();
 * System.out.println ("Request: "+request);
 * 
 * String[] elements; elements = request.split("/"); String newip = elements[1];
 * String newts = elements[2]; String newus = elements[3]; String newpa =
 * elements[4]; System.out.println ("New VC: " + newts + " (" + newip + ") [" +
 * newus + " / " + newpa + "]");
 * 
 * t.venusServer(newip, 5900, "dana123", newts); byte[] done = new byte[4];
 * done[0] = 1; done[1] = 2; done[2] = 3; done[3] = '\0'; out.write (done, 0,
 * 3); out.flush(); }
 * 
 * catch (IOException e) { System.out.println("IO Error" + e); } } } End REST
 * Server code
 ***/
