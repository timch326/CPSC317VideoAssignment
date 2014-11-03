package ubc.cs317.rtsp.client.model;



public class VideoStatistics {
	
	private static String PAUSED = "paused";
	private static String RUNNING = "running";
	
	private String state = PAUSED;
	private long startTime;
	private long cumulatedElapsedTime = 0;
	private long frameCount = 0;
	private long outOfOrderCount = 0; 
	private short lastSequenceNo = 0;
	
	private double averageDelay = 0;
	private double jitterVariance = 0;
	
	private long lastFrameReceivedTime;
	
	public VideoStatistics() {
	}
	
	public void startLogging() {
		if (state.equals(PAUSED)) {
			startTime = System.currentTimeMillis();
			lastFrameReceivedTime = startTime;
			state = RUNNING;
		}
	}
	
	public void pauseLogging() {
		if (state.equals(RUNNING)) {
			cumulatedElapsedTime += System.currentTimeMillis() - startTime;
			state = PAUSED;
		}
	}
	
	public long getTimeElapsed() {
		if (state.equals(PAUSED)) {
			return cumulatedElapsedTime;
		} else {
			return (System.currentTimeMillis() - startTime) + cumulatedElapsedTime;
		}
	}
	
	public void logFrame(Frame frame) {
		long timeReceivedFrame = System.currentTimeMillis();
		long timeSinceLastFrame = timeReceivedFrame - lastFrameReceivedTime;
		
		System.out.println("Time Between Last Frame: " + timeSinceLastFrame);
		
		if (frameCount > 0) {
			averageDelay = (averageDelay * (frameCount - 1) + timeSinceLastFrame) / frameCount;
			jitterVariance = (jitterVariance * (frameCount - 1) + Math.pow((averageDelay - timeSinceLastFrame), 2)) / frameCount;
		}
		frameCount++;
		if (frame.getSequenceNumber() < lastSequenceNo)
			outOfOrderCount++;
		
		lastFrameReceivedTime = timeReceivedFrame;
		lastSequenceNo = frame.getSequenceNumber(); 
	}
	
	public void printStatistics() {
		double timeElapsedSec = getTimeElapsed() / 1000.0;
		
		double framesPerSec = frameCount / timeElapsedSec;
		double outOfOrderPerSec = outOfOrderCount / timeElapsedSec; 
		double lostFramesPerSec = (lastSequenceNo - frameCount + 1) / timeElapsedSec; 
		
		System.out.println("The frame count per second is " + framesPerSec);
		System.out.println("The out of order count per second is " + outOfOrderPerSec);
		System.out.println("The lost packets per second is " + lostFramesPerSec);
		System.out.println("The time to play the video is " + timeElapsedSec);
		System.out.println("The number of frames received to play the video is " + frameCount);
		System.out.println("The average time between frames is " + averageDelay);
		System.out.println("The average amount of jitter is " + Math.pow(jitterVariance, 0.5));
	}

	public double getExpectedDelayPerPacket() {
		return averageDelay;
	}

	public double getJitter() {
		return Math.pow(jitterVariance, 0.5);
	}
	
	public short getLastSequenceNumber() {
		return lastSequenceNo;
	}
}
