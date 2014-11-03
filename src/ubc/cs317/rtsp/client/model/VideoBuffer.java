package ubc.cs317.rtsp.client.model;

import java.util.TreeSet;

public class VideoBuffer {

	TreeSet<Frame> frames;

	public VideoBuffer() {
		frames = new TreeSet<Frame>();
	}

	public void add(Frame frame) {
		frames.add(frame);
	}

	public Frame pop() {
		Frame firstFrame = frames.first();
		frames.remove(firstFrame);
		return firstFrame;
	}

	public short peekSequenceNo() {
		return !frames.isEmpty() ? frames.first().getSequenceNumber()
				: 0;
	}

	public long peekTimeStamp() {
		return !frames.isEmpty() ? frames.first().getTimestamp() : 0;
	}
	
	public boolean isEmpty() {
		return frames.isEmpty();
	}

}
