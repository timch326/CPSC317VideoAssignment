package ubc.cs317.rtsp.client.net;

import java.io.BufferedWriter;
import java.io.IOException;

import ubc.cs317.rtsp.client.exception.RTSPException;

public class RTSPRequest {

	String requestString = "";

	public RTSPRequest(String rtspType, String videoName) {
		addFirstLine(rtspType, videoName);
	}

	public RTSPRequest setCSeq(int cSeq) {
		requestString += "CSeq: " + cSeq + "\r\n";
		return this;
	}

	public RTSPRequest setRtpPort(int rtpPort) {
		requestString += "Transport: RTP/UDP; client_port=" + rtpPort + "\r\n";
		return this;

	}
	
	public RTSPRequest setSession(String sessionNumber) {
		requestString += "Session: " + sessionNumber + "\r\n";
		return this;
	}

	public RTSPResponse sendRequest(BufferedWriter rtspWriter) throws RTSPException {
		try {
			requestString += "\r\n";
			rtspWriter.write(requestString);
			rtspWriter.flush();
		} catch (IOException e) {
			throw new RTSPException("Could not get input/output stream", e);
		}
		return null;
	}
	
	private void addFirstLine(String rtspType, String videoName) {
		requestString += rtspType + " " + videoName + " RTSP/1.0\r\n";
	}


}
