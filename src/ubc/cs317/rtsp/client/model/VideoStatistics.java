package ubc.cs317.rtsp.client.model;

import java.util.Date;


public class VideoStatistics {
	
	private long startTime;
	private double timeElapsed = 0;		// seconds
	private long frameCount = 0;
	private long outOfOrderCount = 0; 
	private int lastSequenceNo = 0;
	
	private double averageDelay = 0;
	private double jitterVariance = 0;
	
	private long lastFrameReceivedTime;
	
	public VideoStatistics() {
	}
	
	public void startLogging() {
		this.startTime = new Date().getTime();
		lastFrameReceivedTime = startTime;
	}
	
	public void pauseLogging() {
		long currentTime = new Date().getTime();
		timeElapsed += (currentTime - startTime) / 1000.0;
	}
	
	public void logFrame(Frame frame) {
		long timeReceivedFrame = new Date().getTime(); 
		long timeSinceLastFrame = timeReceivedFrame - lastFrameReceivedTime;
		
		// System.out.println("Time Between Last Frame: " + timeSinceLastFrame);
		
		if (frameCount > 0) {
			averageDelay = (averageDelay * (frameCount - 1) + timeSinceLastFrame) / frameCount;
			jitterVariance = (jitterVariance * (frameCount - 1) + Math.pow((averageDelay - timeSinceLastFrame), 2)) / frameCount;
		}
			
		
		lastFrameReceivedTime = timeReceivedFrame;
		
		frameCount++;
		
		if (frame.getSequenceNumber() < lastSequenceNo)
			outOfOrderCount++;

		lastSequenceNo = frame.getSequenceNumber(); 
	}
	

	
	public void printStatistics() {
		double framesPerSec = frameCount / timeElapsed;
		double outOfOrderPerSec = outOfOrderCount / timeElapsed; 
		double lostFramesPerSec = (lastSequenceNo - frameCount + 1) / timeElapsed; 
		
		System.out.println("The frame count per second is " + framesPerSec);
		System.out.println("The out of order count per second is " + outOfOrderPerSec);
		System.out.println("The lost packets per second is " + lostFramesPerSec);
		System.out.println("The time to play the video is " + timeElapsed);
		System.out.println("The number of frames received to play the video is " + frameCount);
		System.out.println("The average time between frames is " + averageDelay);
		System.out.println("The average amount of jitter is " + Math.pow(jitterVariance, 0.5));

	}
	
}
