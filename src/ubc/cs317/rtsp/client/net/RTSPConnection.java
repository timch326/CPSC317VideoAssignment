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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import ubc.cs317.rtsp.client.exception.RTSPException;
import ubc.cs317.rtsp.client.model.Frame;
import ubc.cs317.rtsp.client.model.Session;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

	private static final int RTP_TIMEOUT = 1000;
	private static final int RTP_HEADER_LENGTH = 12;
	private static final int BUFFER_LENGTH = 15000;
	private static final long MINIMUM_DELAY_READ_PACKETS_MS = 20;
	private static final long TARGET_FRAMERATE = 40;
	private static final long MAXIMUM_WAIT_FOR_LOST_FRAME = 80;
	
	private static final String STATE_INIT = "INIT";
	private static final String STATE_READY = "READY";
	private static final String STATE_PLAYING = "PLAYING";

	private Session session;
	private Timer rtpTimer;
	private Timer videoTimer;

	private Socket tcpSocket; 
	private DatagramSocket rtpSocket;
	private int cSeq = 1;
	
	private int BUFFER_SIZE; 
	private TreeSet<Frame> frameBuffer = new TreeSet<Frame>(); 
	
	//Statistical constants
	private Date timeStart; 
	private long frameCount = 0; 
	private long outOfOrderCount = 0; 
	private long lostFrameCount = 0;
	private int lastSequenceNo = 0;
	
	private short lastPlayedFrameSeqNum = -1; 
	private long lastPlayedFrameTimeStamp = -40; 
	private String state = STATE_INIT;

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
			rtspWriter = new BufferedWriter(new PrintWriter(
					tcpSocket.getOutputStream()));
			rtspReader = new BufferedReader(new InputStreamReader(
					tcpSocket.getInputStream()));
		} catch (UnknownHostException e) {
			throw new RTSPException("Could not connect to host", e);
		} catch (IOException e) {
			throw new RTSPException("Malformed input to client", e);
		}
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
		
		if (state != STATE_INIT) {
			throw new RTSPException("Close connection before opening a new video.");
		}
		
		try {
			rtpSocket = new DatagramSocket();
			rtpSocket.setSoTimeout(RTP_TIMEOUT);
			int rtpPort = rtpSocket.getLocalPort();
			cSeq = 1;

			new RTSPRequest("SETUP", videoName)
					.setCSeq(cSeq)
					.setRtpPort(rtpPort)
					.sendRequest(rtspWriter);
			RTSPResponse setupResponse = RTSPResponse
					.readRTSPResponse(rtspReader);
			
			System.out.println(sessionNumber);

			checkSuccessfulResponse(setupResponse);
			sessionNumber = setupResponse.getHeaderValue("Session");
			cSeq++;
			state = STATE_READY;

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
		
		if (state == STATE_INIT) {
			throw new RTSPException("Open a video first.");
		}
		
		if (state == STATE_PLAYING) {
			throw new RTSPException("A video is already playing.");
		}
		
		try {

			new RTSPRequest("PLAY", session.getVideoName())
				.setCSeq(cSeq)
				.setSession(sessionNumber)
				.sendRequest(rtspWriter);
			
			RTSPResponse playResponse = RTSPResponse
					.readRTSPResponse(rtspReader);

			checkSuccessfulResponse(playResponse);
			
			cSeq++;
			System.out.println("Payload Type,Marker,Sequence#,Timestamp\n");
			startRTPTimer();
			startVideoTimer();
			timeStart = new Date();
			state = STATE_PLAYING;

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
	
	private void startVideoTimer()
	{
		videoTimer = new Timer();
		videoTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if(!frameBuffer.isEmpty()) {

					short currentSeqNum = frameBuffer.first().getSequenceNumber();
					long currentTimeStamp = frameBuffer.first().getTimestamp();
					long timeElapsed = (System.currentTimeMillis() - timeStart.getTime());
					long timeWaitDelta = timeElapsed - MAXIMUM_WAIT_FOR_LOST_FRAME;
					
					//TODO Refactor dirty code
					//Never play any frame that is before the last played frame EVER
					if ( lastPlayedFrameSeqNum > currentSeqNum )
					{
						frameBuffer.remove(frameBuffer.first());
					}
					//Else if the frame is the next in the sequence, play it
					else if( lastPlayedFrameSeqNum + 1 == currentSeqNum )
					{
						System.out.printf("Time since starting %d\n", System.currentTimeMillis() - timeStart.getTime());
						System.out.printf("Timestamp %d\n", frameBuffer.first().getTimestamp());
						System.out.println("------------------------");

						session.processReceivedFrame(frameBuffer.first());

						lastPlayedFrameSeqNum = currentSeqNum;
						lastPlayedFrameTimeStamp = currentTimeStamp;

						frameBuffer.remove(frameBuffer.first()); 
					}
					//If it's not the next in sequence, but there is a frame between us, wait
					else if( lastPlayedFrameSeqNum + 1 != currentSeqNum && lastPlayedFrameTimeStamp > timeWaitDelta ) {
						return;
					}
					//We have waited too long and the next sequence is greater by one, just play the next frame
					else if ( lastPlayedFrameSeqNum + 1 != currentSeqNum && lastPlayedFrameTimeStamp <= timeWaitDelta ) {
						System.out.printf("Time since starting %d\n", System.currentTimeMillis() - timeStart.getTime());
						System.out.printf("Timestamp %d\n", frameBuffer.first().getTimestamp());
						System.out.println("------------------------");

						session.processReceivedFrame(frameBuffer.first());

						lastPlayedFrameSeqNum = currentSeqNum;
						lastPlayedFrameTimeStamp = currentTimeStamp;

						frameBuffer.remove(frameBuffer.first()); 
						lostFrameCount++;
					} 
				}
			}
		}, 0, TARGET_FRAMERATE);
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
			frameBuffer.add(frame);

			if( frame.getSequenceNumber() - lastSequenceNo < 0 )
				outOfOrderCount++;

			lastSequenceNo = frame.getSequenceNumber(); 
			frameCount++; 
//			System.out.println("this packets time: " + (System.currentTimeMillis() - timeStart.getTime()));

		} catch (SocketTimeoutException e) {
			rtpTimer.cancel();
			double secondsElapsed = (new Date().getTime() - timeStart.getTime()) / 1000.0;
			double framesPerSec = frameCount / secondsElapsed;
			double outOfOrderPerSec = outOfOrderCount / secondsElapsed; 
			double lostFramesPerSec = lostFrameCount / secondsElapsed; 
			System.out.println("The frame count per a second is " + framesPerSec);
			System.out.println("The out of order count per a second is " + outOfOrderPerSec);
			System.out.println("The lost packets per a second is " + lostFramesPerSec);
		} catch (IOException e) {
			e.printStackTrace();
		}

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
		
		if (state == STATE_INIT) {
			throw new RTSPException("There is no video to pause.");
		}
		
		if (state == STATE_READY) {
			throw new RTSPException("The video is not playing.");
		}

		try {

			new RTSPRequest("PAUSE", session.getVideoName())
				.setCSeq(cSeq)
				.setSession(sessionNumber)
				.sendRequest(rtspWriter);

			RTSPResponse pauseResponse = RTSPResponse
					.readRTSPResponse(rtspReader);
						
			checkSuccessfulResponse(pauseResponse);
			cSeq++;
			rtpTimer.cancel();
			state = STATE_READY;

		} catch (IOException e) {
			throw new RTSPException("Could not get input/output stream", e);
		}

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
		
		if (state == STATE_INIT) {
			throw new RTSPException("There is no video to teardown.");
		}

		try {
			new RTSPRequest("TEARDOWN", session.getVideoName())
				.setCSeq(cSeq)
				.setSession(sessionNumber)
				.sendRequest(rtspWriter);

			RTSPResponse teardownResponse = RTSPResponse
					.readRTSPResponse(rtspReader);
			
			checkSuccessfulResponse(teardownResponse);
			
			rtpTimer.cancel();
			videoTimer.cancel();
			rtpSocket.close();
			cSeq++;
			
			state = STATE_INIT;

		} catch (IOException e) {
			throw new RTSPException("Could not get input/output stream", e);
		}

	}

	/**
	 * Closes the connection with the RTSP server. This method should also close
	 * any open resource associated to this connection, such as the RTP
	 * connection, if it is still open.
	 */
	public synchronized void closeConnection() {
		try {
			rtpTimer.cancel();
			rtpSocket.close();
			tcpSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

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

		ByteBuffer wrap = ByteBuffer.wrap(packet);
		
		byte payloadType = (byte) (packet[1] & 0x7F);
		boolean marker = (packet[1] & 0x80) == 1 ? true : false;
		short sequenceNumber = wrap.getShort(2);
		int timestamp = wrap.getInt(4);
		byte[] payload = Arrays.copyOfRange(packet, RTP_HEADER_LENGTH,
				BUFFER_LENGTH);

//		System.out.println("Payload Type: " + payloadType);
//		System.out.println("Marker: " + marker);
//		System.out.println("Sequence No: " + sequenceNumber);
//		System.out.println("Timestamp: " + timestamp);
//		System.out.printf( "%d, %d, %d, %d \n", payloadType, (marker) ? 1 : 0 , sequenceNumber, timestamp);

		return new Frame(payloadType, marker, sequenceNumber, timestamp,
				payload);

	}
	
	private void checkSuccessfulResponse(RTSPResponse response)
			throws RTSPException {
		
		System.out.println(response.getResponseCode());

		if (response.getResponseCode() != 200) {
			throw new RTSPException(
					"Server did not return a successful response, got: "
							+ response.getResponseCode());
		}
	}
}
