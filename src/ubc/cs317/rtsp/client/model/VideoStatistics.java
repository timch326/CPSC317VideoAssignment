package ubc.cs317.rtsp.client.model;

import java.util.Date;


public class VideoStatistics {
	
	private long startTime;
	private double timeElapsed = 0;		// seconds
	private long frameCount = 0;
	private long outOfOrderCount = 0; 
	private long lostFrameCount = 0;
	private int lastSequenceNo = 0;
	
	public VideoStatistics() {
	}
	
	public void startLogging() {
		this.startTime = new Date().getTime();
	}
	
	public void pauseLogging() {
		long currentTime = new Date().getTime();
		timeElapsed += (currentTime - startTime) / 1000.0;
	}
	
	public void logFrame(Frame frame) {
		frameCount++;
		
		if (frame.getSequenceNumber() < lastSequenceNo)
			outOfOrderCount++;

		lastSequenceNo = frame.getSequenceNumber(); 
	}
	
	public void logLostFrame() {
		lostFrameCount++;
	}
	
	public void printStatistics() {
		double framesPerSec = frameCount / timeElapsed;
		double outOfOrderPerSec = outOfOrderCount / timeElapsed; 
		double lostFramesPerSec = lostFrameCount / timeElapsed; 
		System.out.println("The frame count per a second is " + framesPerSec);
		System.out.println("The out of order count per a second is " + outOfOrderPerSec);
		System.out.println("The lost packets per a second is " + lostFramesPerSec);
	}
	
}
