/*
 * University of British Columbia
 * Department of Computer Science
 * CPSC317 - Internet Programming
 * Assignment 2
 * 
 * Author: Jonatan Schroeder
 * January 2013
 * 
 * This code may not be used without written consent of the authors, except for 
 * current and future projects and assignments of the CPSC317 course at UBC.
 */

package ubc.cs317.rtsp.client.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import ubc.cs317.rtsp.client.exception.RTSPException;
import ubc.cs317.rtsp.client.model.Frame;
import ubc.cs317.rtsp.client.model.RTPHeader;
import ubc.cs317.rtsp.client.model.Session;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

	private static final int BUFFER_LENGTH = 15000;
	private static final long MINIMUM_DELAY_READ_PACKETS_MS = 20;

	private Session session;
	private Timer rtpTimer;


	// TODO Add additional fields, if necessary
	private Socket tcpSocket;
	private DatagramSocket rtpSocket;
	private int cSeq = 1;
	
	private BufferedWriter rtspWriter;
	private BufferedReader rtspReader;
	private String sessionNumber;
	
	/**
	 * Establishes a new connection with an RTSP server. No message is sent at
	 * this point, and no stream is set up.
	 * 
	 * @param session
	 *            The Session object to be used for connectivity with the UI.
	 * @param server
	 *            The hostname or IP address of the server.
	 * @param port
	 *            The TCP port number where the server is listening to.
	 * @throws RTSPException
	 *             If the connection couldn't be accepted, such as if the host
	 *             name or port number are invalid or there is no connectivity.
	 */
	public RTSPConnection(Session session, String server, int port)
			throws RTSPException {
		this.session = session;
		try {
			tcpSocket = new Socket(server, port);
			rtspWriter = new BufferedWriter(new PrintWriter(tcpSocket.getOutputStream()));
			rtspReader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
		} catch (UnknownHostException e) {
			throw new RTSPException("Could not connect to host", e);
		} catch (IOException e) {
			throw new RTSPException("Malformed input to client", e );
		}
		// TODO
	}

	/**
	 * Sends a SETUP request to the server. This method is responsible for
	 * sending the SETUP request, receiving the response and retrieving the
	 * session identification to be used in future messages. It is also
	 * responsible for establishing an RTP datagram socket to be used for data
	 * transmission by the server. The datagram socket should be created with a
	 * random UDP port number, and the port number used in that connection has
	 * to be sent to the RTSP server for setup. This datagram socket should also
	 * be defined to timeout after 1 second if no packet is received.
	 * 
	 * @param videoName
	 *            The name of the video to be setup.
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the RTP socket could not be created, or if the server did
	 *             not return a successful response.
	 */
	public synchronized void setup(String videoName) throws RTSPException {
		try {
			rtpSocket = new DatagramSocket();
			int rtpPort = rtpSocket.getLocalPort();

			cSeq = 1;

			String req = "";
			req = req.concat("SETUP " + videoName + " RTSP/1.0\r\n");
			req = req.concat("CSeq: " + cSeq + "\r\n");
			req = req.concat("Transport: RTP/UDP; client_port=" + rtpPort + "\r\n");
			req = req.concat("\r\n");
			System.out.println(req);
			rtspWriter.write(req);
			rtspWriter.flush();
			
			RTSPResponse setupResponse = RTSPResponse.readRTSPResponse(rtspReader);
			sessionNumber = setupResponse.getHeaderValue("Session");
			System.out.println(setupResponse.getResponseCode());
			System.out.println(sessionNumber);
			cSeq++;

		} catch (IOException e) {
			throw new RTSPException("Could not get input/output stream", e);
		}
	}

	/**
	 * Sends a PLAY request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, starting the RTP timer responsible for receiving RTP packets
	 * with frames.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void play() throws RTSPException {
		try {
			
			String req = "";
			req = req.concat("PLAY " + session.getVideoName() + " RTSP/1.0\r\n");
			req = req.concat("CSeq: " + cSeq + "\r\n");
			req = req.concat("Session: " + sessionNumber + "\r\n");
			req = req.concat("\r\n");
			System.out.println(req);
			rtspWriter.write(req);
			rtspWriter.flush();
			
			RTSPResponse playResponse = RTSPResponse.readRTSPResponse(rtspReader);
			System.out.println(playResponse.getResponseCode());
			cSeq++;
			
			startRTPTimer();

		} catch (IOException e) {
			throw new RTSPException("Could not get input/output stream", e);
		}
	}

	/**
	 * Starts a timer that reads RTP packets repeatedly. The timer will wait at
	 * least MINIMUM_DELAY_READ_PACKETS_MS after receiving a packet to read the
	 * next one.
	 */
	private void startRTPTimer() {

		rtpTimer = new Timer();
		rtpTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				receiveRTPPacket();
			}
		}, 0, MINIMUM_DELAY_READ_PACKETS_MS);
	}

	/**
	 * Receives a single RTP packet and processes the corresponding frame. The
	 * data received from the datagram socket is assumed to be no larger than
	 * BUFFER_LENGTH bytes. This data is then parsed into a Frame object (using
	 * the parseRTPPacket method) and the method session.processReceivedFrame is
	 * called with the resulting packet. In case of timeout no exception should
	 * be thrown and no frame should be processed.
	 */
	private void receiveRTPPacket() {

		byte[] buffer = new byte[BUFFER_LENGTH];
		DatagramPacket rtpPacket = new DatagramPacket(buffer, BUFFER_LENGTH);
		try {
			rtpSocket.receive(rtpPacket);
			byte[] rtpHeaderData = rtpPacket.getData();
			Frame frame = parseRTPPacket(rtpHeaderData, BUFFER_LENGTH);
			session.processReceivedFrame(frame);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// TODO
	}

	/**
	 * Sends a PAUSE request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, cancelling the RTP timer responsible for receiving RTP packets
	 * with frames.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void pause() throws RTSPException {

		// TODO
	}

	/**
	 * Sends a TEARDOWN request to the server. This method is responsible for
	 * sending the request, receiving the response and, in case of a successful
	 * response, closing the RTP socket. This method does not close the RTSP
	 * connection, and a further SETUP in the same connection should be
	 * accepted. Also this method can be called both for a paused and for a
	 * playing stream, so the timer responsible for receiving RTP packets will
	 * also be cancelled.
	 * 
	 * @throws RTSPException
	 *             If there was an error sending or receiving the RTSP data, or
	 *             if the server did not return a successful response.
	 */
	public synchronized void teardown() throws RTSPException {

		// TODO
	}

	/**
	 * Closes the connection with the RTSP server. This method should also close
	 * any open resource associated to this connection, such as the RTP
	 * connection, if it is still open.
	 */
	public synchronized void closeConnection() {
		// TODO
	}

	/**
	 * Parses an RTP packet into a Frame object.
	 * 
	 * @param packet
	 *            the byte representation of a frame, corresponding to the RTP
	 *            packet.
	 * @return A Frame object.
	 */
	private static Frame parseRTPPacket(byte[] packet, int length) {

		byte payloadType = (byte) (packet[1] & 0x7F);
		boolean marker;
		short sequenceNumber;
		int timestamp;
		byte[] payload;
		int offset;
		int payloadLength;

		System.out.println(payloadType);

		return null; // Replace with a proper Frame
	}
}
